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

    public static final int NUMBER_OF_CARS = 100;  // Can increase since they overlap
    public static final int NUMBER_OF_EPISODES = 100;  // Increased for better learning

    // Rewards
    public static final double FINISH_REWARD = 50.0;
    public static final double PROGRESS_MULTIPLIER = 2.0;
    public static final double OUT_OF_BOUNDS_PENALTY = -10.0;
    public static final double OBSTACLE_CLOSE_PENALTY = -0.5;
    public static final double OBSTACLE_NEAR_PENALTY = -0.2;
    public static final double SHARP_TURN_PENALTY = -0.1;
    public static final double STATIONARY_PENALTY = -10;  // Penalty per update when not moving
    public static final double FORWARD_PROGRESS_REWARD = 0.8;  // Multiplier for forward movement
    
    // DQN Parameters
    public static final double GAMMA = 0.99;  // Discount factor
    public static final double EPSILON_START = 1.0;
    public static final double EPSILON_MIN = 0.01;  // Lower minimum
    // Adjusted decay for smoother transition
    public static final double EPSILON_DECAY = 0.95;  // Fixed decay rate instead of calculated
    public static final int STEPS_PER_EPISODE = 1000; // New constant
    public static final int MEMORY_SIZE = 10000;
    public static final int BATCH_SIZE = 32;
}