package com.nncartrack;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;

public class Car {
    public static final int REWARD_EVT_FORWARD = 0;
    public static final int REWARD_EVT_NEW_TERRITORY = 1;
    public static final int REWARD_EVT_BACKWARD = 2;
    public static final int REWARD_EVT_STATIONARY = 3;
    public static final int REWARD_EVT_SPEED_PENALTY = 4;
    public static final int REWARD_EVT_STUCK = 5;
    public static final int REWARD_EVT_BOUNDARY_HIT = 6;
    public static final int REWARD_EVT_OBSTACLE_HIT = 7;
    public static final int REWARD_EVT_FINISH = 8;
    public static final int REWARD_EVT_COUNT = 9;

    private static final String[] REWARD_EVENT_LABELS = {
        "FWD", "NEW", "BACK", "IDLE", "FAST", "STUCK", "WALL", "OBS", "GOAL"
    };
    // Car properties
    private double x, y;
    private double speed;
    private int lastAction = -1;
    private int actionCount = 0;

    // Neural network brain
    private NeuralNetwork brain;

    // Total reward
    private double totalReward = 0;
    private double grandTotalReward = 0;

    // Track boundaries - using track dimensions with margin
    private static final double DRIVABLE_PADDING = Math.max(2.0, Config.CAR_SIZE);
    private static final double MIN_X = Config.TRACK_MARGIN + DRIVABLE_PADDING;
    private static final double MAX_X = Config.TRACK_MARGIN + Config.TRACK_WIDTH - DRIVABLE_PADDING;
    private static final double MIN_Y = Config.TRACK_MARGIN + DRIVABLE_PADDING;
    private static final double MAX_Y = Config.TRACK_MARGIN + Config.TRACK_HEIGHT - DRIVABLE_PADDING;

    private boolean isOutOfBoundsState = false;

    private double startX; // Store initial X position
    private boolean hasFinished = false;

    // Add new fields at the top
    private double lastX, lastY;

    // Add new field for car index
    private int carIndex;
    private int updateCount = 0;
    private final List<Point2D.Double> trail = new ArrayList<>();
    private static final double[][] SENSOR_DIRECTIONS = {
        {1.0, 0.0},    // forward
        {1.0, -0.5},   // forward-up
        {1.0, 0.5},    // forward-down
        {0.0, -1.0},   // up
        {0.0, 1.0},    // down
        {-1.0, 0.0}    // back
    };
    private static final double[] SENSOR_RANGE_SCALE = {
        1.0, // forward
        1.0, // forward-up
        1.0, // forward-down
        0.6, // up
        0.6, // down
        1.0  // back
    };
    private final double[] sensorDistances = new double[Config.SENSOR_RAY_COUNT];
    private double[] lastQValues = new double[Config.OUTPUT_SIZE];
    private final double[] stateBuffer = new double[Config.INPUT_SIZE];
    private final double[] nextStateBuffer = new double[Config.INPUT_SIZE];
    private int stepsSinceProgressCheck = 0;
    private double progressWindowStartX = 0.0;
    private double maxXReached = 0.0;
    private double bestEpisodeReward = Double.NEGATIVE_INFINITY;
    private double lastEpisodeReward = 0.0;
    private final Deque<Double> recentEpisodeRewards = new ArrayDeque<>();
    private static final int RECENT_REWARD_WINDOW = 5;
    private final Deque<Double> recentStepRewards = new ArrayDeque<>();
    private final int[] rewardEventCounts = new int[REWARD_EVT_COUNT];
    private final double[] rewardEventTotals = new double[REWARD_EVT_COUNT];

    public Car(double x, double y, int index) {
        this.startX = x;
        this.x = x;
        this.y = y;
        this.carIndex = index;
        this.speed = Config.INITIAL_SPEED;
        brain = new NeuralNetwork();  // Create new brain instead of using shared
        this.lastX = x;
        this.lastY = y;
        this.progressWindowStartX = x;
        this.maxXReached = x;
        trail.add(new Point2D.Double(x, y));
        for (int i = 0; i < sensorDistances.length; i++) {
            sensorDistances[i] = Config.SENSOR_RANGE;
        }
    }

