package io.javafmt.intellij;

import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drives the {@code javafmt} binary from <em>Reformat Code</em>.
 *
 * <p>An {@link AsyncDocumentFormattingService} rather than an {@code ExternalFormatProcessor}
 * because the format is a subprocess. The platform calls an {@code ExternalFormatProcessor}
 * synchronously from {@code CodeStyleManagerImpl.reformatText}, on the EDT, with the write lock
 * held: a binary on a cold network mount, or one caught by an antivirus scan, would freeze the
 * whole IDE for as long as it took, with no way to cancel. This base class instead runs the
 * {@link FormattingTask} on a pooled thread under a cancellable progress indicator and installs
 * the result in a write action of its own.
 *
 * <p>The empty {@link #getFeatures()} set is what routes everything javafmt cannot do back to
 * IntelliJ's own formatter. {@code FormattingServiceUtil.findService} picks the first service that
 * declares the needed feature <em>and</em> answers {@code canFormat}, so leaving out
 * {@code FORMAT_FRAGMENTS} sends a selection reformat on to {@code CoreFormattingService}, and
 * leaving out {@code AD_HOC_FORMATTING} does the same for the whitespace-only reformatting the
 * editor does while you type. javafmt lays out a whole compilation unit or nothing, and its lint
 * pass rewrites code — it inserts {@code final}, adds braces, drops unused imports — so neither
 * request is one it can honour.
 */
public final class JavafmtFormattingService extends AsyncDocumentFormattingService {

    private static final Logger LOG = Logger.getInstance(JavafmtFormattingService.class);

    /** Must match the {@code notificationGroup} id registered in {@code plugin.xml}. */
    private static final String NOTIFICATION_GROUP = "javafmt";

    /**
     * Version probes are cached per binary identity (path, size, and last-modified time), not just
     * per path. A stale cached {@code false} (the binary was old, then upgraded) only costs one
     * unnecessary warning on the next reformat — cheap to accept. A stale cached {@code true} is the
     * direction that actually matters: keyed on path alone, a binary replaced in place at the same
     * path (a downgrade, a symlink swap, a rebuild) would keep reading as supported for the rest of
     * the IDE session, letting a binary that does not understand {@code --diagnostics=json} be
     * driven as if it did. Including size and mtime in the key means a replaced binary gets a new
     * key and is re-probed instead of silently reusing the old verdict in either direction.
     */
    private static final Map<VersionCacheKey, Boolean> VERSION_SUPPORTED = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Set<Feature> getFeatures() {
        return Set.of();
    }

    @Override
    public boolean canFormat(@NotNull final PsiFile file) {
        return file instanceof PsiJavaFile && JavafmtSettings.getInstance(file.getProject()).isEnabled();
    }

    @Override
    @Nullable
    protected FormattingTask createFormattingTask(@NotNull final AsyncFormattingRequest request) {
        return new JavafmtFormattingTask(request);
    }

    @Override
    @NotNull
    protected String getNotificationGroupId() {
        return NOTIFICATION_GROUP;
    }

    @Override
    @NotNull
    protected String getName() {
        return "javafmt";
    }

    /**
     * Tells the user javafmt did nothing and why. An internal diagnostic is a javafmt bug and is
     * reported as an error; everything else is a problem with the source in front of the user,
     * which they can act on themselves.
     */
    private static void report(final Project project, final String fileName, final JavafmtBinary.Outcome.Formatted formatted) {
        final var message = fileName + " was left unformatted:\n" + describe(formatted);
        if (formatted.hasInternalError()) {
            LOG.warn("javafmt: " + message);
            notifyUser(project, NotificationType.ERROR, message);
        } else {
            LOG.info("javafmt: " + message);
            notifyUser(project, NotificationType.WARNING, message);
        }
    }

    private static String describe(final JavafmtBinary.Outcome.Formatted formatted) {
        return formatted.diagnostics()
                .stream()
                .map(ShimDiagnostic::message)
                .collect(Collectors.joining("\n"));
    }

    private static void notifyUser(final Project project, final NotificationType type, final String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("javafmt", message, type)
                .notify(project);
    }

    private static boolean probeSupportedVersion(final JavafmtBinary binary) {
        return binary.version().map(JavafmtBinary::isSupportedVersion).orElse(false);
    }

    /**
     * A cache key identifying the binary at {@code path} by content, not merely by location: size
     * and last-modified time change whenever the file at that path is replaced, so a downgrade, a
     * symlink swap, or a rebuild in place invalidates the cached verdict instead of reusing it.
     * Empty when the file cannot be stat'd (e.g. it vanished between resolving and probing it), in
     * which case the caller probes directly rather than caching under a key that cannot detect a
     * later replacement.
     */
    private static Optional<VersionCacheKey> versionCacheKey(final Path path) {
        try {
            return Optional.of(new VersionCacheKey(path, Files.getLastModifiedTime(path), Files.size(path)));
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    private record VersionCacheKey(Path path, FileTime lastModified, long size) {}

    /**
     * One reformat, run off the EDT by the platform.
     *
     * <p>Every path ends in exactly one call on the request — {@code onTextReady} — including the
     * paths where javafmt declines to format. A request that is never completed holds the platform
     * until its 30 s timeout expires, so "leave the file alone" is expressed as "install the text
     * the document already has" rather than by returning silently.
     */
    private static final class JavafmtFormattingTask implements FormattingTask {

        private final AsyncFormattingRequest request;

        /**
         * The thread the platform runs {@link #run} on, published so {@link #cancel} can interrupt
         * it. {@code JavafmtBinary.format} waits on the child with an interruptible
         * {@code Process.waitFor}, so an interrupt there kills the child and returns a refusal
         * rather than leaving the subprocess running.
         */
        private volatile Thread worker;

        private JavafmtFormattingTask(final AsyncFormattingRequest request) {
            this.request = request;
        }

        @Override
        public void run() {
            worker = Thread.currentThread();
            final var context = request.getContext();
            final var project = context.getProject();
            final var fileName = context.getContainingFile().getName();
            final var content = request.getDocumentText();
            final var resolved = JavafmtBinary.resolve(JavafmtSettings.getInstance(project).getBinaryPath());
            if (resolved.isEmpty()) {
                notifyUser(
                        project,
                        NotificationType.WARNING,
                        "No javafmt binary found. Set its path under Settings | Tools | javafmt, or put javafmt on your PATH.");
                request.onTextReady(content);
                return;
            }
            final var binary = new JavafmtBinary(resolved.get());
            final var supported = versionCacheKey(resolved.get())
                    .map(key -> VERSION_SUPPORTED.computeIfAbsent(key, ignored -> probeSupportedVersion(binary)))
                    .orElseGet(() -> probeSupportedVersion(binary));
            if (!supported) {
                notifyUser(
                        project,
                        NotificationType.WARNING,
                        "The javafmt binary at " + resolved.get() + " is too old: javafmt " + JavafmtBinary.minimumVersion()
                                + " or newer is required.");
                request.onTextReady(content);
                return;
            }
            final var outcome = binary.format(content, List.of());
            if (outcome instanceof JavafmtBinary.Outcome.Refused refused) {
                LOG.warn("javafmt: " + refused.reason());
                notifyUser(
                        project,
                        refused.internal() ? NotificationType.ERROR : NotificationType.WARNING,
                        fileName + " was left unformatted: " + refused.reason());
                request.onTextReady(content);
                return;
            }
            final var formatted = (JavafmtBinary.Outcome.Formatted) outcome;
            if (formatted.hasFailure()) {
                report(project, fileName, formatted);
                request.onTextReady(content);
                return;
            }
            if (!formatted.diagnostics().isEmpty()) {
                // Warning-only rules fire on ordinary code, so a balloon per reformat would be
                // noise; the log keeps them reachable while the reformat itself goes through.
                LOG.info("javafmt: " + fileName + ":\n" + describe(formatted));
            }
            request.onTextReady(formatted.output());
        }

        @Override
        public boolean cancel() {
            final var thread = worker;
            if (thread != null) {
                thread.interrupt();
            }
            return true;
        }
    }
}
