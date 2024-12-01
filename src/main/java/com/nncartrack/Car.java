package com.nncartrack;

import java.awt.Color;
import java.awt.Graphics;

public class Car {
    // Car properties
    private double x, y;
    private double speed;
    private int lastAction = -1;
    private int actionCount = 0;

    // Sensor range
    private double sensorRange = Config.SENSOR_RANGE;

    // Neural network brain
    private NeuralNetwork brain;

    // Total reward
    private double totalReward = 0;

    // Track boundaries - using track dimensions with margin
    private static final double MIN_X = Config.TRACK_MARGIN + 20;
    private static final double MAX_X = Config.TRACK_MARGIN + Config.TRACK_WIDTH - 20;
    private static final double MIN_Y = Config.TRACK_MARGIN + 20;
    private static final double MAX_Y = Config.TRACK_MARGIN + Config.TRACK_HEIGHT - 20;

    private boolean isOutOfBoundsState = false;

    // Add finish line constant
    private static final double FINISH_LINE_X = MAX_X - 20;
    private double startX; // Store initial X position
    private boolean hasFinished = false;

    // Add new fields at the top
    private double lastX, lastY;
    private static final double MIN_MOVEMENT_THRESHOLD = 0.5;

    // Add new field for car index
    private int carIndex;

    public Car(double x, double y, int index) {
        this.startX = x;
        this.x = x;
        this.y = y;
        this.carIndex = index;
        this.speed = Config.INITIAL_SPEED;
        brain = new NeuralNetwork();  // Create new brain instead of using shared
    }

    // Update the car's position based on neural network's output
    public void update(double obstacleDistance) {
        // Don't update if already out of bounds
        if (isOutOfBoundsState) {
            return;
        }

        // Normalize inputs
        double normalizedX = (x - MIN_X) / (MAX_X - MIN_X);
        double normalizedY = (y - MIN_Y) / (MAX_Y - MIN_Y);
        double normalizedDistance = obstacleDistance / sensorRange;

        // Inputs: [Obstacle Distance, X Position, Y Position]
        double[] inputs = { normalizedDistance, normalizedX, normalizedY };
        
        // Use selectAction to get the actual action taken
        int action = brain.selectAction(inputs);

        // Adjust speed based on repeated actions
        if (action == lastAction) {
            actionCount++;
            if (actionCount >= Config.TIMES_ACC) {
                speed = Math.min(speed * Config.ACC_MODIFIER, Config.MAX_SPEED);
            }
        } else {
            actionCount = 0;
            speed *= 0.95;  // Reduce speed by 5%
            speed = Math.max(speed, Config.INITIAL_SPEED);  // Ensure speed doesn't drop below initial speed
        }
        lastAction = action;

        // Move the car based on the selected action
        switch (action) {
            case 0: y -= speed; break; // Up
            case 1: y += speed; break; // Down
            case 2: x -= speed; break; // Left
            case 3: x += speed; break; // Right
        }

        // Check boundaries before updating position
        if (x < MIN_X || x > MAX_X || y < MIN_Y || y > MAX_Y) {
            isOutOfBoundsState = true;
            speed = 0;
            double penalty = Config.OUT_OF_BOUNDS_PENALTY;
            totalReward += penalty;
            double[] nextState = { normalizedDistance, normalizedX, normalizedY };
            brain.trainWithReward(inputs, penalty, nextState, true);
            return;
        }

        // Calculate reward
        double reward = calculateReward(obstacleDistance);
        totalReward += reward;

        // Prepare nextState and done flag
        double[] nextState = { normalizedDistance, normalizedX, normalizedY };
        boolean done = isOutOfBoundsState || hasFinished;
        
        // Add experience to shared replay memory and train
        brain.trainWithReward(inputs, reward, nextState, done);
    }

    // Simulate sensor (distance to obstacle)
    public double senseObstacle(Obstacle obstacle) {
        double dx = obstacle.getX() - x;
        double dy = obstacle.getY() - y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance < sensorRange ? distance : sensorRange;
    }

    // Calculate the reward based on the car's state
    private double calculateReward(double obstacleDistance) {
        double reward = 0;

        // Calculate movement since last update
        double movementDelta = Math.sqrt(Math.pow(x - lastX, 2) + Math.pow(y - lastY, 2));
        
        // Penalize staying still or moving very little
        if (movementDelta < MIN_MOVEMENT_THRESHOLD) {
            reward += Config.STATIONARY_PENALTY;
        }
        
        // Reward forward progress (movement towards finish line)
        if (x > lastX) {
            reward += (x - lastX) * Config.FORWARD_PROGRESS_REWARD;
        }
        
        // Update last position
        lastX = x;
        lastY = y;

        double progressToFinish = (x - startX) / (FINISH_LINE_X - startX);
        reward += progressToFinish * Config.PROGRESS_MULTIPLIER;

        if (x >= FINISH_LINE_X && !hasFinished) {
            reward += Config.FINISH_REWARD;
            hasFinished = true;
            isOutOfBoundsState = true;
            speed = 0;
        }

        if (obstacleDistance < Config.OBSTACLE_CLOSE_DISTANCE) {
            reward += Config.OBSTACLE_CLOSE_PENALTY;
        } else if (obstacleDistance < Config.OBSTACLE_NEAR_DISTANCE) {
            reward += Config.OBSTACLE_NEAR_PENALTY;
        }

        return reward;
    }

    // Render the car
    public void draw(Graphics g) {
        // Set color based on finish state or out of bounds
        g.setColor((hasFinished || isOutOfBoundsState) ? Color.RED : Color.BLUE);
        g.fillOval((int) x - (int)(Config.CAR_SIZE/2), 
                   (int) y - (int)(Config.CAR_SIZE/2), 
                   (int)Config.CAR_SIZE, 
                   (int)Config.CAR_SIZE);

        // Draw car number
        g.setColor(Color.BLACK);
        g.drawString(String.valueOf(carIndex), (int) x - 3, (int) y - 7);

        // Draw finish line
        if (g != null) {
            g.setColor(Color.GREEN);
            g.drawLine((int)FINISH_LINE_X, (int)MIN_Y, (int)FINISH_LINE_X, (int)MAX_Y);
        }
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getTotalReward() { return totalReward; }
    public void setTotalReward(double totalReward) { this.totalReward = totalReward; }
    public static double getMinX() { return MIN_X; }
    public static double getMinY() { return MIN_Y; }
    public static double getMaxX() { return MAX_X; }
    public static double getMaxY() { return MAX_Y; }

    public NeuralNetwork getBrain() {
        return brain;
    }

    public void reset(double startX, double startY) {
        x = startX;
        y = startY;
        speed = Config.INITIAL_SPEED;
        isOutOfBoundsState = false;
        this.startX = startX;
        hasFinished = false;
        totalReward = 0; // Make sure reward is reset properly
        lastX = startX;
        lastY = startY;
        lastAction = -1;
        actionCount = 0;
    }

    public boolean hasFinished() {
        return hasFinished;
    }
}