    // Update the car's position based on neural network's output
    public void update() {
        updateCount++;
        // Don't update if already out of bounds
        if (isOutOfBoundsState) {
            return;
        }

        // Normalize inputs
        double[] inputs = buildStateVector(stateBuffer);
        
        NeuralNetwork.ActionSelection actionSelection = brain.selectActionWithQValues(inputs);
        int action = actionSelection.getAction();
        lastQValues = actionSelection.getQValues();

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
        trail.add(new Point2D.Double(x, y));
        if (trail.size() > Config.TRAIL_MAX_POINTS) {
            trail.remove(0);
        }

        // Enforce boundaries with penalty + full stop, but keep the car alive.
        boolean crossedFinish = x >= Config.FINISH_LINE_X;
        if (x < MIN_X || y < MIN_Y || y > MAX_Y || (x > MAX_X && !crossedFinish)) {
            // Clamp back inside the track.
            x = Math.max(MIN_X, Math.min(MAX_X, x));
            y = Math.max(MIN_Y, Math.min(MAX_Y, y));

            // Full stop and action reset so it can recover next step.
            speed = Config.INITIAL_SPEED;
            actionCount = 0;
            lastAction = -1;

            double penalty = Config.OUT_OF_BOUNDS_PENALTY;
            recordRewardEvent(REWARD_EVT_BOUNDARY_HIT, penalty);
            totalReward += penalty;
            recordStepReward();
            double[] nextState = buildStateVector(nextStateBuffer);
            brain.trainWithReward(inputs, penalty, nextState, false);
            return;
        }
        if (isInsideObstacle()) {
            double impactSpeed = speed;
            resolveObstacleOverlap();
            speed = Config.INITIAL_SPEED;
            actionCount = 0;
            lastAction = -1;
            double penalty = Config.OBSTACLE_COLLISION_PENALTY - (impactSpeed * Config.OBSTACLE_COLLISION_SPEED_PENALTY);
            recordRewardEvent(REWARD_EVT_OBSTACLE_HIT, penalty);
            totalReward += penalty;
            recordStepReward();
            double[] nextState = buildStateVector(nextStateBuffer);
            brain.trainWithReward(inputs, penalty, nextState, false);
            return;
        }

        // Calculate reward
        double reward = calculateReward();
        totalReward += reward;
        recordStepReward();

        // Prepare nextState and done flag
        double[] nextState = buildStateVector(nextStateBuffer);
        boolean done = isOutOfBoundsState || hasFinished;
        
        // Add experience every step; train at a lower frequency for speed.
        brain.addExperience(inputs, reward, nextState, done);
        if (updateCount % Config.TRAIN_EVERY_N_STEPS == 0 || done) {
            brain.train();
        }
    }

