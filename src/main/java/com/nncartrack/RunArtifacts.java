package com.nncartrack;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RunArtifacts {
    private static final Path METRICS_FILE = Path.of("logs", "training_metrics.csv");
    private static final Path TRAINING_BATCH_FILE = Path.of("logs", "training_batches.csv");
    private static final Path RUN_METADATA_FILE = Path.of("logs", "run_metadata.txt");
    private static final Path TRACK_FILE = Path.of(Config.TRACK_FILE_PATH);
    private static final AtomicBoolean CONSOLE_CAPTURE_STARTED = new AtomicBoolean(false);
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;

    private RunArtifacts() {
    }

    public static void initializeForTrainingRun() {
        if (Config.isInferenceOnly()) {
            return;
        }
        try {
            Files.createDirectories(runDirectory());
        } catch (IOException e) {
            ORIGINAL_ERR.println("Failed to create run artifacts directory: " + e.getMessage());
            return;
        }
        startConsoleCapture();
    }

    public static void copyAnalysisFilesToRunFolder() {
        if (Config.isInferenceOnly()) {
            return;
        }
        Logger.getInstance().flush();
        System.out.flush();
        System.err.flush();
        copyIfPresent(METRICS_FILE, runDirectory().resolve(METRICS_FILE.getFileName()));
        copyIfPresent(TRAINING_BATCH_FILE, runDirectory().resolve(TRAINING_BATCH_FILE.getFileName()));
        copyIfPresent(RUN_METADATA_FILE, runDirectory().resolve(RUN_METADATA_FILE.getFileName()));
        copyIfPresent(TRACK_FILE, runDirectory().resolve("track.json"));
    }

    public static Path runDirectory() {
        return Path.of(Config.RUN_MODEL_DIR);
    }

    public static Path consoleLogPath() {
        return runDirectory().resolve("console.log");
    }

    private static void startConsoleCapture() {
        if (!CONSOLE_CAPTURE_STARTED.compareAndSet(false, true)) {
            return;
        }
        try {
            OutputStream logFile = new LockedOutputStream(Files.newOutputStream(
                consoleLogPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            ));
            System.setOut(buildTeePrintStream(ORIGINAL_OUT, logFile));
            System.setErr(buildTeePrintStream(ORIGINAL_ERR, logFile));
            System.out.println("Run artifacts dir: " + runDirectory().toAbsolutePath());
            System.out.println("Console log: " + consoleLogPath().toAbsolutePath());
        } catch (IOException e) {
            ORIGINAL_ERR.println("Failed to start console capture: " + e.getMessage());
        }
    }

    private static PrintStream buildTeePrintStream(PrintStream consoleStream, OutputStream logFile)
        throws IOException {
        return new PrintStream(
            new TeeOutputStream(consoleStream, logFile),
            true,
            StandardCharsets.UTF_8
        );
    }

    private static void copyIfPresent(Path source, Path target) {
        if (!Files.exists(source)) {
            return;
        }
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to copy run artifact " + source + ": " + e.getMessage());
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream primary;
        private final OutputStream secondary;

        private TeeOutputStream(OutputStream primary, OutputStream secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public void write(int b) throws IOException {
            primary.write(b);
            secondary.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            primary.write(b, off, len);
            secondary.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            primary.flush();
            secondary.flush();
        }
    }

    private static final class LockedOutputStream extends OutputStream {
        private final OutputStream delegate;

        private LockedOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public synchronized void flush() throws IOException {
            delegate.flush();
        }
    }
}
