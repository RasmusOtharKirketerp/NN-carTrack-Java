package com.nncartrack;

public class Config {
    // Track parameters
    public static final double TRACK_MARGIN = 40.0;  // Margin around track
    public static final int TRACK_WIDTH = 800;       // Track size
    public static final int TRACK_HEIGHT = 600;      // Track size
    
    // Window size includes margins
    public static final int WINDOW_WIDTH = TRACK_WIDTH + (int)(TRACK_MARGIN * 2);
    public static final int WINDOW_HEIGHT = TRACK_HEIGHT + (int)(TRACK_MARGIN * 2);
    
    // Adjust starting position to account for margin
    public static final double STARTING_X = TRACK_MARGIN + 50.0;
    public static final double STARTING_Y = TRACK_HEIGHT / 2 + TRACK_MARGIN;

    public static final int NUMBER_OF_CARS = 20;  // Can increase since they overlap
    public static final int NUMBER_OF_EPISODES = 100;  // Increased for better learning

    // Rewards
    public static final double FINISH_REWARD = 50.0;
    public static final double PROGRESS_MULTIPLIER = 12.0;
    public static final double OUT_OF_BOUNDS_PENALTY = -10.0;
    public static final double OBSTACLE_CLOSE_PENALTY = -0.5;
    public static final double OBSTACLE_NEAR_PENALTY = -0.2;
    public static final double SHARP_TURN_PENALTY = -0.1;
    public static final double STATIONARY_PENALTY = -100;  // Penalty per update when not moving
    public static final double FORWARD_PROGRESS_REWARD = 0.8;  // Multiplier for forward movement
    
    // DQN Parameters
    public static final double GAMMA = 0.99;  // Discount factor
    public static final double EPSILON_START = 1.0;
    public static final double EPSILON_MIN = 0.1;  // Lower minimum
    // Adjusted decay for smoother transition
    public static final double EPSILON_DECAY = 0.98;  // Slower decay
    public static final int STEPS_PER_EPISODE = 800; // New constant
    public static final int MEMORY_SIZE = 50000; // Increased from 10000

    // Prioritized Experience Replay Parameters
    public static final double PER_ALPHA = 0.7;  // How much prioritization is used
    public static final double PER_BETA_START = 0.4; // Initial value of beta
    public static final double PER_BETA_INCREMENT = 0.001; // Increment per step
    public static final double PER_EPSILON = 1e-6; // Small constant to ensure no experience has zero probability

    public static final int BATCH_SIZE = 32;

    // Neural Network Parameters
    public static final int INPUT_SIZE = 3;  // Adjusted for [Obstacle Distance, X Position, Y Position]
    public static final int HIDDEN_SIZE = 24;
    public static final int OUTPUT_SIZE = 4;  // Four possible actions: up, down, left, right
    public static final double LEARNING_RATE = 0.001;
    public static final double WEIGHT_INIT_STD = 2.0;  // For weight initialization

    // Car Parameters
    public static final double CAR_SIZE = 5;  // Diameter of car circle
    public static final double DIRECTION_LINE_LENGTH = 20;
    public static final double SENSOR_RANGE = 150;
    public static final double INITIAL_SPEED = 1.0;
    public static final double MAX_SPEED = 10;
    public static final int TIMES_ACC = 10;  // Number of times the same action can be repeated to increase speed
    public static final double ACC_MODIFIER = 1.5;  // Multiplier for speed increase
    
    // Obstacle Parameters
    public static final double OBSTACLE_SIZE = 120;  // Width/Height of obstacle
    public static final double COLLISION_DISTANCE = 15;
    public static final double OBSTACLE_CLOSE_DISTANCE = 10;
    public static final double OBSTACLE_NEAR_DISTANCE = 30;

    // Visualization Parameters
    public static final int LIVE_DATA_WINDOW_WIDTH = 800;
    public static final int LIVE_DATA_WINDOW_HEIGHT = 600;
    public static final int MAX_DATA_POINTS = 100;
    public static final int SIMULATION_SLEEP_MS = 2;

    // UI Update Parameters
    public static final int UI_UPDATE_INTERVAL_MS = 16;  // ~60 FPS
    public static final int UI_QUEUE_CAPACITY = 1000;    // Maximum number of pending updates
}