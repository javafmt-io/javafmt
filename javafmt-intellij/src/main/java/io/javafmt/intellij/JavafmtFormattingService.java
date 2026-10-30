package io.javafmt.intellij;

import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import io.javafmt.daemon.protocol.Diagnostic;
import java.util.Optional;
import java.util.Set;
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
                .map(Diagnostic::getMessage)
                .collect(Collectors.joining("\n"));
    }

    private static void notifyUser(final Project project, final NotificationType type, final String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("javafmt", message, type)
                .notify(project);
    }

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
         * it. {@code JavafmtDaemon.format} waits for the reply with an interruptible wait, so an
         * interrupt there returns at once, leaves the file alone without a notification, and the
         * daemon drops the late reply.
         */
        @Nullable private Thread worker;

        private JavafmtFormattingTask(final AsyncFormattingRequest request) {
            this.request = request;
        }

        @Override
        public void run() {
            synchronized (this) {
                worker = Thread.currentThread();
            }
            try {
                format();
            } finally {
                synchronized (this) {
                    worker = null;
                }
            }
        }

        private void format() {
            final var context = request.getContext();
            final var project = context.getProject();
            final var fileName = context.getContainingFile().getName();
            final var content = request.getDocumentText();
            final var provider = JavafmtBinaryProvider.getInstance();
            final var resolved = locate(project, provider);
            if (resolved.isEmpty()) {
                request.onTextReady(content);
                return;
            }
            final var binary = resolved.get();
            final var supported = provider.versionGate().isSupported(binary);
            if (supported.isEmpty()) {
                if (!Thread.currentThread().isInterrupted()) {
                    notifyUser(project, NotificationType.WARNING, "The javafmt binary at " + binary.path() + " did not report its version in time.");
                }
                request.onTextReady(content);
                return;
            }
            if (!supported.get()) {
                notifyUser(
                        project,
                        NotificationType.WARNING,
                        "The javafmt binary at " + binary.path() + " is too old: javafmt " + JavafmtBinary.minimumVersion()
                                + " or newer is required.");
                request.onTextReady(content);
                return;
            }
            switch (JavafmtDaemons.getInstance(project).format(binary, content)) {
                case JavafmtBinary.Outcome.Cancelled ignored -> request.onTextReady(content);
                case JavafmtBinary.Outcome.Refused refused -> {
                    LOG.warn("javafmt: " + refused.reason());
                    notifyUser(
                            project,
                            refused.internal() ? NotificationType.ERROR : NotificationType.WARNING,
                            fileName + " was left unformatted: " + refused.reason());
                    request.onTextReady(content);
                }
                case JavafmtBinary.Outcome.Formatted formatted -> install(project, fileName, content, formatted);
            }
        }

        private static Optional<BinaryIdentity> locate(final Project project, final JavafmtBinaryProvider provider) {
            final var configured = JavafmtSettings.getInstance(project).getBinaryPath();
            if (!configured.isBlank()) {
                final var found = JavafmtBinary.resolve(configured).flatMap(BinaryIdentity::of);
                if (found.isEmpty()) {
                    notifyUser(
                            project,
                            NotificationType.WARNING,
                            "No javafmt binary found at " + configured + ". Fix javafmt.binaryPath under Settings | Tools | javafmt, or clear it.");
                }
                return found;
            }
            final var onPath = provider.onPath();
            if (onPath.isPresent()) {
                return onPath;
            }
            final var download = provider.download(project);
            download.thenAccept(installation -> reportFailedDownload(project, provider, installation));
            if (download.isDone() && download.join() instanceof Installation.Installed installed) {
                return BinaryIdentity.of(installed.executable());
            }
            if (!download.isDone() && provider.claimDownloadNotice()) {
                notifyUser(project, NotificationType.INFORMATION, "javafmt is downloading its formatter; formatting will start when it's ready.");
            }
            return Optional.empty();
        }

        private static void reportFailedDownload(final Project project, final JavafmtBinaryProvider provider, final Installation installation) {
            if (installation instanceof Installation.Failed failed && provider.claimFailureNotice()) {
                LOG.warn("javafmt: " + failed.reason());
                notifyUser(
                        project,
                        NotificationType.WARNING,
                        "javafmt could not download its binary: " + failed.reason()
                                + ". Set javafmt.binaryPath under Settings | Tools | javafmt, or put javafmt on your PATH.");
            }
        }

        private void install(final Project project, final String fileName, final String content, final JavafmtBinary.Outcome.Formatted formatted) {
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
        public synchronized boolean cancel() {
            if (worker != null) {
                worker.interrupt();
            }
            return true;
        }
    }
}
