package com.nncartrack;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

public class Simulation extends JPanel {  // Remove Scrollable interface
    private ArrayList<Car> cars;
    private int episode = 0;
    private int carsFinished = 0;
    private LiveDataWindow liveData;
    private LiveNNStatusWindow nnStatusWindow;
    private double[] recentScores = new double[10]; // Track last 10 episodes
    private int scoreIndex = 0;
    private Logger logger = Logger.getInstance();
    private JProgressBar progressBar;
    private JProgressBar overallProgressBar;
    private final List<RunSnapshot> topRunSnapshots = new ArrayList<>();
    private int nextSnapshotId = 1;
    private double bestModelReward = Double.NEGATIVE_INFINITY;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean modelFinalized = new AtomicBoolean(false);

    public Simulation() {
        setDoubleBuffered(true);  // Add double buffering
        cars = new ArrayList<>();
        // Start cars evenly distributed vertically across the lane.
        for (int i = 0; i < Config.numberOfCars(); i++) {
            cars.add(new Car(Config.STARTING_X, startYForCar(i), i));
        }
        if (!Config.isHeadlessLogsOnly()) {
            liveData = new LiveDataWindow();
            liveData.setVisible(true);  // Explicitly make LiveDataWindow visible
            nnStatusWindow = new LiveNNStatusWindow();
            nnStatusWindow.setVisible(true);
            progressBar = new JProgressBar(0, Config.dynamicStepsPerEpisode());
            progressBar.setStringPainted(true);
            overallProgressBar = new JProgressBar(0, Config.NUMBER_OF_EPISODES);
            overallProgressBar.setStringPainted(true);
            setLayout(new BorderLayout());
            JPanel progressPanel = new JPanel(new GridLayout(2, 1));
            progressPanel.add(progressBar);
            progressPanel.add(overallProgressBar);
            add(progressPanel, BorderLayout.NORTH);
        }
    }

