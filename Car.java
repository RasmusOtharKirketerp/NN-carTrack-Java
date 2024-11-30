import java.awt.Color;
import java.awt.Graphics;
import java.util.Random;

public class Car {
    // Car properties
    private double x, y;
    private double speed;
    private double angle;
    private double turn; // Declare turn as a field

    // Remove unused history tracking
    // private static final int HISTORY_SIZE = 20;
    // private double[] positionHistoryX = new double[HISTORY_SIZE];
    private static final double TURN_SENSITIVITY = 2.0;
    // private int historyIndex = 0;

    // Sensor range
    private double sensorRange = 150;

    private NeuralNetwork brain;

    // Random instance
    private static Random rand = new Random();

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
        // Randomize initial angle between 0 and 360 degrees
        this.angle = rand.nextDouble() * 360;
        // Randomize initial speed between 0 and 2 units
        this.speed = rand.nextDouble() * 2;
        brain = new NeuralNetwork();
    }

    // Update the car's position based on neural network's output
    public void update(double obstacleDistance) {
        // Don't update if already out of bounds
        if (isOutOfBoundsState) {
            return;
        }

        // Remove history tracking
        // positionHistoryX[historyIndex] = x;
        // historyIndex = (historyIndex + 1) % HISTORY_SIZE;

        // Normalize inputs
        double normalizedSpeed = speed / 10.0;
        double normalizedAngle = angle / 360.0;
        double normalizedDistance = obstacleDistance / sensorRange;
        double normalizedX = (x - MIN_X) / (MAX_X - MIN_X);
        double normalizedY = (y - MIN_Y) / (MAX_Y - MIN_Y);

        // Inputs: [Speed, Angle, Obstacle Distance, X Position, Y Position]
        double[] inputs = { normalizedSpeed, normalizedAngle, normalizedDistance, normalizedX, normalizedY };
        double[] outputs = brain.forward(inputs);

        // Outputs: [Acceleration, Turn]
        double acceleration = outputs[0] * 2 - 1; // Range [-1, 1]
        double turn = outputs[1] * 2 - 1;         // Range [-1, 1]

        // Modify how turn and speed are applied
        speed += acceleration * 0.1; // Reduced from 0.2
        this.turn = turn;  // Add this line to use the class field
        angle += this.turn * TURN_SENSITIVITY;

        // Limit speed
        if (speed < 0) speed = 0;
        if (speed > 10) speed = 10;

        // Keep angle between 0 and 360 degrees
        angle = angle % 360;
        if (angle < 0) {
            angle += 360;
        }

        // Update position
        double newX = x + Math.cos(Math.toRadians(angle)) * speed;
        double newY = y + Math.sin(Math.toRadians(angle)) * speed;

        // Check boundaries before updating position
        if (newX < MIN_X || newX > MAX_X || newY < MIN_Y || newY > MAX_Y) {
            isOutOfBoundsState = true;
            speed = 0;
            // Apply heavy penalty
            double penalty = -10.0;
            totalReward += penalty;
            brain.trainWithReward(inputs, penalty);
            return;
        }

        // Update position only if within bounds
        x = newX;
        y = newY;

        // Calculate reward
        double reward = calculateReward(obstacleDistance);
        totalReward += reward;

        // Train the neural network using the reward
        brain.trainWithReward(inputs, reward);
    }

    // Simulate sensor (distance to obstacle)
    public double senseObstacle(Obstacle obstacle) {
        double dx = obstacle.getX() - x;
        double dy = obstacle.getY() - y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance < sensorRange ? distance : sensorRange;
    }

    // Check if the car is out-of-bounds
    private boolean isOutOfBounds() {
        return x < MIN_X || x > MAX_X || y < MIN_Y || y > MAX_Y;
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
            System.out.println("Car reached finish line! Final reward: " + reward);
        }

        if (obstacleDistance < 20) {
            reward += Config.OBSTACLE_CLOSE_PENALTY;
        } else if (obstacleDistance < 50) {
            reward += Config.OBSTACLE_NEAR_PENALTY;
        }

        if (Math.abs(turn) > 0.8) {
            reward += Config.SHARP_TURN_PENALTY;
        }

        if (isOutOfBounds()) {
            reward += Config.OUT_OF_BOUNDS_PENALTY;
        }

        return reward;
    }

    // Render the car
    public void draw(Graphics g) {
        // Set color based on finish state or out of bounds
        g.setColor((hasFinished || isOutOfBoundsState) ? Color.RED : Color.BLUE);
        g.fillOval((int) x - 5, (int) y - 5, 10, 10);

        // Draw direction line
        int lineX = (int) (x + Math.cos(Math.toRadians(angle)) * 20);
        int lineY = (int) (y + Math.sin(Math.toRadians(angle)) * 20);
        g.drawLine((int) x, (int) y, lineX, lineY);

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
        speed = rand.nextDouble() * 2;
        angle = rand.nextDouble() * 360;
        isOutOfBoundsState = false;
        // Remove unused historyIndex reset
        // historyIndex = 0;
        this.startX = startX;
        hasFinished = false;
        totalReward = 0; // Make sure reward is reset properly
        lastX = startX;
        lastY = startY;
    }

    public boolean hasFinished() {
        return hasFinished;
    }
}
