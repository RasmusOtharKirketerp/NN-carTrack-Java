package com.nncartrack;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ModelViewer extends JFrame {
    private static final DecimalFormat KB_FORMAT = new DecimalFormat("0.0");

    private final DefaultListModel<ModelEntry> listModel = new DefaultListModel<>();
    private final JList<ModelEntry> modelList = new JList<>(listModel);
    private final JTextArea summaryArea = new JTextArea();
    private final NetworkPanel networkPanel = new NetworkPanel();
    private final JButton playButton = new JButton("Open In Play");
    private final JButton playLogsButton = new JButton("Open In Play Logs");
    private final JButton resumeTrainingButton = new JButton("Resume Training");
    private final JButton refreshButton = new JButton("Refresh");
    private final JComboBox<SpeedOption> speedSelector = new JComboBox<>(new SpeedOption[] {
        new SpeedOption("Very Slow", 60),
        new SpeedOption("Slow", 30),
        new SpeedOption("Medium", 12),
        new SpeedOption("Fast", 4),
        new SpeedOption("Max", 0)
    });

    public ModelViewer() {
        setTitle("Model Viewer");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1180, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modelList.setFont(new Font("Monospaced", Font.PLAIN, 13));
        modelList.addListSelectionListener(e -> updateDetails());

        JScrollPane listScroll = new JScrollPane(modelList);
        listScroll.setPreferredSize(new Dimension(420, 680));
        root.add(listScroll, BorderLayout.WEST);

        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setBackground(new Color(14, 22, 40));
        summaryArea.setForeground(new Color(222, 235, 255));
        summaryArea.setCaretColor(summaryArea.getForeground());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Summary", new JScrollPane(summaryArea));
        tabs.addTab("NN Visual", networkPanel);
        root.add(tabs, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(10, 0));
        JPanel speedPanel = new JPanel(new BorderLayout(6, 0));
        JLabel speedLabel = new JLabel("Play Speed");
        speedLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        speedPanel.add(speedLabel, BorderLayout.WEST);
        speedSelector.setSelectedIndex(1);
        speedPanel.add(speedSelector, BorderLayout.CENTER);
        controls.add(speedPanel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new GridLayout(1, 4, 8, 0));
        buttons.add(playButton);
        buttons.add(playLogsButton);
        buttons.add(resumeTrainingButton);
        buttons.add(refreshButton);
        controls.add(buttons, BorderLayout.CENTER);
        root.add(controls, BorderLayout.SOUTH);

        playButton.addActionListener(e -> launchSelectedModel("play", false));
        playLogsButton.addActionListener(e -> launchSelectedModel("playlogs", false));
        resumeTrainingButton.addActionListener(e -> launchSelectedModel("", true));
        refreshButton.addActionListener(e -> refreshModels());

        refreshModels();
    }

    private void refreshModels() {
        List<ModelEntry> entries = new ArrayList<>();
        Path modelsRoot = Paths.get("models");
        if (Files.exists(modelsRoot)) {
            try (var stream = Files.walk(modelsRoot)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nn"))
                    .sorted(Comparator.comparing(ModelViewer::lastModifiedSafe).reversed())
                    .forEach(path -> entries.add(new ModelEntry(path)));
            } catch (IOException e) {
                showError("Failed to scan model files: " + e.getMessage());
            }
        }

        listModel.clear();
        for (ModelEntry entry : entries) {
            listModel.addElement(entry);
        }

        if (!listModel.isEmpty()) {
            modelList.setSelectedIndex(0);
        } else {
            summaryArea.setText("No .nn model files found under models/");
            networkPanel.setSnapshot(null);
        }
    }

    private void updateDetails() {
        ModelEntry entry = modelList.getSelectedValue();
        if (entry == null) {
            summaryArea.setText("Select a model to inspect.");
            networkPanel.setSnapshot(null);
            return;
        }

        try {
            ModelSnapshot snapshot = ModelSnapshot.read(entry.path);
            summaryArea.setText(String.format(
                Locale.US,
                """
                File
                  name=%s
                  path=%s

                Metadata
                  modified=%s
                  size=%s

                Network
                  inputSize=%d
                  hiddenSize=%d
                  outputSize=%d

                Weight Stats
                  input->hidden min=%.6f max=%.6f meanAbs=%.6f
                  hidden->output min=%.6f max=%.6f meanAbs=%.6f

                Notes
                  Left side shows the saved model files.
                  NN Visual shows a node graph and weight heatmaps.
                  Use the buttons below to run or resume from the selected model.
                  Play Speed controls how much delay is added per simulation step.
                  Play mode now runs a single car by default for easier viewing.
                """,
                entry.path.getFileName(),
                entry.path,
                entry.modifiedDisplay,
                entry.sizeDisplay,
                snapshot.inputSize,
                snapshot.hiddenSize,
                snapshot.outputSize,
                snapshot.inputHiddenStats.min,
                snapshot.inputHiddenStats.max,
                snapshot.inputHiddenStats.meanAbs,
                snapshot.hiddenOutputStats.min,
                snapshot.hiddenOutputStats.max,
                snapshot.hiddenOutputStats.meanAbs
            ));
            networkPanel.setSnapshot(snapshot);
        } catch (IOException e) {
            summaryArea.setText("Failed to read model: " + e.getMessage());
            networkPanel.setSnapshot(null);
        }
    }

    private void launchSelectedModel(String mode, boolean resumeTraining) {
        ModelEntry entry = modelList.getSelectedValue();
        if (entry == null) {
            showError("Select a model first.");
            return;
        }

        try {
            List<String> command = new ArrayList<>();
            command.add("cmd.exe");
            command.add("/c");
            command.add("start");
            command.add("\"NN Model Viewer\"");
            command.add("cmd");
            command.add("/c");
            command.add("call");
            command.add("run.bat");
            if (mode != null && !mode.isBlank()) {
                command.add(mode);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(Paths.get(".").toFile());
            String modelPath = entry.path.toString().replace('\\', '/');
            StringBuilder props = new StringBuilder("-Dnn.model.load.path=").append(modelPath);
            if (resumeTraining) {
                props.append(" -Dnn.resume.training=true");
            } else {
                props.append(" -Dnn.simulation.sleep.ms=").append(selectedSpeedDelayMs());
            }
            pb.environment().put("NN_MAVEN_EXTRA_PROPS", props.toString());
            pb.start();
        } catch (IOException e) {
            showError("Failed to launch selected model: " + e.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Model Viewer", JOptionPane.ERROR_MESSAGE);
    }

    private static FileTime lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0L);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ModelViewer().setVisible(true));
    }

    private int selectedSpeedDelayMs() {
        SpeedOption selected = (SpeedOption) speedSelector.getSelectedItem();
        return selected == null ? 0 : selected.delayMs;
    }

    private static final class NetworkPanel extends JPanel {
        private ModelSnapshot snapshot;

        NetworkPanel() {
            setBackground(new Color(14, 22, 40));
        }

        void setSnapshot(ModelSnapshot snapshot) {
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
                g2d.drawString("Select a model to visualize...", 24, 36);
                return;
            }

            int diagramWidth = (int) (width * 0.60);
            drawNetwork(g2d, 0, 0, diagramWidth, height, snapshot);
            int heatmapX = diagramWidth + 10;
            int heatmapWidth = width - heatmapX - 12;
            int topHeatmapHeight = (height - 42) / 2;
            drawHeatmap(g2d, heatmapX, 16, heatmapWidth, topHeatmapHeight, "Input -> Hidden", snapshot.inputHidden, snapshot.inputHiddenStats);
            drawHeatmap(g2d, heatmapX, 26 + topHeatmapHeight, heatmapWidth, topHeatmapHeight, "Hidden -> Output", snapshot.hiddenOutput, snapshot.hiddenOutputStats);
        }

        private void drawNetwork(Graphics2D g2d, int x, int y, int width, int height, ModelSnapshot snapshot) {
            double[][] layers = {
                new double[snapshot.inputSize],
                aggregateColumns(snapshot.inputHidden),
                aggregateColumns(snapshot.hiddenOutput)
            };
            String[] labels = {"INPUT", "HIDDEN", "OUTPUT"};
            int[] xs = {x + width / 6, x + width / 2, x + (width * 5) / 6};
            Point[][] points = new Point[layers.length][];
            for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
                points[layerIndex] = buildNodePositions(xs[layerIndex], height, layers[layerIndex].length);
            }

            drawConnections(g2d, points[0], points[1], snapshot.inputHidden);
            drawConnections(g2d, points[1], points[2], snapshot.hiddenOutput);

            for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
                g2d.setColor(new Color(98, 245, 255));
                g2d.setFont(new Font("Monospaced", Font.BOLD, 15));
                g2d.drawString(labels[layerIndex], xs[layerIndex] - 32, 24);
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
            Stroke previousStroke = g2d.getStroke();
            for (int i = 0; i < from.length && i < weights.length; i++) {
                for (int j = 0; j < to.length && j < weights[i].length; j++) {
                    double normalized = Math.min(1.0, Math.abs(weights[i][j]) / maxAbs);
                    int alpha = 10 + (int) (110 * normalized);
                    g2d.setColor(weights[i][j] >= 0
                        ? new Color(57, 255, 20, alpha)
                        : new Color(255, 77, 109, alpha));
                    g2d.setStroke(new BasicStroke((float) (0.3 + normalized * 1.4)));
                    g2d.drawLine(from[i].x, from[i].y, to[j].x, to[j].y);
                }
            }
            g2d.setStroke(previousStroke);
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
                int radius = outputLayer ? 16 : (points.length > 24 ? 7 : 10);
                Color fill = value >= 0
                    ? new Color(0, 224, 255, 110 + (int) (145 * normalized))
                    : new Color(255, 196, 0, 110 + (int) (145 * normalized));
                g2d.setColor(fill);
                g2d.fillOval(p.x - radius, p.y - radius, radius * 2, radius * 2);
                g2d.setColor(new Color(230, 240, 255));
                g2d.drawOval(p.x - radius, p.y - radius, radius * 2, radius * 2);
                if (points.length <= 12 || outputLayer) {
                    g2d.setFont(new Font("Monospaced", Font.PLAIN, 10));
                    g2d.drawString(String.format(Locale.US, "%d", i), p.x - radius - 14, p.y + 4);
                }
            }
        }

        private void drawHeatmap(Graphics2D g2d, int x, int y, int width, int height,
                                 String title, double[][] matrix, MatrixStats stats) {
            g2d.setColor(new Color(16, 24, 40, 220));
            g2d.fillRoundRect(x, y, width, height, 12, 12);
            g2d.setColor(new Color(98, 245, 255));
            g2d.setFont(new Font("Monospaced", Font.BOLD, 13));
            g2d.drawString(title, x + 12, y + 20);
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));
            g2d.drawString(String.format(Locale.US, "min %.4f   max %.4f   meanAbs %.4f", stats.min, stats.max, stats.meanAbs), x + 12, y + 36);

            int rows = Math.max(1, matrix.length);
            int cols = Math.max(1, matrix[0].length);
            int top = y + 48;
            int innerHeight = Math.max(20, height - 60);
            int cellW = Math.max(1, width / cols);
            int cellH = Math.max(1, innerHeight / rows);
            double maxAbs = Math.max(1e-9, Math.max(Math.abs(stats.min), Math.abs(stats.max)));
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    double value = matrix[r][c];
                    double normalized = Math.min(1.0, Math.abs(value) / maxAbs);
                    Color cell = value >= 0
                        ? new Color(0, 224, 255, 30 + (int) (200 * normalized))
                        : new Color(255, 77, 109, 30 + (int) (200 * normalized));
                    g2d.setColor(cell);
                    g2d.fillRect(x + c * cellW, top + r * cellH, Math.max(1, cellW - 1), Math.max(1, cellH - 1));
                }
            }
        }

        private double[] aggregateColumns(double[][] matrix) {
            double[] values = new double[matrix[0].length];
            for (int c = 0; c < matrix[0].length; c++) {
                double sum = 0.0;
                for (double[] row : matrix) {
                    sum += Math.abs(row[c]);
                }
                values[c] = sum;
            }
            return values;
        }
    }

    private static final class ModelEntry {
        private final Path path;
        private final String modifiedDisplay;
        private final String sizeDisplay;

        private ModelEntry(Path path) {
            this.path = path;
            this.modifiedDisplay = lastModifiedSafe(path).toString();
            this.sizeDisplay = readableSize(path);
        }

        @Override
        public String toString() {
            return path.toString().replace('\\', '/');
        }

        private static String readableSize(Path path) {
            try {
                long bytes = Files.size(path);
                return KB_FORMAT.format(bytes / 1024.0) + " KB";
            } catch (IOException e) {
                return "unknown";
            }
        }
    }

    private static final class SpeedOption {
        private final String label;
        private final int delayMs;

        private SpeedOption(String label, int delayMs) {
            this.label = label;
            this.delayMs = delayMs;
        }

        @Override
        public String toString() {
            return label + " (" + delayMs + " ms)";
        }
    }

    private static final class ModelSnapshot {
        private final int inputSize;
        private final int hiddenSize;
        private final int outputSize;
        private final double[][] inputHidden;
        private final double[][] hiddenOutput;
        private final MatrixStats inputHiddenStats;
        private final MatrixStats hiddenOutputStats;

        private ModelSnapshot(int inputSize, int hiddenSize, int outputSize,
                              double[][] inputHidden, double[][] hiddenOutput) {
            this.inputSize = inputSize;
            this.hiddenSize = hiddenSize;
            this.outputSize = outputSize;
            this.inputHidden = inputHidden;
            this.hiddenOutput = hiddenOutput;
            this.inputHiddenStats = MatrixStats.of(inputHidden);
            this.hiddenOutputStats = MatrixStats.of(hiddenOutput);
        }

        private static ModelSnapshot read(Path path) throws IOException {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                int inputSize = in.readInt();
                int hiddenSize = in.readInt();
                int outputSize = in.readInt();
                double[][] inputHidden = new double[inputSize][hiddenSize];
                double[][] hiddenOutput = new double[hiddenSize][outputSize];
                for (int i = 0; i < inputSize; i++) {
                    for (int j = 0; j < hiddenSize; j++) {
                        inputHidden[i][j] = in.readDouble();
                    }
                }
                for (int i = 0; i < hiddenSize; i++) {
                    for (int j = 0; j < outputSize; j++) {
                        hiddenOutput[i][j] = in.readDouble();
                    }
                }
                return new ModelSnapshot(inputSize, hiddenSize, outputSize, inputHidden, hiddenOutput);
            }
        }
    }

    private static final class MatrixStats {
        private final double min;
        private final double max;
        private final double meanAbs;

        private MatrixStats(double min, double max, double meanAbs) {
            this.min = min;
            this.max = max;
            this.meanAbs = meanAbs;
        }

        private static MatrixStats of(double[][] matrix) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double absSum = 0.0;
            int count = 0;
            for (double[] row : matrix) {
                for (double value : row) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    absSum += Math.abs(value);
                    count++;
                }
            }
            if (count == 0) {
                return new MatrixStats(0.0, 0.0, 0.0);
            }
            return new MatrixStats(min, max, absSum / count);
        }
    }
}
