package com.nncartrack;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ModelViewer extends JFrame {
    private static final DecimalFormat KB_FORMAT = new DecimalFormat("0.0");
    private static final DateTimeFormatter RUN_TS_PARSER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter RUN_TS_DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DefaultListModel<ModelEntry> listModel = new DefaultListModel<>();
    private final JList<ModelEntry> modelList = new JList<>(listModel);
    private final JScrollPane listScroll = new JScrollPane(modelList);
    private final JTextArea summaryArea = new JTextArea();
    private final NetworkPanel networkPanel = new NetworkPanel();
    private final ArtifactPanel artifactPanel = new ArtifactPanel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JSplitPane contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JButton playButton = new JButton("Open In Play");
    private final JButton playLogsButton = new JButton("Open In Play Logs");
    private final JButton newTrainRunButton = new JButton("New Train Run");
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
        setTitle("NN Control Room");
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

        listScroll.setMinimumSize(new Dimension(240, 320));

        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setBackground(new Color(14, 22, 40));
        summaryArea.setForeground(new Color(222, 235, 255));
        summaryArea.setCaretColor(summaryArea.getForeground());

        tabs.addTab("Summary", new JScrollPane(summaryArea));
        tabs.addTab("NN Visual", networkPanel);
        tabs.addTab("Run Artifacts", artifactPanel);
        tabs.setMinimumSize(new Dimension(520, 320));

        contentSplitPane.setLeftComponent(listScroll);
        contentSplitPane.setRightComponent(tabs);
        contentSplitPane.setContinuousLayout(true);
        contentSplitPane.setOneTouchExpandable(true);
        contentSplitPane.setResizeWeight(0.0);
        root.add(contentSplitPane, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(10, 0));
        JPanel speedPanel = new JPanel(new BorderLayout(6, 0));
        JLabel speedLabel = new JLabel("Play Speed");
        speedLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        speedPanel.add(speedLabel, BorderLayout.WEST);
        speedSelector.setSelectedIndex(1);
        speedPanel.add(speedSelector, BorderLayout.CENTER);
        controls.add(speedPanel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new GridLayout(1, 5, 8, 0));
        buttons.add(playButton);
        buttons.add(playLogsButton);
        buttons.add(newTrainRunButton);
        buttons.add(resumeTrainingButton);
        buttons.add(refreshButton);
        controls.add(buttons, BorderLayout.CENTER);
        root.add(controls, BorderLayout.SOUTH);

        playButton.addActionListener(e -> launchSelectedModel("play", false));
        playLogsButton.addActionListener(e -> launchSelectedModel("playlogs", false));
        newTrainRunButton.addActionListener(e -> launchNewTrainingRun());
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
            artifactPanel.setModelEntry(null);
        }
        updateListPaneWidth();
    }

    private void updateListPaneWidth() {
        int width = preferredListWidth();
        listScroll.setPreferredSize(new Dimension(width, listScroll.getPreferredSize().height));
        SwingUtilities.invokeLater(() -> contentSplitPane.setDividerLocation(width));
    }

    private int preferredListWidth() {
        FontMetrics metrics = modelList.getFontMetrics(modelList.getFont());
        int widest = 0;
        for (int i = 0; i < listModel.size(); i++) {
            widest = Math.max(widest, metrics.stringWidth(listModel.get(i).toString()));
        }
        int computed = widest + 48;
        return Math.max(300, Math.min(700, computed));
    }

    private void updateDetails() {
        ModelEntry entry = modelList.getSelectedValue();
        if (entry == null) {
            summaryArea.setText("Select a model to inspect.");
            networkPanel.setSnapshot(null);
            artifactPanel.setModelEntry(null);
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
                  Run Artifacts shows the saved track, end-of-run snapshots, and the console log tail.
                  Use the buttons below to run, start fresh training, or resume from the selected model.
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
            artifactPanel.setModelEntry(entry);
        } catch (IOException e) {
            summaryArea.setText("Failed to read model: " + e.getMessage());
            networkPanel.setSnapshot(null);
            artifactPanel.setModelEntry(entry);
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
            command.add("\"NN Control Room\"");
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
        JOptionPane.showMessageDialog(this, message, "NN Control Room", JOptionPane.ERROR_MESSAGE);
    }

    private void launchNewTrainingRun() {
        try {
            List<String> command = new ArrayList<>();
            command.add("cmd.exe");
            command.add("/c");
            command.add("start");
            command.add("\"NN Control Room\"");
            command.add("cmd");
            command.add("/c");
            command.add("call");
            command.add("run.bat");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(Paths.get(".").toFile());
            pb.start();
        } catch (IOException e) {
            showError("Failed to start a new training run: " + e.getMessage());
        }
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

    private static final class ArtifactPanel extends JPanel {
        private static final int THUMB_WIDTH = 250;
        private static final int THUMB_HEIGHT = 150;

        private final JTextArea infoArea = buildTextArea();
        private final TrackPreviewPanel trackPreviewPanel = new TrackPreviewPanel();
        private final JLabel simulationLabel = buildImageLabel("Simulation snapshot");
        private final JLabel telemetryLabel = buildImageLabel("Telemetry snapshot");
        private final JLabel nnStatusLabel = buildImageLabel("NN status snapshot");
        private final JTextArea consoleArea = buildTextArea();

        private ArtifactPanel() {
            setLayout(new BorderLayout(10, 10));
            setBackground(new Color(8, 12, 24));

            infoArea.setRows(4);
            consoleArea.setRows(10);
            consoleArea.setMargin(new Insets(8, 8, 8, 8));

            JPanel thumbs = new JPanel(new GridLayout(2, 2, 10, 10));
            thumbs.setBackground(new Color(8, 12, 24));
            thumbs.add(wrapArtifactCard("Track", trackPreviewPanel));
            thumbs.add(wrapArtifactCard("Simulation", simulationLabel));
            thumbs.add(wrapArtifactCard("Telemetry", telemetryLabel));
            thumbs.add(wrapArtifactCard("NN Status", nnStatusLabel));

            add(wrapArtifactCard("Run Folder", new JScrollPane(infoArea)), BorderLayout.NORTH);
            add(thumbs, BorderLayout.CENTER);
            add(wrapArtifactCard("Console Log Tail", new JScrollPane(consoleArea)), BorderLayout.SOUTH);

            setModelEntry(null);
        }

        private void setModelEntry(ModelEntry entry) {
            if (entry == null) {
                infoArea.setText("Select a model to inspect run artifacts.");
                trackPreviewPanel.setTrackPath(null);
                setMissingImage(simulationLabel, "No simulation snapshot");
                setMissingImage(telemetryLabel, "No telemetry snapshot");
                setMissingImage(nnStatusLabel, "No NN status snapshot");
                consoleArea.setText("No console log loaded.");
                return;
            }

            Path runDirectory = entry.path.getParent();
            infoArea.setText(buildRunInfo(entry));
            trackPreviewPanel.setTrackPath(runDirectory.resolve("track.json"));
            updateThumbnail(simulationLabel, runDirectory.resolve("snapshot-simulation.png"), "Simulation snapshot");
            updateThumbnail(telemetryLabel, runDirectory.resolve("snapshot-telemetry.png"), "Telemetry snapshot");
            updateThumbnail(nnStatusLabel, runDirectory.resolve("snapshot-nn-status.png"), "NN status snapshot");
            consoleArea.setText(readLogTail(runDirectory.resolve("console.log"), 40));
            consoleArea.setCaretPosition(0);
        }

        private String buildRunInfo(ModelEntry entry) {
            Path runDirectory = entry.path.getParent();
            return String.format(
                Locale.US,
                """
                Run folder
                  %s

                Model
                  file=%s
                  timestamp=%s
                  episodes=%s
                  runtime=%s
                  modified=%s
                  size=%s

                Files
                  track.json=%s
                  console.log=%s
                  training_metrics.csv=%s
                  training_batches.csv=%s
                  run_metadata.txt=%s
                """,
                runDirectory.toString().replace('\\', '/'),
                entry.path.getFileName(),
                entry.runTimestampDisplay,
                entry.episodesDisplay,
                entry.runtimeDisplay,
                entry.modifiedDisplay,
                entry.sizeDisplay,
                Files.exists(runDirectory.resolve("track.json")),
                Files.exists(runDirectory.resolve("console.log")),
                Files.exists(runDirectory.resolve("training_metrics.csv")),
                Files.exists(runDirectory.resolve("training_batches.csv")),
                Files.exists(runDirectory.resolve("run_metadata.txt"))
            );
        }

        private void updateThumbnail(JLabel label, Path imagePath, String title) {
            if (!Files.exists(imagePath)) {
                setMissingImage(label, "Missing: " + imagePath.getFileName());
                return;
            }

            try {
                BufferedImage source = ImageIO.read(imagePath.toFile());
                if (source == null) {
                    setMissingImage(label, "Unreadable: " + imagePath.getFileName());
                    return;
                }
                Image scaled = source.getScaledInstance(THUMB_WIDTH, THUMB_HEIGHT, Image.SCALE_SMOOTH);
                label.setText("<html><center>" + title + "<br><span style='font-size:10px'>"
                    + imagePath.getFileName() + "</span></center></html>");
                label.setHorizontalTextPosition(SwingConstants.CENTER);
                label.setVerticalTextPosition(SwingConstants.BOTTOM);
                label.setIcon(new ImageIcon(scaled));
            } catch (IOException e) {
                setMissingImage(label, "Failed to load " + imagePath.getFileName());
            }
        }

        private void setMissingImage(JLabel label, String text) {
            label.setIcon(null);
            label.setText("<html><center>" + text + "</center></html>");
            label.setHorizontalTextPosition(SwingConstants.CENTER);
            label.setVerticalTextPosition(SwingConstants.CENTER);
        }

        private String readLogTail(Path logPath, int maxLines) {
            if (!Files.exists(logPath)) {
                return "No console log found for this run yet.";
            }

            LinkedList<String> lines = new LinkedList<>();
            try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    if (lines.size() > maxLines) {
                        lines.removeFirst();
                    }
                }
            } catch (IOException e) {
                return "Failed to read console log: " + e.getMessage();
            }

            if (lines.isEmpty()) {
                return "Console log is empty.";
            }
            return String.join(System.lineSeparator(), lines);
        }

        private static JTextArea buildTextArea() {
            JTextArea area = new JTextArea();
            area.setEditable(false);
            area.setFont(new Font("Monospaced", Font.PLAIN, 12));
            area.setForeground(new Color(222, 235, 255));
            area.setBackground(new Color(14, 22, 40));
            area.setCaretColor(area.getForeground());
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            return area;
        }

        private static JLabel buildImageLabel(String text) {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setFont(new Font("Monospaced", Font.PLAIN, 12));
            label.setForeground(new Color(222, 235, 255));
            label.setOpaque(true);
            label.setBackground(new Color(14, 22, 40));
            label.setPreferredSize(new Dimension(THUMB_WIDTH, THUMB_HEIGHT + 32));
            return label;
        }

        private static JComponent wrapArtifactCard(String title, JComponent component) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(new Color(8, 12, 24));
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 88, 122), 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
            ));

            JLabel label = new JLabel(title);
            label.setFont(new Font("Monospaced", Font.BOLD, 13));
            label.setForeground(new Color(98, 245, 255));
            label.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
            panel.add(label, BorderLayout.NORTH);
            panel.add(component, BorderLayout.CENTER);
            return panel;
        }
    }

    private static final class TrackPreviewPanel extends JPanel {
        private TrackDefinition track;
        private String statusMessage = "No track artifact loaded.";

        private TrackPreviewPanel() {
            setBackground(new Color(14, 22, 40));
        }

        private void setTrackPath(Path trackPath) {
            if (trackPath == null) {
                track = null;
                statusMessage = "No track artifact loaded.";
                repaint();
                return;
            }
            if (!Files.exists(trackPath)) {
                track = null;
                statusMessage = "Missing: " + trackPath.getFileName();
                repaint();
                return;
            }
            try {
                track = TrackLoader.load(trackPath.toString());
                statusMessage = track.getName() + " (" + trackPath.getFileName() + ")";
            } catch (IllegalStateException e) {
                track = null;
                statusMessage = "Failed to load track.json";
            }
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

            g2d.setColor(new Color(210, 225, 255));
            g2d.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2d.drawString(statusMessage, 12, 20);

            if (track == null) {
                return;
            }

            int pad = 12;
            int mapTop = 32;
            int mapWidth = Math.max(20, width - pad * 2);
            int mapHeight = Math.max(20, height - mapTop - pad);

            double sx = mapWidth / (track.getWidth() + track.getMargin() * 2.0);
            double sy = mapHeight / (track.getHeight() + track.getMargin() * 2.0);
            double scale = Math.min(sx, sy);

            int originX = pad;
            int originY = mapTop;
            int roadX = originX + (int) Math.round(track.getMargin() * scale);
            int roadY = originY + (int) Math.round(track.getMargin() * scale);
            int roadWidth = Math.max(1, (int) Math.round(track.getWidth() * scale));
            int roadHeight = Math.max(1, (int) Math.round(track.getHeight() * scale));

            g2d.setColor(new Color(20, 20, 24));
            g2d.fillRoundRect(roadX, roadY, roadWidth, roadHeight, 10, 10);
            g2d.setColor(new Color(98, 245, 255));
            g2d.drawRoundRect(roadX, roadY, roadWidth, roadHeight, 10, 10);

            g2d.setColor(new Color(60, 255, 120));
            int finishX = originX + (int) Math.round(track.getFinishX() * scale);
            g2d.drawLine(finishX, roadY, finishX, roadY + roadHeight);

            g2d.setColor(new Color(80, 45, 25));
            for (TrackDefinition.Obstacle obstacle : track.getObstacles()) {
                int x = originX + (int) Math.round(obstacle.getX() * scale);
                int y = originY + (int) Math.round(obstacle.getY() * scale);
                int w = Math.max(2, (int) Math.round(obstacle.getWidth() * scale));
                int h = Math.max(2, (int) Math.round(obstacle.getHeight() * scale));
                g2d.fillRoundRect(x, y, w, h, 8, 8);
                g2d.setColor(new Color(245, 210, 120));
                g2d.drawRoundRect(x, y, w, h, 8, 8);
                g2d.setColor(new Color(80, 45, 25));
            }

            int startX = originX + (int) Math.round(track.getStartX() * scale);
            int startY = originY + (int) Math.round(track.getStartY() * scale);
            g2d.setColor(new Color(255, 196, 0));
            g2d.fillOval(startX - 5, startY - 5, 10, 10);
            g2d.setColor(new Color(230, 240, 255));
            g2d.drawString("START", Math.max(8, startX - 18), Math.min(height - 8, startY - 8));
        }
    }

    private static final class ModelEntry {
        private final Path path;
        private final String modifiedDisplay;
        private final String sizeDisplay;
        private final String runTimestampDisplay;
        private final String episodesDisplay;
        private final String runtimeDisplay;
        private final String listLabel;

        private ModelEntry(Path path) {
            this.path = path;
            this.modifiedDisplay = lastModifiedSafe(path).toString();
            this.sizeDisplay = readableSize(path);
            ParsedRunName parsed = ParsedRunName.from(path);
            this.runTimestampDisplay = parsed.timestampDisplay;
            this.episodesDisplay = parsed.episodesDisplay;
            this.runtimeDisplay = parsed.runtimeDisplay;
            this.listLabel = parsed.listLabel;
        }

        @Override
        public String toString() {
            return listLabel;
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

    private static final class ParsedRunName {
        private final String timestampDisplay;
        private final String episodesDisplay;
        private final String runtimeDisplay;
        private final String listLabel;

        private ParsedRunName(String timestampDisplay, String episodesDisplay, String runtimeDisplay) {
            this.timestampDisplay = timestampDisplay;
            this.episodesDisplay = episodesDisplay;
            this.runtimeDisplay = runtimeDisplay;
            this.listLabel = String.format(
                Locale.US,
                "%s  |  %s  |  %s",
                timestampDisplay,
                episodesDisplay,
                runtimeDisplay
            );
        }

        private static ParsedRunName from(Path path) {
            String filename = path.getFileName().toString();
            String runDirName = path.getParent() != null ? path.getParent().getFileName().toString() : "";

            String timestampDisplay = formatRunTimestamp(runDirName);
            String episodesDisplay = "episodes: ?";
            String runtimeDisplay = "runtime: ?";

            int epIndex = filename.indexOf("-ep");
            int timeIndex = filename.indexOf("-t");
            int secondsIndex = filename.indexOf('s', Math.max(0, timeIndex));
            if (epIndex >= 0 && timeIndex > epIndex) {
                episodesDisplay = "episodes: " + filename.substring(epIndex + 3, timeIndex);
            }
            if (timeIndex >= 0 && secondsIndex > timeIndex) {
                runtimeDisplay = "runtime: " + filename.substring(timeIndex + 2, secondsIndex) + "s";
            }

            return new ParsedRunName(timestampDisplay, episodesDisplay, runtimeDisplay);
        }

        private static String formatRunTimestamp(String value) {
            try {
                LocalDateTime timestamp = LocalDateTime.parse(value, RUN_TS_PARSER);
                return timestamp.format(RUN_TS_DISPLAY);
            } catch (DateTimeParseException e) {
                return value == null || value.isBlank() ? "unknown run" : value;
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
