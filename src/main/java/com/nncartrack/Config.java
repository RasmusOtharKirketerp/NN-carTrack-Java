package com.nncartrack;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Config {
    private static final DateTimeFormatter RUN_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    public static final String RUN_TIMESTAMP =
        System.getProperty("nn.run.ts", LocalDateTime.now().format(RUN_TS_FORMAT));

    public static boolean isInferenceOnly() {
        return "play".equalsIgnoreCase(System.getProperty("nn.mode", ""));
    }
    public static boolean shouldResumeTraining() {
        return Boolean.parseBoolean(System.getProperty("nn.resume.training", "false"));
    }
    public static boolean isFileLoggingEnabled() {
        return Boolean.parseBoolean(System.getProperty("nn.filelogs", "true"));
    }
    public static boolean isHeadlessLogsOnly() {
        return Boolean.parseBoolean(System.getProperty("nn.headless", "false"))
            || Boolean.parseBoolean(System.getProperty("java.awt.headless", "false"));
    }
    public static int playModeCarCount() {
        return Integer.parseInt(System.getProperty("nn.play.cars", Integer.toString(PLAY_MODE_NUMBER_OF_CARS)));
    }
    public static int simulationSleepMs() {
        return Integer.parseInt(System.getProperty("nn.simulation.sleep.ms", Integer.toString(SIMULATION_SLEEP_MS)));
    }
    public static final String RUN_MODEL_DIR = "models/runs/" + RUN_TIMESTAMP;
    public static final String DEFAULT_MODEL_LOAD_FILE_PATH = "models/best-model.nn";
    public static final String DEFAULT_MODEL_SAVE_FILE_PATH = RUN_MODEL_DIR + "/best-model-current.nn";
    public static final String MODEL_LOAD_FILE_PATH =
        System.getProperty("nn.model.load.path", DEFAULT_MODEL_LOAD_FILE_PATH);
    public static final String MODEL_SAVE_FILE_PATH =
        System.getProperty("nn.model.save.path", DEFAULT_MODEL_SAVE_FILE_PATH);

    public static String completedModelFilePath(int episodesRan, double totalRuntimeSeconds) {
        int roundedSeconds = (int) Math.round(totalRuntimeSeconds);
        return String.format(
            Locale.US,
            "%s/best-model-%s-ep%d-t%ds.nn",
            RUN_MODEL_DIR,
            RUN_TIMESTAMP,
            episodesRan,
            roundedSeconds
        );
    }
    // Track parameters
    public static final double TRACK_MARGIN = 80.0;  // Margin around track
    public static final int TRACK_WIDTH = 3200;      // Track size (scaled x2)
    public static final int TRACK_HEIGHT = 600;      // Track size (scaled x2)
    
    // Window size includes margins
    public static final int WINDOW_WIDTH = TRACK_WIDTH + (int)(TRACK_MARGIN * 2);
    public static final int WINDOW_HEIGHT = TRACK_HEIGHT + (int)(TRACK_MARGIN * 2) + 100;  // Extra space for UI
    
    // Adjust starting position to account for margin
    public static final double STARTING_X = TRACK_MARGIN + 100.0;
    public static final double STARTING_Y = TRACK_HEIGHT / 2 + TRACK_MARGIN;
    public static final double DRIVABLE_MAX_X = TRACK_MARGIN + TRACK_WIDTH - 20.0;
    public static final double FINISH_LINE_X = DRIVABLE_MAX_X - 20.0;

    public static final int NUMBER_OF_CARS = 14;  // Can increase since they overlap
    public static final int PLAY_MODE_NUMBER_OF_CARS = 1;
    public static final int NUMBER_OF_EPISODES = 150;  // Increased for better learning

    // Rewards
    public static final double FINISH_REWARD = 5000.0;
    public static final double PROGRESS_MULTIPLIER = 4.0;
    public static final double OUT_OF_BOUNDS_PENALTY = -30.0;
    public static final double OBSTACLE_COLLISION_PENALTY = -140.0;
    public static final double OBSTACLE_COLLISION_SPEED_PENALTY = 12.0;
    public static final double OBSTACLE_CLOSE_PENALTY = -0.5;
    public static final double OBSTACLE_NEAR_PENALTY = -0.2;
    public static final double SHARP_TURN_PENALTY = -0.1;
    public static final double STATIONARY_PENALTY = -0.2;  // Penalty per update when not moving
    public static final double MIN_MOVEMENT_THRESHOLD = 10;
    public static final double FORWARD_PROGRESS_REWARD = 1.0;
    public static final double NEW_TERRITORY_REWARD = 2.4;
    public static final double BACKWARD_PROGRESS_PENALTY_MULTIPLIER = 0.8;
    public static final double HIGH_SPEED_THRESHOLD = 18.0;
    public static final double HIGH_SPEED_PENALTY_FACTOR = 0.9;
    public static final int STUCK_WINDOW_STEPS = 30;
    public static final double STUCK_PROGRESS_THRESHOLD = 25.0;
    public static final double STUCK_WINDOW_PENALTY = -6.0;
    
    // DQN Parameters
    public static final double GAMMA = 0.99;  // Discount factor
    public static final double EPSILON_START = 1.0;
    public static final double EPSILON_MIN = 0.1;  // Lower minimum
    // Adjusted decay for smoother transition
    public static final double EPSILON_DECAY = 0.992;  // Slower decay
    public static final int MIN_STEPS_PER_EPISODE = 1200;
    public static final double STEPS_PER_TRACK_PIXEL = 1.92;
    public static final int MEMORY_SIZE = 50000; // Increased from 10000

    // Prioritized Experience Replay Parameters
    public static final double PER_ALPHA = 0.7;  // How much prioritization is used
    public static final double PER_BETA_START = 0.4; // Initial value of beta
    public static final double PER_BETA_INCREMENT = 0.001; // Increment per step
    public static final double PER_EPSILON = 1e-6; // Small constant to ensure no experience has zero probability

    public static final int BATCH_SIZE = 64;
    public static final int TRAIN_EVERY_N_STEPS = 4;
    public static final int TRAIN_LOG_EVERY_N_BATCHES = 1;
    public static final int LOG_FLUSH_EVERY_N_BATCHES = 100;
    public static final int SUCCESS_BOOST_MAX_EXTRA_BATCHES = 12;
    public static final double SUCCESS_BOOST_MIN_FINISH_RATIO = 0.10;

    // Neural Network Parameters
    public static final int SENSOR_RAY_COUNT = 6;
    public static final int INPUT_SIZE = SENSOR_RAY_COUNT + 2;  // [sensor rays..., X, Y]
    public static final int HIDDEN_SIZE = 64;
    public static final int OUTPUT_SIZE = 4;  // Four possible actions: up, down, left, right
    public static final double LEARNING_RATE = 0.001;
    public static final double WEIGHT_INIT_STD = 2.0;  // For weight initialization
    
    // Car Parameters
    public static final double CAR_SIZE = 8;  // Diameter of car circle
    public static final double DIRECTION_LINE_LENGTH = 20;
    public static final double SENSOR_RANGE = 300;
    public static final double INITIAL_SPEED = 4.0;
    public static final double MAX_SPEED = 24.0;
    public static final int TIMES_ACC = 3;  // Quicker acceleration ramp in the 2x track
    public static final double ACC_MODIFIER = 1.2;  // Gradual, stable speed increase
    public static final double STEP_PENALTY = 0.1;

    // Obstacle (boxed icon) parameters
    public static final int OBSTACLE_COUNT = 3;
    public static final int OBSTACLE_WIDTH = 180;
    public static final int OBSTACLE_HEIGHT = 280;
    public static final int OBSTACLE_X = (int) (TRACK_MARGIN + TRACK_WIDTH * 0.55);
    public static final int OBSTACLE_Y = (int) (TRACK_MARGIN + (TRACK_HEIGHT - OBSTACLE_HEIGHT) / 2.0);
    public static final int OBSTACLE_GAP = 20;
    
    // Visualization Parameters
    public static final int LIVE_DATA_WINDOW_WIDTH = 800;
    public static final int LIVE_DATA_WINDOW_HEIGHT = 600;
    public static final int MAX_DATA_POINTS = 100;
    public static final int SIMULATION_SLEEP_MS = 0;
    public static final int RENDER_EVERY_N_STEPS = 4;
    public static final int TRAIL_MAX_POINTS = 12000;
    public static final int TRAIL_MIN_ALPHA = 20;
    public static final int TRAIL_MAX_ALPHA = 570;
    public static final boolean ENABLE_CAMERA_FOLLOW = true;
    public static final int CAMERA_FOLLOW_CAR_INDEX = 0;
    public static final int EPISODE_TIMELINE_MAX = 120;

    // UI Update Parameters
    public static final int UI_PAUSE_INTERVAL_MS = 3;  // ~60 FPS
    public static final int UI_QUEUE_CAPACITY = 1000;    // Maximum number of pending updates

    public static int dynamicStepsPerEpisode() {
        int scaled = (int) Math.ceil(TRACK_WIDTH * STEPS_PER_TRACK_PIXEL);
        return Math.max(MIN_STEPS_PER_EPISODE, scaled);
    }

    public static int numberOfCars() {
        return isInferenceOnly() ? playModeCarCount() : NUMBER_OF_CARS;
    }

    public static int obstacleX(int index) {
        switch (index) {
            case 0:
                return OBSTACLE_X - 500; // top block earlier
            case 1:
                return OBSTACLE_X;       // middle block center
            case 2:
                return OBSTACLE_X + 500; // bottom block later
            default:
                return OBSTACLE_X;
        }
    }

    public static int obstacleY(int index) {
        switch (index) {
            case 0:
                return (int) TRACK_MARGIN + OBSTACLE_GAP; // top
            case 1:
                return OBSTACLE_Y; // middle
            case 2:
                return (int) (TRACK_MARGIN + TRACK_HEIGHT - OBSTACLE_HEIGHT - OBSTACLE_GAP); // bottom
            default:
                return OBSTACLE_Y;
        }
    }
}