    public void runEpisode() {
        if (stopRequested.get()) {
            return;
        }
        episode++;
        logger.setCurrentEpisode(episode);
        carsFinished = 0;
        int episodeStepBudget = Config.dynamicStepsPerEpisode();
        int carsTerminated = 0;
        if (!Config.isHeadlessLogsOnly()) {
            progressBar.setMaximum(episodeStepBudget);
            progressBar.setValue(0);
            overallProgressBar.setValue(episode);
            overallProgressBar.setString(String.format("Episodes: %d/%d (%.2f%%)", episode, Config.NUMBER_OF_EPISODES, (episode / (double) Config.NUMBER_OF_EPISODES) * 100));
        }
        
        double episodeStartTime = System.currentTimeMillis();
        
        for (int t = 0; t < episodeStepBudget && carsTerminated < cars.size() && !stopRequested.get(); t++) {
            if (!Config.isHeadlessLogsOnly()) {
                double episodeElapsedSeconds = (System.currentTimeMillis() - episodeStartTime) / 1000.0;
                progressBar.setValue(t);
                progressBar.setString(String.format("Steps: %d/%d (%.2f%%)", t, episodeStepBudget, (t / (double) episodeStepBudget) * 100));
                if (nnStatusWindow != null) {
                    nnStatusWindow.updateStatus(
                        episode, t, episodeElapsedSeconds, carsFinished, cars.size(), currentLeader());
                }
            }
            for (int i = 0; i < cars.size(); i++) {
                Car car = cars.get(i);
                if (!car.isTerminated()) {
                    car.update();

                    if (car.isTerminated()) {
                        carsTerminated++;
                    }
                    if (car.hasFinished()) {
                        carsFinished++;
                        logger.logCarFinish(car.getTotalReward());
                    }
                }
            }
            if (!Config.isHeadlessLogsOnly() && t % Config.RENDER_EVERY_N_STEPS == 0) {
                repaint();
            }
            int sleepMs = Config.simulationSleepMs();
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    requestStop();
                    break;
                }
            }
        }

        double episodeDurationSeconds = (System.currentTimeMillis() - episodeStartTime) / 1000.0;

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
            episodeDurationSeconds,
            carsFinished,
            cars.size(),
            averageReward,
            bestReward,
            worstReward,
            cars.get(0).getBrain().getEpsilon(),
            cars.get(0).getBrain().getMaxQValue(),
            cars.get(0).getBrain().getCurrentLoss()
        );
        
        // Update live data window
        if (!Config.isHeadlessLogsOnly()) {
            liveData.updateData(
                episode,
                cars.get(0).getBrain().getEpsilon(),
                cars.get(0).getBrain().getMaxQValue(),
                cars.get(0).getBrain().getCurrentLoss(),
                avgRecentScore,
                carsFinished,
                cars.size(),
                episodeDurationSeconds
            );
            if (nnStatusWindow != null) {
                nnStatusWindow.updateStatus(
                    episode,
                    episodeStepBudget,
                    episodeDurationSeconds,
                    carsFinished,
                    cars.size(),
                    currentLeader()
                );
            }
        }

        // Episode-level training boost: more finishers => more replay updates.
        double finishRatio = cars.isEmpty() ? 0.0 : (carsFinished / (double) cars.size());
        int extraBoostBatches = 0;
        if (finishRatio >= Config.SUCCESS_BOOST_MIN_FINISH_RATIO) {
            double normalized = (finishRatio - Config.SUCCESS_BOOST_MIN_FINISH_RATIO)
                / Math.max(1e-6, 1.0 - Config.SUCCESS_BOOST_MIN_FINISH_RATIO);
            normalized = Math.max(0.0, Math.min(1.0, normalized));
            extraBoostBatches = (int) Math.round(normalized * Config.SUCCESS_BOOST_MAX_EXTRA_BATCHES);
        }
        if (extraBoostBatches > 0) {
            for (Car car : cars) {
                car.getBrain().trainMultiple(extraBoostBatches);
            }
        }

        if (!Config.isInferenceOnly()) {
            Car bestCar = Collections.max(cars, Comparator.comparingDouble(Car::getTotalReward));
            if (bestCar.getTotalReward() > bestModelReward) {
                if (bestCar.getBrain().saveModel(Config.MODEL_SAVE_FILE_PATH)) {
                    bestModelReward = bestCar.getTotalReward();
                }
            }
        }
        
        updateTopRunSnapshots();

        // After episode ends, call onEpisodeEnd for each car's brain
        for (Car car : cars) {
            car.getBrain().onEpisodeEnd();
        }
        
        // Reset all cars to same starting position
        for (int i = 0; i < cars.size(); i++) {
            cars.get(i).reset(Config.STARTING_X, startYForCar(i));
        }
    }

    public void requestStop() {
        if (stopRequested.compareAndSet(false, true)) {
            System.out.println("Stop requested. Finalizing current run...");
        }
    }

    private double startYForCar(int carIndex) {
        double minY = Config.TRACK_MARGIN + 20;
        double maxY = Config.TRACK_MARGIN + Config.TRACK_HEIGHT - 20;
        if (Config.numberOfCars() <= 1) {
            return (minY + maxY) / 2.0;
        }
        double spacing = (maxY - minY) / (Config.numberOfCars() - 1);
        return minY + spacing * carIndex;
    }

    private Car currentLeader() {
        if (cars == null || cars.isEmpty()) {
            return null;
        }
        return Collections.max(cars, Comparator.comparingDouble(Car::getTotalReward));
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
        g.setColor(Color.BLACK);
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
        g.drawLine((int) Config.FINISH_LINE_X, (int) Config.TRACK_MARGIN, (int) Config.FINISH_LINE_X, (int) Config.TRACK_MARGIN + Config.TRACK_HEIGHT);

        renderObstacle(g2d);

        // Draw trail history under car sprites.
        renderTrails(g2d);
        renderSensors(g2d);

        // Draw cars
        renderCars(g2d);
        renderTopSnapshotOverlay(g2d);
        renderScoreDashboard(g2d);
    }

    private void renderTrails(Graphics2D g2d) {
        if (cars.isEmpty()) {
            return;
        }
        Stroke previousStroke = g2d.getStroke();
        Car leader = Collections.max(cars, Comparator.comparingDouble(Car::getTotalReward));
        java.util.List<Point2D.Double> trail = leader.getTrail();
        if (trail.size() >= 2) {
            float hue = (leader.getCarIndex() % Math.max(1, Config.numberOfCars())) / (float) Math.max(1, Config.numberOfCars());
            Color base = Color.getHSBColor(hue, 0.9f, 1.0f);
            g2d.setStroke(new BasicStroke(0.8f));
            for (int i = 1; i < trail.size(); i++) {
                float age = i / (float) (trail.size() - 1);
                int alpha = 1 + (int) (age * 99.0f); // Fade from 1..100 over the full trail
                g2d.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                Point2D.Double p0 = trail.get(i - 1);
                Point2D.Double p1 = trail.get(i);
                g2d.drawLine((int) p0.x, (int) p0.y, (int) p1.x, (int) p1.y);
            }
        }
        g2d.setStroke(previousStroke);
    }
    
    private void renderCars(Graphics2D g2d) {
        for (Car car : cars) {
            double x = car.getX();
            double y = car.getY();
            float hue = (car.getCarIndex() % Math.max(1, Config.numberOfCars())) / (float) Math.max(1, Config.numberOfCars());
            g2d.setColor(Color.getHSBColor(hue, 0.9f, 1.0f));
            g2d.fillOval((int)x - 8, (int)y - 8, 16, 16);
        }
    }

    private void renderTopSnapshotOverlay(Graphics2D g2d) {
        if (topRunSnapshots.isEmpty()) {
            return;
        }
        RunSnapshot best = topRunSnapshots.get(0);
        Stroke prev = g2d.getStroke();
        g2d.setStroke(new BasicStroke(1.2f));
        List<Point2D.Double> trail = best.getTrail();
        for (int i = 1; i < trail.size(); i++) {
            Point2D.Double p0 = trail.get(i - 1);
            Point2D.Double p1 = trail.get(i);
            g2d.setColor(new Color(255, 255, 255, 150));
            g2d.drawLine((int) p0.x, (int) p0.y, (int) p1.x, (int) p1.y);
        }
        if (!trail.isEmpty()) {
            Point2D.Double tailEnd = trail.get(trail.size() - 1);
            int bx = (int) tailEnd.x + 10;
            int by = (int) tailEnd.y - 18;
            int boxSize = 14;
            g2d.setColor(new Color(255, 255, 255, 240));
            g2d.fillRect(bx, by, boxSize, boxSize);
            g2d.setColor(Color.BLACK);
            g2d.setFont(g2d.getFont().deriveFont(Font.BOLD, 10f));
            g2d.drawString("1", bx + 4, by + 11);
        }
        g2d.setStroke(prev);
    }

    private void updateTopRunSnapshots() {
        for (Car car : cars) {
            RunSnapshot snapshot = new RunSnapshot(
                nextSnapshotId++,
                episode,
                car.getCarIndex(),
                car.getTotalReward(),
                car.getTrail()
            );
            topRunSnapshots.add(snapshot);
        }
        topRunSnapshots.sort(Comparator.comparingDouble(RunSnapshot::getScore).reversed());
        if (topRunSnapshots.size() > 15) {
            topRunSnapshots.subList(15, topRunSnapshots.size()).clear();
        }
    }

    private void renderScoreDashboard(Graphics2D g2d) {
        if (cars.isEmpty()) {
            return;
        }
        Font oldFont = g2d.getFont();
        int boardTop = (int) (Config.TRACK_MARGIN + Config.TRACK_HEIGHT + 8);
        int boardHeight = Math.max(38, getHeight() - boardTop - 8);
        ArrayList<Car> sortedCars = new ArrayList<>(cars);
        sortedCars.sort(Comparator.comparingDouble(Car::getTotalReward).reversed());
        int slotWidth = Math.max(80, (getWidth() - 16) / sortedCars.size());
        int[] globalCountMax = new int[Car.REWARD_EVT_COUNT];
        for (Car c : sortedCars) {
            int[] cc = c.getRewardEventCounts();
            for (int k = 0; k < Math.min(globalCountMax.length, cc.length); k++) {
                globalCountMax[k] = Math.max(globalCountMax[k], cc[k]);
            }
        }
        g2d.setColor(new Color(235, 235, 235, 200));
        g2d.drawString("Bars: FWD NEW BACK IDLE FAST STUCK WALL OBS GOAL", 8, Math.max(12, boardTop - 2));
        for (int i = 0; i < sortedCars.size(); i++) {
            Car car = sortedCars.get(i);
            int x = 8 + i * slotWidth;
            int w = slotWidth - 6;
            int y = boardTop;
            int h = boardHeight;

            g2d.setColor(new Color(0, 0, 0, 165));
            g2d.fillRoundRect(x, y, w, h, 8, 8);
            float hue = (car.getCarIndex() % Math.max(1, Config.numberOfCars())) / (float) Math.max(1, Config.numberOfCars());
            Color c = Color.getHSBColor(hue, 0.9f, 1.0f);
            g2d.setColor(c);
            g2d.fillOval(x + 6, y + 4, 8, 8);
            g2d.setColor(new Color(245, 245, 245, 230));
            g2d.drawString(String.format("C%d %.0f", car.getCarIndex(), car.getTotalReward()), x + 18, y + 12);

            // Connector from score board to car only for top 3.
            if (i < 3) {
                int bx = x + w / 2;
                int by = y;
                int cx = (int) car.getX();
                int cy = (int) car.getY();
                Stroke prev = g2d.getStroke();
                // White glow underlay for clear separation from sensor rays.
                g2d.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.setColor(new Color(255, 255, 255, 120));
                g2d.drawLine(bx, by, cx, cy);
                // Distinct dashed cyan connector style (non-sensor).
                g2d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 6f}, 0f));
                g2d.setColor(new Color(90, 235, 255, 220));
                g2d.drawLine(bx, by, cx, cy);
                g2d.setStroke(prev);
                // Anchor markers.
                g2d.setColor(new Color(90, 235, 255, 220));
                g2d.fillOval(bx - 3, by - 3, 6, 6);
                g2d.fillOval(cx - 3, cy - 3, 6, 6);
            }

            int gx = x + 4;
            int gy = y + 16;
            int gw = w - 8;
            int gh = Math.max(12, h - 20);
            g2d.setColor(new Color(255, 255, 255, 55));
            g2d.drawRect(gx, gy, gw, gh);

            int[] counts = car.getRewardEventCounts();
            String[] labels = Car.getRewardEventLabels();
            int barCount = Math.min(labels.length, counts.length);
            int barGap = 2;
            int barW = Math.max(2, (gw - (barCount + 1) * barGap) / Math.max(1, barCount));
            int baseY = gy + gh - 2;
            for (int j = 0; j < barCount; j++) {
                int bx = gx + barGap + j * (barW + barGap);
                int denom = Math.max(1, globalCountMax[j]);
                int bh = (int) Math.round((gh - 8) * (counts[j] / (double) denom));
                bh = Math.max(1, bh);
                Color barColor = new Color(100, 220, 120, 210);
                if (j >= Car.REWARD_EVT_BACKWARD && j <= Car.REWARD_EVT_OBSTACLE_HIT) {
                    barColor = new Color(255, 120, 80, 220);
                }
                if (j == Car.REWARD_EVT_FINISH) {
                    barColor = new Color(255, 220, 80, 230);
                }
                g2d.setColor(barColor);
                g2d.fillRect(bx, baseY - bh, barW, bh);
            }
        }
        g2d.setFont(oldFont);
    }

    private void renderSensors(Graphics2D g2d) {
        Stroke previousStroke = g2d.getStroke();
        for (Car car : cars) {
            double[] distances = car.getSensorDistancesView();
            for (int i = 0; i < Config.SENSOR_RAY_COUNT; i++) {
                double[] dir = car.getSensorDirection(i);
                double mag = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
                double ndx = dir[0] / mag;
                double ndy = dir[1] / mag;
                double sensed = distances[i];
                double sensorMax = car.getSensorMaxRange(i);
                boolean hitSomething = sensed < sensorMax - 0.5;

                int x0 = (int) car.getX();
                int y0 = (int) car.getY();
                int x1 = (int) (car.getX() + ndx * sensed);
                int y1 = (int) (car.getY() + ndy * sensed);

                if (!hitSomething) {
                    continue;
                }

                float proximity = (float) Math.max(0.0, Math.min(1.0, 1.0 - (sensed / sensorMax)));
                float thickness = 0.7f + (proximity * 3.3f); // thin when far, thick when close
                int alpha = 35 + (int) (220 * proximity);    // true opacity scaling
                g2d.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.setColor(new Color(255, 40, 40, alpha));
                g2d.drawLine(x0, y0, x1, y1);

                g2d.setColor(new Color(255, 80, 80, Math.min(255, alpha + 20)));
                g2d.fillOval(x1 - 2, y1 - 2, 4, 4);
            }
        }
        g2d.setStroke(previousStroke);
    }

    private void renderObstacle(Graphics2D g2d) {
        int w = Config.OBSTACLE_WIDTH;
        int h = Config.OBSTACLE_HEIGHT;
        Font oldFont = g2d.getFont();
        g2d.setFont(oldFont.deriveFont(Font.BOLD, 30f));
        for (int i = 0; i < Config.OBSTACLE_COUNT; i++) {
            int x = Config.obstacleX(i);
            int y = Config.obstacleY(i);
            g2d.setColor(new Color(80, 45, 25));
            g2d.fillRoundRect(x, y, w, h, 12, 12);
            g2d.setColor(new Color(245, 210, 120));
            g2d.setStroke(new BasicStroke(3f));
            g2d.drawRoundRect(x, y, w, h, 12, 12);
            g2d.drawString("\uD83D\uDCA9", x + w / 2 - 15, y + h / 2 + 12);
        }
        g2d.setFont(oldFont);
    }

    // Remove all Scrollable interface methods
    
    public static void main(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("logs".equalsIgnoreCase(arg)) {
                    System.setProperty("nn.headless", "true");
                    System.setProperty("java.awt.headless", "true");
                } else if ("headless".equalsIgnoreCase(arg)) {
                    System.setProperty("nn.headless", "true");
                    System.setProperty("java.awt.headless", "true");
                    System.setProperty("nn.filelogs", "false");
                } else if ("play".equalsIgnoreCase(arg)) {
                    System.setProperty("nn.mode", "play");
                }
            }
        }

        long runStartNanos = System.nanoTime();
        Simulation sim = new Simulation();
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            sim.finalizeModelOutput((System.nanoTime() - runStartNanos) / 1_000_000_000.0)
        ));
        if (Config.isHeadlessLogsOnly()) {
            for (int i = 0; i < Config.NUMBER_OF_EPISODES && !sim.stopRequested.get(); i++) {
                sim.runEpisode();
            }
            sim.finalizeModelOutput((System.nanoTime() - runStartNanos) / 1_000_000_000.0);
            return;
        }

        JFrame frame = new JFrame("Car Racing Simulation with Neural Networks");
        frame.add(sim);  // Direct add, no scroll pane
        frame.setSize(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                sim.requestStop();
                sim.finalizeModelOutput((System.nanoTime() - runStartNanos) / 1_000_000_000.0);
                frame.dispose();
                System.exit(0);
            }
        });
        JRootPane rootPane = frame.getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("ESCAPE"), "stopAndExit");
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke('Q'), "stopAndExit");
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke('X'), "stopAndExit");
        rootPane.getActionMap().put("stopAndExit", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sim.requestStop();
                sim.finalizeModelOutput((System.nanoTime() - runStartNanos) / 1_000_000_000.0);
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setVisible(true);

        // Run multiple episodes using config value
        new Thread(() -> {
            for (int i = 0; i < Config.NUMBER_OF_EPISODES && !sim.stopRequested.get(); i++) {
                sim.runEpisode();
            }
            sim.finalizeModelOutput((System.nanoTime() - runStartNanos) / 1_000_000_000.0);
            if (sim.stopRequested.get()) {
                SwingUtilities.invokeLater(frame::dispose);
            }
        }).start();
    }

    private void finalizeModelOutput(double totalRuntimeSeconds) {
        if (!modelFinalized.compareAndSet(false, true)) {
            return;
        }
        if (Config.isInferenceOnly()) {
            return;
        }
        Path source = Path.of(Config.MODEL_SAVE_FILE_PATH);
        if (!Files.exists(source)) {
            return;
        }

        Path target = Path.of(Config.completedModelFilePath(episode, totalRuntimeSeconds));
        if (source.equals(target)) {
            return;
        }

        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Final model file: " + target);
        } catch (IOException e) {
            System.err.println("Failed to finalize model output name: " + e.getMessage());
        }
    }
}
