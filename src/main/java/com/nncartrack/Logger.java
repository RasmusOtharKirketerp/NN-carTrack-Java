
package com.nncartrack;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public class Logger {
    private static final Path METRICS_FILE = Path.of("logs", "training_metrics.csv");
    private static final Path TRAINING_BATCH_FILE = Path.of("logs", "training_batches.csv");
    private static final Path RUN_METADATA_FILE = Path.of("logs", "run_metadata.txt");
    private static Logger instance = new Logger();
    private boolean verbose = false;
    private final boolean fileLoggingEnabled = Config.isFileLoggingEnabled();
    private int currentEpisode = 0;
    private long globalBatchStep = 0;
    private Writer metricsWriter;
    private Writer batchWriter;

    private Logger() {
        if (!fileLoggingEnabled) {
            return;
        }
        try {
            Files.createDirectories(METRICS_FILE.getParent());
            metricsWriter = Files.newBufferedWriter(
                METRICS_FILE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            batchWriter = Files.newBufferedWriter(
                TRAINING_BATCH_FILE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            metricsWriter.write("episode,time_s,cars_completed,total_cars,average_reward,best_reward,worst_reward,epsilon,max_q,loss\n");
            batchWriter.write("batch_step,episode,batch_size,memory_size,beta,sum_priorities,epsilon,loss,max_q,mean_q,mean_td_error,max_td_error,done_ratio,action0_ratio,action1_ratio,action2_ratio,action3_ratio\n");
            writeRunMetadata();
            Runtime.getRuntime().addShutdownHook(new Thread(this::closeWritersSafely));
        } catch (IOException e) {
            System.err.println("Failed to initialize metrics log file: " + e.getMessage());
        }
    }

    public static Logger getInstance() {
        return instance;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public synchronized void setCurrentEpisode(int episode) {
        this.currentEpisode = episode;
    }

    public void logCarFinish(double reward) {
        if (verbose) {
            System.out.println("Car finished with reward: " + reward);
        }
    }

    public void logEpisodeStats(int episode, double episodeTime, int carsFinished, int totalCars, 
                              double averageReward, double bestReward, double worstReward, double epsilon, double maxQ, double loss) {
        System.out.println("\n=== Episode " + episode + " Statistics ===");
        System.out.println("Time: " + String.format("%.2f", episodeTime) + "s");
        System.out.println("Cars completed: " + carsFinished + "/" + totalCars);
        System.out.println("Average Reward: " + String.format("%.2f", averageReward));
        System.out.println("Best Reward: " + String.format("%.2f", bestReward));
        System.out.println("Worst Reward: " + String.format("%.2f", worstReward));
        System.out.println("Epsilon: " + String.format("%.4f", epsilon));
        System.out.println("Max Q: " + String.format("%.2f", maxQ));
        System.out.println("Loss: " + String.format("%.6f", loss));
        System.out.println("================================\n");
        appendMetrics(episode, episodeTime, carsFinished, totalCars, averageReward, bestReward, worstReward, epsilon, maxQ, loss);
        flushWritersSafely();
    }

    private synchronized void appendMetrics(int episode, double episodeTime, int carsFinished, int totalCars,
                                            double averageReward, double bestReward, double worstReward,
                                            double epsilon, double maxQ, double loss) {
        if (!fileLoggingEnabled) {
            return;
        }
        String row = String.format(
            Locale.US,
            "%d,%.6f,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
            episode, episodeTime, carsFinished, totalCars, averageReward, bestReward, worstReward, epsilon, maxQ, loss
        );
        try {
            if (metricsWriter != null) {
                metricsWriter.write(row);
            }
        } catch (IOException e) {
            System.err.println("Failed to append metrics log: " + e.getMessage());
        }
    }

    public synchronized void logTrainingBatch(
        int batchSize,
        int memorySize,
        double beta,
        double sumPriorities,
        double epsilon,
        double loss,
        double maxQ,
        double meanQ,
        double meanTdError,
        double maxTdError,
        double doneRatio,
        int[] actionCounts
    ) {
        if (!fileLoggingEnabled) {
            return;
        }
        globalBatchStep++;
        String row = String.format(
            Locale.US,
            "%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
            globalBatchStep,
            currentEpisode,
            batchSize,
            memorySize,
            beta,
            sumPriorities,
            epsilon,
            loss,
            maxQ,
            meanQ,
            meanTdError,
            maxTdError,
            doneRatio,
            actionCounts[0] / (double) batchSize,
            actionCounts[1] / (double) batchSize,
            actionCounts[2] / (double) batchSize,
            actionCounts[3] / (double) batchSize
        );
        try {
            if (batchWriter != null) {
                batchWriter.write(row);
                if (globalBatchStep % Config.LOG_FLUSH_EVERY_N_BATCHES == 0) {
                    batchWriter.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to append batch metrics log: " + e.getMessage());
        }
    }

    public synchronized void flush() {
        flushWritersSafely();
    }

    private void writeRunMetadata() throws IOException {
        if (!fileLoggingEnabled) {
            return;
        }
        String metadata = String.format(
            Locale.US,
            "TRACK_NAME=%s%nTRACK_FILE=%s%nTRACK_WIDTH=%d%nTRACK_HEIGHT=%d%nNUMBER_OF_CARS=%d%nNUMBER_OF_EPISODES=%d%n" +
            "MIN_STEPS_PER_EPISODE=%d%nSTEPS_PER_TRACK_PIXEL=%.4f%nMEMORY_SIZE=%d%nBATCH_SIZE=%d%n" +
            "TRAIN_EVERY_N_STEPS=%d%nTRAIN_LOG_EVERY_N_BATCHES=%d%nLEARNING_RATE=%.6f%nGAMMA=%.6f%n" +
            "EPSILON_START=%.6f%nEPSILON_MIN=%.6f%nEPSILON_DECAY=%.6f%nPER_ALPHA=%.6f%nPER_BETA_START=%.6f%nPER_BETA_INCREMENT=%.6f%n",
            Config.TRACK_NAME,
            Config.TRACK_FILE_PATH,
            Config.TRACK_WIDTH,
            Config.TRACK_HEIGHT,
            Config.numberOfCars(),
            Config.NUMBER_OF_EPISODES,
            Config.MIN_STEPS_PER_EPISODE,
            Config.STEPS_PER_TRACK_PIXEL,
            Config.MEMORY_SIZE,
            Config.BATCH_SIZE,
            Config.TRAIN_EVERY_N_STEPS,
            Config.TRAIN_LOG_EVERY_N_BATCHES,
            Config.LEARNING_RATE,
            Config.GAMMA,
            Config.EPSILON_START,
            Config.EPSILON_MIN,
            Config.EPSILON_DECAY,
            Config.PER_ALPHA,
            Config.PER_BETA_START,
            Config.PER_BETA_INCREMENT
        );
        Files.writeString(
            RUN_METADATA_FILE,
            metadata,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private synchronized void flushWritersSafely() {
        if (!fileLoggingEnabled) {
            return;
        }
        try {
            if (metricsWriter != null) {
                metricsWriter.flush();
            }
            if (batchWriter != null) {
                batchWriter.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to flush log writers: " + e.getMessage());
        }
    }

    private synchronized void closeWritersSafely() {
        if (!fileLoggingEnabled) {
            return;
        }
        try {
            if (metricsWriter != null) {
                metricsWriter.flush();
                metricsWriter.close();
                metricsWriter = null;
            }
            if (batchWriter != null) {
                batchWriter.flush();
                batchWriter.close();
                batchWriter = null;
            }
        } catch (IOException e) {
            System.err.println("Failed to close log writers: " + e.getMessage());
        }
    }
}
