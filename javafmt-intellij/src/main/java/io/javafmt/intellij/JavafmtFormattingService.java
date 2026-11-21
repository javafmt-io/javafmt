package io.javafmt.intellij;

import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import io.javafmt.JavafmtConfig;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs javafmt from <em>Reformat Code</em>, inside the IDE or through the native {@code javafmt}
 * binary, as {@link JavafmtApplicationSettings#getEngine()} selects for the whole IDE, and logs how long each format took.
 *
 * <p>An {@link AsyncDocumentFormattingService} rather than an {@code ExternalFormatProcessor}
 * because a format can take a while. The platform calls an {@code ExternalFormatProcessor}
 * synchronously from {@code CodeStyleManagerImpl.reformatText}, on the EDT, with the write lock
 * held: a large file, a binary on a cold network mount, or one caught by an antivirus scan, would
 * freeze the whole IDE for as long as it took, with no way to cancel. This base class instead runs the
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
        return file instanceof PsiJavaFile
            && JavafmtSettings.getInstance(file.getProject()).isEnabled();
    }

    @Override
    protected FormattingTask createFormattingTask(@NotNull final AsyncFormattingRequest request) {
        return new JavafmtFormattingTask(request);
    }

    @Override
    @NotNull
    protected Duration getTimeout() {
        return JavafmtDaemons.FORMAT_TIMEOUT.plus(JavafmtBinary.TIMEOUT);
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

    static String timing(final String fileName,
                         final JavafmtApplicationSettings.Engine engine,
                         final JavafmtConfig.Analysis analysis,
                         final Duration took) {
        final var engineLabel = switch (engine) {
            case IN_PROCESS -> "in-process";
            case NATIVE -> "native";
        };
        final var analysisLabel = switch (analysis) {
            case JavafmtConfig.Analysis.Attributed _ -> "analyzed";
            case JavafmtConfig.Analysis.ParseOnly _ -> "parse-only";
        };
        return "javafmt: formatted " + fileName + " in " + took.toMillis() + " ms (" + engineLabel + ", " + analysisLabel + ")";
    }

    /**
     * Tells the user javafmt did nothing and why. An internal diagnostic is a javafmt bug and is
     * reported as an error; everything else is a problem with the source in front of the user,
     * which they can act on themselves.
     */
    private static void leftUnformatted(final Project project,
                                        final String fileName,
                                        final boolean internal,
                                        final String detail,
                                        final Consumer<String> log) {
        final var message = fileName + " was left unformatted: " + detail;
        log.accept("javafmt: " + message);
        notifyUser(project, internal ? NotificationType.ERROR : NotificationType.WARNING, message);
    }

    private static void notifyUser(final Project project, final NotificationType type, final String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("javafmt", message, type)
                .notify(project);
    }

    private static final class JavafmtFormattingTask implements FormattingTask {

        private final AsyncFormattingRequest request;

        /**
         * The thread the platform runs {@link #run} on, published so {@link #cancel} can interrupt
         * it. {@code JavafmtDaemon.format} waits for the reply with an interruptible wait, so an
         * interrupt there returns at once, leaves the file alone without a notification, and the
         * daemon drops the late reply.
         */
        private Optional<Thread> worker = Optional.empty();

        private boolean interrupted;

        private JavafmtFormattingTask(final AsyncFormattingRequest request) {
            this.request = request;
        }

        @Override
        public void run() {
            synchronized (this) {
                worker = Optional.of(Thread.currentThread());
            }
            try {
                format();
            } finally {
                synchronized (this) {
                    worker = Optional.empty();
                    if (interrupted) {
                        Thread.interrupted();
                    }
                }
            }
        }

        private void format() {
            final var context = request.getContext();
            final var project = context.getProject();
            final var fileName = context.getContainingFile().getName();
            final var content = request.getDocumentText();
            final var scope = Optional.ofNullable(context.getVirtualFile())
                    .map(file -> FileScope.of(project, file))
                    .orElseGet(FileScope::none);
            final var engine = JavafmtApplicationSettings.getInstance().getEngine();
            final var outcome = switch (engine) {
                case IN_PROCESS -> timed(fileName, engine, scope, () -> InProcessFormatter.format(content, scope));
                case NATIVE ->
                    supportedBinary(project)
                            .map(binary -> timed(
                                    fileName,
                                    engine,
                                    scope,
                                    () -> JavafmtDaemons.getInstance(project).format(binary, content, scope)))
                            .orElseGet(Outcome.Cancelled::new);
            };
            request.onTextReady(
                    switch (outcome) {
                        case Outcome.Cancelled _ -> content;
                        case Outcome.Refused refused -> {
                            leftUnformatted(project, fileName, refused.internal(), refused.reason(), LOG::warn);
                            yield content;
                        }
                        case Outcome.Formatted formatted -> textFor(project, fileName, content, formatted);
                    });
        }

        private static Outcome timed(final String fileName,
                                     final JavafmtApplicationSettings.Engine engine,
                                     final FileScope scope,
                                     final Supplier<Outcome> formatter) {
            final var start = System.nanoTime();
            final var outcome = formatter.get();
            if (!(outcome instanceof Outcome.Cancelled)) {
                LOG.info(timing(fileName, engine, scope.analysis(), Duration.ofNanos(System.nanoTime() - start)));
            }
            return outcome;
        }

        private static Optional<BinaryIdentity> supportedBinary(final Project project) {
            final var provider = JavafmtBinaryProvider.getInstance();
            return locate(project, provider).filter(binary -> isSupported(project, provider, binary));
        }

        private static boolean isSupported(final Project project, final JavafmtBinaryProvider provider, final BinaryIdentity binary) {
            return switch (provider.verdict(binary)) {
                case SUPPORTED -> true;
                case TOO_OLD -> {
                    notifyUser(
                            project,
                            NotificationType.WARNING,
                            "The javafmt binary at " + binary.path() + " is too old: javafmt " + JavafmtBinary.minimumVersion()
                                    + " or newer is required.");
                    yield false;
                }
                case UNKNOWN -> {
                    if (!Thread.currentThread().isInterrupted()) {
                        notifyUser(
                                project, NotificationType.WARNING, "The javafmt binary at " + binary.path() + " did not report its version in time.");
                    }
                    yield false;
                }
            };
        }

        private static Optional<BinaryIdentity> locate(final Project project, final JavafmtBinaryProvider provider) {
            return switch (provider.lookup(JavafmtSettings.getInstance(project).getBinaryPath(), project)) {
                case BinaryLookup.Found found -> Optional.of(found.binary());
                case BinaryLookup.MissingAt missing -> {
                    notifyUser(
                            project,
                            NotificationType.WARNING,
                            "No javafmt binary found at " + missing.configuredPath()
                                    + ". Fix javafmt.binaryPath under Settings | Tools | javafmt, or clear it.");
                    yield Optional.empty();
                }
                case BinaryLookup.Pending pending -> {
                    pending.download().thenAccept(installation -> reportFailedDownload(project, provider, installation));
                    if (!pending.download().isDone() && provider.claimDownloadNotice()) {
                        notifyUser(
                                project,
                                NotificationType.INFORMATION,
                                "javafmt is downloading its formatter; formatting will start when it's ready.");
                    }
                    yield Optional.empty();
                }
            };
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

        private static String textFor(final Project project, final String fileName, final String content, final Outcome.Formatted formatted) {
            if (formatted.hasFailure()) {
                leftUnformatted(
                        project, fileName, formatted.hasInternalError(), formatted.messages(), formatted.hasInternalError() ? LOG::warn : LOG::info);
                return content;
            }
            if (!formatted.diagnostics().isEmpty()) {
                // Warning-only rules fire on ordinary code, so a balloon per reformat would be
                // noise; the log keeps them reachable while the reformat itself goes through.
                LOG.info("javafmt: " + fileName + ":\n" + formatted.messages());
            }
            return formatted.output();
        }

        @Override
        public synchronized boolean cancel() {
            worker.ifPresent(thread -> {
                interrupted = true;
                thread.interrupt();
            });
            return true;
        }
    }
}