    // Calculate the reward based on the car's state
    private double calculateReward() {
        double reward = 0;
        double deltaX = x - lastX;
        double previousMaxX = maxXReached;
        double newMaxXDelta = 0.0;
        if (x > previousMaxX) {
            newMaxXDelta = x - previousMaxX;
            maxXReached = x;
        }

        // Penalty for using many steps
        reward -= Config.STEP_PENALTY;

        // Calculate movement since last update
        double movementDelta = Math.sqrt(Math.pow(x - lastX, 2) + Math.pow(y - lastY, 2));
        
        // Penalize staying still or moving very little
        if (movementDelta < Config.MIN_MOVEMENT_THRESHOLD) {
            reward += Config.STATIONARY_PENALTY;
            recordRewardEvent(REWARD_EVT_STATIONARY, Config.STATIONARY_PENALTY);
        }
        
        // Reward only true forward progress (new furthest X), not back-and-forth recovery.
        if (newMaxXDelta > 0) {
            double amt = newMaxXDelta * Config.FORWARD_PROGRESS_REWARD;
            reward += amt;
            recordRewardEvent(REWARD_EVT_FORWARD, amt);
        }

        // Extra incentive for expanding explored territory.
        if (newMaxXDelta > 0) {
            double amt = newMaxXDelta * Config.NEW_TERRITORY_REWARD;
            reward += amt;
            recordRewardEvent(REWARD_EVT_NEW_TERRITORY, amt);
        }

        // Penalize backward movement
        if (deltaX < 0) {
            double amt = -Math.abs(deltaX) * Config.FORWARD_PROGRESS_REWARD * Config.BACKWARD_PROGRESS_PENALTY_MULTIPLIER;
            reward += amt;
            recordRewardEvent(REWARD_EVT_BACKWARD, amt);
        }

        // Discourage reckless high-speed behavior near obstacles/boundaries.
        if (speed > Config.HIGH_SPEED_THRESHOLD) {
            double amt = -(speed - Config.HIGH_SPEED_THRESHOLD) * Config.HIGH_SPEED_PENALTY_FACTOR;
            reward += amt;
            recordRewardEvent(REWARD_EVT_SPEED_PENALTY, amt);
        }

        // Penalize getting stuck (too little net forward progress in a window).
        stepsSinceProgressCheck++;
        if (stepsSinceProgressCheck >= Config.STUCK_WINDOW_STEPS) {
            double netProgress = x - progressWindowStartX;
            if (netProgress < Config.STUCK_PROGRESS_THRESHOLD) {
                reward += Config.STUCK_WINDOW_PENALTY;
                recordRewardEvent(REWARD_EVT_STUCK, Config.STUCK_WINDOW_PENALTY);
                if (Config.isInferenceOnly()) {
                    // In viewer/play mode, end the attempt once the policy is clearly stuck.
                    isOutOfBoundsState = true;
                    speed = 0.0;
                }
            }
            progressWindowStartX = x;
            stepsSinceProgressCheck = 0;
        }
        
        // Update last position
        lastX = x;
        lastY = y;

        double progressDelta = newMaxXDelta / (Config.FINISH_LINE_X - startX);
        reward += progressDelta * Config.PROGRESS_MULTIPLIER;

        if (x >= Config.FINISH_LINE_X && !hasFinished) {
            reward += Config.FINISH_REWARD;
            recordRewardEvent(REWARD_EVT_FINISH, Config.FINISH_REWARD);
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
    public double getGrandTotalReward() { return grandTotalReward + totalReward; }
    public void setTotalReward(double totalReward) { this.totalReward = totalReward; }
    public static double getMinX() { return MIN_X; }
    public static double getMinY() { return MIN_Y; }
    public static double getMaxX() { return MAX_X; }
    public static double getMaxY() { return MAX_Y; }

    public NeuralNetwork getBrain() {
        return brain;
    }

    public int getCarIndex() {
        return carIndex;
    }

    public List<Point2D.Double> getTrail() {
        return Collections.unmodifiableList(trail);
    }

    public double[] getSensorDistances() {
        return sensorDistances.clone();
    }

    public double[] getSensorDistancesView() {
        return sensorDistances;
    }

    public double[] getSensorDirection(int sensorIndex) {
        return SENSOR_DIRECTIONS[sensorIndex];
    }

    public double getSensorMaxRange(int sensorIndex) {
        return Config.SENSOR_RANGE * SENSOR_RANGE_SCALE[sensorIndex];
    }

    public int getLastAction() {
        return lastAction;
    }

    public double getSpeed() {
        return speed;
    }

    public double[] getLastQValues() {
        return Arrays.copyOf(lastQValues, lastQValues.length);
    }

    public double[] getLastQValuesView() {
        return lastQValues;
    }

    public double getLastEpisodeReward() {
        return lastEpisodeReward;
    }

    public double getBestEpisodeReward() {
        return bestEpisodeReward == Double.NEGATIVE_INFINITY ? 0.0 : bestEpisodeReward;
    }

    public double getRecentAverageReward() {
        if (recentEpisodeRewards.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double r : recentEpisodeRewards) {
            sum += r;
        }
        return sum / recentEpisodeRewards.size();
    }

    public List<Double> getRecentStepRewards() {
        return new ArrayList<>(recentStepRewards);
    }

    public int[] getRewardEventCounts() {
        return Arrays.copyOf(rewardEventCounts, rewardEventCounts.length);
    }

    public double[] getRewardEventTotals() {
        return Arrays.copyOf(rewardEventTotals, rewardEventTotals.length);
    }

    public static String[] getRewardEventLabels() {
        return Arrays.copyOf(REWARD_EVENT_LABELS, REWARD_EVENT_LABELS.length);
    }

    public void reset(double startX, double startY) {
        if (updateCount > 0 || totalReward != 0.0) {
            grandTotalReward += totalReward;
            lastEpisodeReward = totalReward;
            bestEpisodeReward = Math.max(bestEpisodeReward, totalReward);
            recentEpisodeRewards.addLast(totalReward);
            while (recentEpisodeRewards.size() > RECENT_REWARD_WINDOW) {
                recentEpisodeRewards.removeFirst();
            }
        }
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
        updateCount = 0;
        stepsSinceProgressCheck = 0;
        progressWindowStartX = startX;
        maxXReached = startX;
        trail.clear();
        trail.add(new Point2D.Double(startX, startY));
        recentStepRewards.clear();
        recentStepRewards.addLast(totalReward);
        Arrays.fill(rewardEventCounts, 0);
        Arrays.fill(rewardEventTotals, 0.0);
    }

    public boolean hasFinished() {
        return hasFinished;
    }

    public boolean isTerminated() {
        return hasFinished || isOutOfBoundsState;
    }

    private boolean isInsideObstacle() {
        for (int i = 0; i < Config.OBSTACLE_COUNT; i++) {
            int ox = Config.obstacleX(i);
            int oy = Config.obstacleY(i);
            if (x >= ox
                && x <= ox + Config.OBSTACLE_WIDTH
                && y >= oy
                && y <= oy + Config.OBSTACLE_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    private double[] buildStateVector(double[] state) {
        for (int i = 0; i < Config.SENSOR_RAY_COUNT; i++) {
            double rayRange = Config.SENSOR_RANGE * SENSOR_RANGE_SCALE[i];
            double dist = castRayDistance(SENSOR_DIRECTIONS[i][0], SENSOR_DIRECTIONS[i][1], rayRange);
            sensorDistances[i] = dist;
            state[i] = Math.min(1.0, dist / rayRange);
        }
        state[Config.SENSOR_RAY_COUNT] = (x - MIN_X) / (MAX_X - MIN_X);
        state[Config.SENSOR_RAY_COUNT + 1] = (y - MIN_Y) / (MAX_Y - MIN_Y);
        return state;
    }

    private double castRayDistance(double dirX, double dirY, double rayRange) {
        double mag = Math.sqrt(dirX * dirX + dirY * dirY);
        double ndx = dirX / mag;
        double ndy = dirY / mag;
        for (double d = 1.0; d <= rayRange; d += 2.0) {
            double sx = x + ndx * d;
            double sy = y + ndy * d;
            // Sense top/bottom/left boundaries + obstacles.
            // Right boundary is intentionally ignored so area behind finish is not treated as hazard.
            if (sx < MIN_X || sy < MIN_Y || sy > MAX_Y || pointInsideObstacle(sx, sy)) {
                return d;
            }
        }
        return rayRange;
    }

    private boolean pointInsideObstacle(double px, double py) {
        for (int i = 0; i < Config.OBSTACLE_COUNT; i++) {
            int ox = Config.obstacleX(i);
            int oy = Config.obstacleY(i);
            if (px >= ox && px <= ox + Config.OBSTACLE_WIDTH && py >= oy && py <= oy + Config.OBSTACLE_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    private void recordStepReward() {
        recentStepRewards.addLast(totalReward);
    }

    private void recordRewardEvent(int rewardEvent, double amount) {
        rewardEventCounts[rewardEvent]++;
        rewardEventTotals[rewardEvent] += amount;
    }

    private void resolveObstacleOverlap() {
        for (int i = 0; i < Config.OBSTACLE_COUNT; i++) {
            int ox = Config.obstacleX(i);
            int oy = Config.obstacleY(i);
            int ow = Config.OBSTACLE_WIDTH;
            int oh = Config.OBSTACLE_HEIGHT;
            if (x >= ox && x <= ox + ow && y >= oy && y <= oy + oh) {
                double left = Math.abs(x - ox);
                double right = Math.abs((ox + ow) - x);
                double top = Math.abs(y - oy);
                double bottom = Math.abs((oy + oh) - y);
                double min = Math.min(Math.min(left, right), Math.min(top, bottom));

                if (min == left) {
                    x = ox - 1.0;
                } else if (min == right) {
                    x = ox + ow + 1.0;
                } else if (min == top) {
                    y = oy - 1.0;
                } else {
                    y = oy + oh + 1.0;
                }
                x = Math.max(MIN_X, Math.min(MAX_X, x));
                y = Math.max(MIN_Y, Math.min(MAX_Y, y));
                return;
            }
        }
    }
}
