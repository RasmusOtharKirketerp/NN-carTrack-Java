package com.nncartrack;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;

public class Car {
    // Car properties
    private double x, y;
    private double speed;
    private int lastAction = -1;
    private int actionCount = 0;

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
        // Remove obstacle distance normalization
        double[] inputs = { 0, normalizedX, normalizedY }; // Pass 0 as obstacle distance
        
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
            double[] nextState = { 0, normalizedX, normalizedY }; // Pass 0 as obstacle distance
            brain.trainWithReward(inputs, penalty, nextState, true);
            return;
        }

        // Calculate reward
        double reward = calculateReward();
        totalReward += reward;

        // Prepare nextState and done flag
        double[] nextState = { 0, normalizedX, normalizedY }; // Pass 0 as obstacle distance
        boolean done = isOutOfBoundsState || hasFinished;
        
        // Add experience to shared replay memory and train
        brain.trainWithReward(inputs, reward, nextState, done);
    }

    // Calculate the reward based on the car's state
    private double calculateReward() {
        double reward = 0;

        // Penalty for using many steps
        reward -= Config.STEP_PENALTY;

        // Calculate movement since last update
        double movementDelta = Math.sqrt(Math.pow(x - lastX, 2) + Math.pow(y - lastY, 2));
        
        // Penalize staying still or moving very little
        if (movementDelta < Config.MIN_MOVEMENT_THRESHOLD) {
            reward += Config.STATIONARY_PENALTY;
        }
        
        // Reward forward progress based on absolute position, but only if moving forward
        if (x > lastX) {
          reward += (x - startX) * Config.FORWARD_PROGRESS_REWARD;
        }

        // Penalize backward movement
        if (x < lastX) {
            reward -= (lastX - x) * Config.FORWARD_PROGRESS_REWARD * 0.5;
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

        return reward;
    }

    // Render the car
    public void draw(Graphics g, ArrayList<Car> cars) {
        // Determine the car with the highest and lowest score
        Car highestScoreCar = cars.get(0);
        Car lowestScoreCar = cars.get(0);
        for (Car car : cars) {
            if (car.getTotalReward() > highestScoreCar.getTotalReward()) {
                highestScoreCar = car;
            }
            if (car.getTotalReward() < lowestScoreCar.getTotalReward()) {
                lowestScoreCar = car;
            }
        }

        // Set color based on finish state or out of bounds
        if (this == highestScoreCar || this == lowestScoreCar) {
            g.setColor((hasFinished || isOutOfBoundsState) ? Color.RED : Color.BLUE);
        } else {
            g.setColor(new Color(200, 200, 50)); // Dim color for other cars
        }
        g.fillOval((int) x - (int)(Config.CAR_SIZE/2), 
                   (int) y - (int)(Config.CAR_SIZE/2), 
                   (int)Config.CAR_SIZE, 
                   (int)Config.CAR_SIZE);

        // Draw car number and score only for the highest and lowest score cars
        if (this == highestScoreCar || this == lowestScoreCar) {
            g.setColor(Color.BLACK);
            g.drawString(String.format("Car %d: %.2f", carIndex, totalReward), (int) x - 20, (int) y - 10);
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
