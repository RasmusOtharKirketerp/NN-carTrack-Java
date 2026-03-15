package com.nncartrack;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.awt.RenderingHints;

public class LiveNNStatusWindow extends JFrame {
    private final JTextArea runtimeArea;
    private final NetworkPanel networkPanel;
    private final Queue<NNStatusSnapshot> updateQueue;
    private final Timer updateTimer;

    private static class NNStatusSnapshot {
        final int episode;
        final int step;
        final int carsFinished;
        final int carsTotal;
        final int replaySize;
        final double replayBeta;
        final double replayFillRatio;
        final double epsilon;
        final double loss;
        final double maxQ;
        final int trainBatchCounter;
        final int carIndex;
        final double carX;
        final double carY;
        final double carSpeed;
        final double carReward;
        final int lastAction;
        final double[] sensors;
        final double[] inputs;
        final double[] hidden;
        final double[] qValues;
        final double[][] weightsInputHidden;
        final double[][] weightsHiddenOutput;

        NNStatusSnapshot(
            int episode,
            int step,
            int carsFinished,
            int carsTotal,
            int replaySize,
            double replayBeta,
            double replayFillRatio,
            double epsilon,
            double loss,
            double maxQ,
            int trainBatchCounter,
            int carIndex,
            double carX,
            double carY,
            double carSpeed,
            double carReward,
            int lastAction,
            double[] sensors,
            double[] inputs,
            double[] hidden,
            double[] qValues,
            double[][] weightsInputHidden,
            double[][] weightsHiddenOutput
        ) {
            this.episode = episode;
            this.step = step;
            this.carsFinished = carsFinished;
            this.carsTotal = carsTotal;
            this.replaySize = replaySize;
            this.replayBeta = replayBeta;
            this.replayFillRatio = replayFillRatio;
            this.epsilon = epsilon;
            this.loss = loss;
            this.maxQ = maxQ;
            this.trainBatchCounter = trainBatchCounter;
            this.carIndex = carIndex;
            this.carX = carX;
            this.carY = carY;
            this.carSpeed = carSpeed;
            this.carReward = carReward;
            this.lastAction = lastAction;
            this.sensors = sensors;
            this.inputs = inputs;
            this.hidden = hidden;
            this.qValues = qValues;
            this.weightsInputHidden = weightsInputHidden;
            this.weightsHiddenOutput = weightsHiddenOutput;
        }
    }

    private static class NetworkPanel extends JPanel {
        private NNStatusSnapshot snapshot;

        NetworkPanel() {
            setBackground(new Color(14, 22, 40));
        }

        void setSnapshot(NNStatusSnapshot snapshot) {
            this.snapshot = snapshot;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            g2d.setPaint(new GradientPaint(0, 0, new Color(18, 28, 50), 0, height, new Color(8, 12, 24)));
            g2d.fillRect(0, 0, width, height);

            if (snapshot == null) {
                g2d.setColor(new Color(210, 225, 255));
                g2d.setFont(new Font("Monospaced", Font.PLAIN, 16));
                g2d.drawString("Waiting for live NN data...", 24, 36);
                return;
            }

            double[][] layers = { snapshot.inputs, snapshot.hidden, snapshot.qValues };
            String[] labels = { "INPUT", "HIDDEN", "OUTPUT" };
            int[] xs = { width / 6, width / 2, (width * 5) / 6 };
            Point[][] points = new Point[layers.length][];

            for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
                points[layerIndex] = buildNodePositions(xs[layerIndex], height, layers[layerIndex].length);
            }

            if (snapshot.weightsInputHidden != null) {
                drawConnections(g2d, points[0], points[1], snapshot.weightsInputHidden);
            }
            if (snapshot.weightsHiddenOutput != null) {
                drawConnections(g2d, points[1], points[2], snapshot.weightsHiddenOutput);
            }

