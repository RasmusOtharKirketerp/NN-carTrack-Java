package com.nncartrack;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class Simulation extends JPanel {  // Remove Scrollable interface
    private ArrayList<Car> cars;
    private int episode = 0;
    private int carsFinished = 0;
    private LiveDataWindow liveData;
    private double[] recentScores = new double[10]; // Track last 10 episodes
    private int scoreIndex = 0;
    private Logger logger = Logger.getInstance();
    private JProgressBar progressBar;
    private JProgressBar overallProgressBar;

    public Simulation() {
        setDoubleBuffered(true);  // Add double buffering
        cars = new ArrayList<>();
        // All cars start at the same position
        for (int i = 0; i < Config.NUMBER_OF_CARS; i++) {
            cars.add(new Car(Config.STARTING_X, Config.STARTING_Y, i));
        }
        liveData = new LiveDataWindow();
        liveData.setVisible(true);  // Explicitly make LiveDataWindow visible
        progressBar = new JProgressBar(0, Config.STEPS_PER_EPISODE);
        progressBar.setStringPainted(true);
        overallProgressBar = new JProgressBar(0, Config.NUMBER_OF_EPISODES);
        overallProgressBar.setStringPainted(true);
        setLayout(new BorderLayout());
        JPanel progressPanel = new JPanel(new GridLayout(2, 1));
        progressPanel.add(progressBar);
        progressPanel.add(overallProgressBar);
        add(progressPanel, BorderLayout.NORTH);
    }

    public void runEpisode() {
        episode++;
        carsFinished = 0;
        progressBar.setValue(0);
        overallProgressBar.setValue(episode);
        overallProgressBar.setString(String.format("Episodes: %d/%d (%.2f%%)", episode, Config.NUMBER_OF_EPISODES, (episode / (double) Config.NUMBER_OF_EPISODES) * 100));
        
        double episodeStartTime = System.currentTimeMillis();
        
        for (int t = 0; t < Config.STEPS_PER_EPISODE && carsFinished < cars.size(); t++) {
            progressBar.setValue(t);
            progressBar.setString(String.format("Steps: %d/%d (%.2f%%)", t, Config.STEPS_PER_EPISODE, (t / (double) Config.STEPS_PER_EPISODE) * 100));
            for (int i = 0; i < cars.size(); i++) {
                Car car = cars.get(i);
                if (!car.hasFinished()) {
                    car.update(0); // Pass 0 as obstacle distance


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
    
        // Draw road (same as track)
        g.setColor(Color.DARK_GRAY);
        int roadX = (int) Config.TRACK_MARGIN;
        int roadY = (int) Config.TRACK_MARGIN;
        int roadWidth = Config.TRACK_WIDTH;
        int roadHeight = Config.TRACK_HEIGHT;
        g.fillRect(roadX, roadY, roadWidth, roadHeight);

        // Draw white stripes on the road
        g.setColor(Color.WHITE);
        int stripeWidth = 20;
        int stripeHeight = 10;
        int stripeSpacing = 40;
        for (int x = roadX; x < roadX + roadWidth; x += stripeWidth + stripeSpacing) {
            g.fillRect(x, roadY + (roadHeight - stripeHeight) / 2, stripeWidth, stripeHeight);
        }
        
        // Draw track boundaries
        g.setColor(Color.DARK_GRAY);
        g.drawRect(roadX, roadY, roadWidth, roadHeight);

        // Draw finish line
        g.setColor(Color.GREEN);
        g.drawLine((int) Config.TRACK_MARGIN + Config.TRACK_WIDTH - 20, (int) Config.TRACK_MARGIN, (int) Config.TRACK_MARGIN + Config.TRACK_WIDTH - 20, (int) Config.TRACK_MARGIN + Config.TRACK_HEIGHT);
    
        // Draw cars
        for (Car car : cars) {
            car.draw(g, cars);
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
