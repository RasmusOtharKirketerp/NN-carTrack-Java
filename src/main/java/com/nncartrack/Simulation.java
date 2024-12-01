package com.nncartrack;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class Simulation extends JPanel {  // Remove Scrollable interface
    private ArrayList<Car> cars;
    private Obstacle obstacle;
    private int episode = 0;
    private int carsFinished = 0;
    private LiveDataWindow liveData;
    private double[] recentScores = new double[10]; // Track last 10 episodes
    private int scoreIndex = 0;
    private Logger logger = Logger.getInstance();

    public Simulation() {
        setDoubleBuffered(true);  // Add double buffering
        cars = new ArrayList<>();
        // All cars start at the same position
        for (int i = 0; i < Config.NUMBER_OF_CARS; i++) {
            cars.add(new Car(Config.STARTING_X, Config.STARTING_Y, i));
        }
        obstacle = new Obstacle(Config.WINDOW_WIDTH / 2, Config.WINDOW_HEIGHT / 2);
        liveData = new LiveDataWindow();
        liveData.setVisible(true);  // Explicitly make LiveDataWindow visible
    }

    public void runEpisode() {
        episode++;
        carsFinished = 0;
        
        double episodeStartTime = System.currentTimeMillis();
        
        // Reset rewards at start of episode
        for (Car car : cars) {
            car.setTotalReward(0);
        }
        
        for (int t = 0; t < Config.STEPS_PER_EPISODE && carsFinished < cars.size(); t++) {
            for (int i = 0; i < cars.size(); i++) {
                Car car = cars.get(i);
                if (!car.hasFinished()) {
                    double distance = car.senseObstacle(obstacle);
                    car.update(distance);


                    if (car.hasFinished()) {
                        carsFinished++;
                        logger.logCarFinish(car.getTotalReward());
                    }
                }
            }
            repaint();
            try { 
                Thread.sleep(Config.SIMULATION_SLEEP_MS); 
            } catch (InterruptedException e) {}
        }

        // Calculate and display episode statistics
        double totalReward = 0;
        double bestReward = Double.NEGATIVE_INFINITY;
        double worstReward = Double.POSITIVE_INFINITY;
        
        for (Car car : cars) {
            double carReward = car.getTotalReward();
            totalReward += carReward;
            bestReward = Math.max(bestReward, carReward);
            worstReward = Math.min(worstReward, carReward);
        }
        
        double averageReward = totalReward / cars.size();
        System.out.println("Average Reward: " + String.format("%.2f", averageReward));
        System.out.println("Best Reward: " + String.format("%.2f", bestReward));
        System.out.println("Worst Reward: " + String.format("%.2f", worstReward));
        System.out.println("Epsilon: " + String.format("%.4f", cars.get(0).getBrain().getEpsilon()));
        System.out.println("================================\n");
        
        // Calculate average score from recent episodes
        recentScores[scoreIndex] = averageReward;
        scoreIndex = (scoreIndex + 1) % recentScores.length;
        double avgRecentScore = 0;
        for (double score : recentScores) {
            avgRecentScore += score;
        }
        avgRecentScore /= recentScores.length;
        
        // Calculate episode statistics and log them
        logger.logEpisodeStats(
            episode,
            (System.currentTimeMillis() - episodeStartTime) / 1000.0,
            carsFinished,
            cars.size(),
            averageReward,
            bestReward,
            worstReward,
            cars.get(0).getBrain().getEpsilon()
        );
        
        // Update live data window
        liveData.updateData(
            episode,
            cars.get(0).getBrain().getEpsilon(),
            cars.get(0).getBrain().getMaxQValue(),
            cars.get(0).getBrain().getCurrentLoss(),
            avgRecentScore
        );
        
        // After episode ends, call onEpisodeEnd for each car's brain
        for (Car car : cars) {
            car.getBrain().onEpisodeEnd();
        }
        
        // Reset all cars to same starting position
        for (int i = 0; i < cars.size(); i++) {
            cars.get(i).reset(Config.STARTING_X, Config.STARTING_Y);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Create buffer for smoother rendering
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw background
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, getWidth(), getHeight());
    
        // Draw track boundaries
        g.setColor(Color.DARK_GRAY);
        g.drawRect((int) Car.getMinX(), (int) Car.getMinY(), (int) (Car.getMaxX() - Car.getMinX()), (int) (Car.getMaxY() - Car.getMinY()));
    
        // Draw obstacle
        obstacle.draw(g);
    
        // Draw cars
        for (Car car : cars) {
            car.draw(g);
        }
    }
    
    // Remove all Scrollable interface methods
    
    public static void main(String[] args) {
        JFrame frame = new JFrame("Car Racing Simulation with Neural Networks");
        Simulation sim = new Simulation();
        frame.add(sim);  // Direct add, no scroll pane
        frame.setSize(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        // Run multiple episodes using config value
        new Thread(() -> {
            for (int i = 0; i < Config.NUMBER_OF_EPISODES; i++) {
                sim.runEpisode();
            }
        }).start();
    }
}