            for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
                g2d.setColor(new Color(98, 245, 255));
                g2d.setFont(new Font("Monospaced", Font.BOLD, 15));
                g2d.drawString(labels[layerIndex], xs[layerIndex] - 30, 24);
                drawNodes(g2d, points[layerIndex], layers[layerIndex], layerIndex == 2);
            }
        }

        private Point[] buildNodePositions(int x, int height, int count) {
            Point[] positions = new Point[count];
            int top = 46;
            int bottom = height - 22;
            if (count == 1) {
                positions[0] = new Point(x, (top + bottom) / 2);
                return positions;
            }
            double spacing = (bottom - top) / (double) (count - 1);
            for (int i = 0; i < count; i++) {
                positions[i] = new Point(x, (int) Math.round(top + i * spacing));
            }
            return positions;
        }

        private void drawConnections(Graphics2D g2d, Point[] from, Point[] to, double[][] weights) {
            double maxAbs = 1e-9;
            for (double[] row : weights) {
                for (double weight : row) {
                    maxAbs = Math.max(maxAbs, Math.abs(weight));
                }
            }
            for (int i = 0; i < from.length && i < weights.length; i++) {
                for (int j = 0; j < to.length && j < weights[i].length; j++) {
                    double normalized = Math.min(1.0, Math.abs(weights[i][j]) / maxAbs);
                    int alpha = 20 + (int) (120 * normalized);
                    g2d.setColor(weights[i][j] >= 0
                        ? new Color(57, 255, 20, alpha)
                        : new Color(255, 77, 109, alpha));
                    g2d.setStroke(new BasicStroke((float) (0.4 + normalized * 1.8)));
                    g2d.drawLine(from[i].x, from[i].y, to[j].x, to[j].y);
                }
            }
        }

        private void drawNodes(Graphics2D g2d, Point[] points, double[] values, boolean outputLayer) {
            double maxAbs = 1e-9;
            for (double value : values) {
                maxAbs = Math.max(maxAbs, Math.abs(value));
            }
            for (int i = 0; i < points.length; i++) {
                Point p = points[i];
                double value = values[i];
                double normalized = Math.min(1.0, Math.abs(value) / maxAbs);
                int radius = outputLayer ? 16 : (points.length > 24 ? 8 : 11);
                Color fill = value >= 0
                    ? new Color(0, 224, 255, 110 + (int) (145 * normalized))
                    : new Color(255, 196, 0, 110 + (int) (145 * normalized));
                g2d.setColor(fill);
                g2d.fillOval(p.x - radius, p.y - radius, radius * 2, radius * 2);
                g2d.setColor(new Color(230, 240, 255));
                g2d.drawOval(p.x - radius, p.y - radius, radius * 2, radius * 2);

                if (points.length <= 12 || outputLayer) {
                    g2d.setFont(new Font("Monospaced", Font.PLAIN, 10));
                    g2d.drawString(String.format(Locale.US, "%d", i), p.x - radius - 16, p.y + 4);
                    g2d.drawString(String.format(Locale.US, "%.2f", value), p.x + radius + 4, p.y + 4);
                }
            }
        }
    }

    public LiveNNStatusWindow() {
        setTitle("NN Config + Runtime");
        setSize(780, 620);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(8, 12, 24));

        JPanel content = new JPanel(new GridLayout(1, 2, 10, 10));
        content.setBackground(new Color(8, 12, 24));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextArea configArea = buildArea();
        configArea.setText(buildConfigText());
        runtimeArea = buildArea();
        runtimeArea.setText("Waiting for runtime data...");
        networkPanel = new NetworkPanel();

        content.add(wrapPanel("NN Config", configArea));

        JPanel right = new JPanel(new GridLayout(2, 1, 0, 10));
        right.setBackground(new Color(8, 12, 24));
        right.add(wrapPanel("Runtime", runtimeArea));
        right.add(wrapPanel("Live Network", networkPanel));
        content.add(right);

        add(content, BorderLayout.CENTER);
        setLocationByPlatform(true);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        updateQueue = new ConcurrentLinkedQueue<>();
        updateTimer = new Timer(Math.max(16, Config.UI_PAUSE_INTERVAL_MS * 8), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processUpdates();
            }
        });
        updateTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                updateTimer.stop();
            }
        });
    }

    public void updateStatus(
        int episode,
        int step,
        int carsFinished,
        int carsTotal,
        Car focusCar
    ) {
        if (focusCar == null) {
            return;
        }
        NeuralNetwork brain = focusCar.getBrain();
        PrioritizedReplayMemory memory = PrioritizedReplayMemory.getInstance();
        updateQueue.offer(new NNStatusSnapshot(
            episode,
            step,
            carsFinished,
            carsTotal,
            memory.size(),
            memory.getBeta(),
            memory.size() / (double) Config.MEMORY_SIZE,
            brain.getEpsilon(),
            brain.getCurrentLoss(),
            brain.getMaxQValue(),
            brain.getTrainBatchCounter(),
            focusCar.getCarIndex(),
            focusCar.getX(),
            focusCar.getY(),
            focusCar.getSpeed(),
            focusCar.getTotalReward(),
            focusCar.getLastAction(),
            focusCar.getSensorDistances(),
            brain.getLatestInputs(),
            brain.getLatestHiddenActivations(),
            focusCar.getLastQValues(),
            brain.getWeightsInputHiddenCopy(),
            brain.getWeightsHiddenOutputCopy()
        ));
    }

    private void processUpdates() {
        NNStatusSnapshot latest = null;
        NNStatusSnapshot next;
        while ((next = updateQueue.poll()) != null) {
            latest = next;
        }
        if (latest == null) {
            return;
        }
        runtimeArea.setText(buildRuntimeText(latest));
        networkPanel.setSnapshot(latest);
    }

    private JTextArea buildArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setForeground(new Color(222, 235, 255));
        area.setBackground(new Color(14, 22, 40));
        area.setCaretColor(area.getForeground());
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return area;
    }

    private JPanel wrapPanel(String title, Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(8, 12, 24));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 88, 122), 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));

        JLabel label = new JLabel(title);
        label.setFont(new Font("Monospaced", Font.BOLD, 14));
        label.setForeground(new Color(98, 245, 255));
        label.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        panel.add(label, BorderLayout.NORTH);
        if (component instanceof JTextArea) {
            panel.add(new JScrollPane(component), BorderLayout.CENTER);
        } else {
            panel.add(component, BorderLayout.CENTER);
        }
        return panel;
    }

    private String buildConfigText() {
        return String.format(Locale.US,
            """
            Mode
              inferenceOnly=%s
              fileLogging=%s
              headless=%s

            Network
              inputSize=%d
              hiddenSize=%d
              outputSize=%d
              learningRate=%.6f
              gamma=%.3f

            Exploration
              epsilonStart=%.3f
              epsilonMin=%.3f
              epsilonDecay=%.6f

            Replay / Training
              memorySize=%d
              batchSize=%d
              trainEvery=%d
              perAlpha=%.3f
              perBetaStart=%.3f
              perBetaInc=%.6f

            Simulation
              cars=%d
              episodes=%d
              stepsPerEpisode=%d
              sensors=%d
              obstacleCount=%d
              modelLoad=%s
              modelSave=%s
            """,
            Config.isInferenceOnly(),
            Config.isFileLoggingEnabled(),
            Config.isHeadlessLogsOnly(),
            Config.INPUT_SIZE,
            Config.HIDDEN_SIZE,
            Config.OUTPUT_SIZE,
            Config.LEARNING_RATE,
            Config.GAMMA,
            Config.EPSILON_START,
            Config.EPSILON_MIN,
            Config.EPSILON_DECAY,
            Config.MEMORY_SIZE,
            Config.BATCH_SIZE,
            Config.TRAIN_EVERY_N_STEPS,
            Config.PER_ALPHA,
            Config.PER_BETA_START,
            Config.PER_BETA_INCREMENT,
            Config.numberOfCars(),
            Config.NUMBER_OF_EPISODES,
            Config.dynamicStepsPerEpisode(),
            Config.SENSOR_RAY_COUNT,
            Config.OBSTACLE_COUNT,
            Config.MODEL_LOAD_FILE_PATH,
            Config.MODEL_SAVE_FILE_PATH
        );
    }

    private String buildRuntimeText(NNStatusSnapshot s) {
        return String.format(Locale.US,
            """
            Episode
              episode=%d
              step=%d / %d
              carsFinished=%d / %d

            Replay Memory
              size=%d / %d
              fill=%.1f%%
              beta=%.4f
              trainBatches=%d

            NN Runtime
              epsilon=%.5f
              currentLoss=%.6f
              maxQ=%.6f

            Focus Car
              carIndex=%d
              position=(%.1f, %.1f)
              speed=%.2f
              reward=%.2f
              lastAction=%s
            """,
            s.episode,
            s.step,
            Config.dynamicStepsPerEpisode(),
            s.carsFinished,
            s.carsTotal,
            s.replaySize,
            Config.MEMORY_SIZE,
            s.replayFillRatio * 100.0,
            s.replayBeta,
            s.trainBatchCounter,
            s.epsilon,
            s.loss,
            s.maxQ,
            s.carIndex,
            s.carX,
            s.carY,
            s.carSpeed,
            s.carReward,
            actionLabel(s.lastAction)
        );
    }

    private String actionLabel(int action) {
        return switch (action) {
            case 0 -> "UP";
            case 1 -> "DOWN";
            case 2 -> "LEFT";
            case 3 -> "RIGHT";
            default -> "NONE";
        };
    }
}
