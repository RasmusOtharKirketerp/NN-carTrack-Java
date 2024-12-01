package com.nncartrack;

import javax.swing.*;
import java.awt.*;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.xy.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.axis.NumberAxis;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class LiveDataWindow extends JFrame {
    private JLabel episodeLabel;
    private XYSeries rewardSeries;
    private XYSeries lossSeries;
    private XYSeries qMaxSeries;
    private XYSeries epsilonSeries;
    private static final int MAX_DATA_POINTS = Config.MAX_DATA_POINTS;
    private final Queue<DataUpdate> updateQueue;
    private volatile boolean running;
    private Thread updateThread;

    // Data class to hold updates
    private static class DataUpdate {
        final int episode;
        final double epsilon;
        final double qMax;
        final double loss;
        final double avgScore;

        DataUpdate(int episode, double epsilon, double qMax, double loss, double avgScore) {
            this.episode = episode;
            this.epsilon = epsilon;
            this.qMax = qMax;
            this.loss = loss;
            this.avgScore = avgScore;
        }
    }
    
    public LiveDataWindow() {
        setTitle("DQN Live Data");
        setSize(Config.LIVE_DATA_WINDOW_WIDTH, Config.LIVE_DATA_WINDOW_HEIGHT);
        setLayout(new BorderLayout());
        
        // Create data series
        rewardSeries = new XYSeries("Average Reward");
        lossSeries = new XYSeries("Loss");
        qMaxSeries = new XYSeries("Q-Max");
        epsilonSeries = new XYSeries("Epsilon");
        
        // Create separate datasets for different value ranges
        XYSeriesCollection dataset1 = new XYSeriesCollection();
        dataset1.addSeries(rewardSeries);
        dataset1.addSeries(lossSeries);
        
        XYSeriesCollection dataset2 = new XYSeriesCollection();
        dataset2.addSeries(qMaxSeries);
        dataset2.addSeries(epsilonSeries);
        
        // Create chart with primary dataset
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Training Metrics",
            "Episode",
            "Value",
            dataset1,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        
        // Get the plot and create second axis
        XYPlot plot = chart.getXYPlot();
        NumberAxis axis2 = new NumberAxis("Normalized Value (0-1)");
        axis2.setRange(0.0, 1.0);
        plot.setRangeAxis(1, axis2);
        plot.setDataset(1, dataset2);
        plot.mapDatasetToRangeAxis(1, 1);
        
        // Create and customize renderers
        XYLineAndShapeRenderer renderer1 = new XYLineAndShapeRenderer();
        renderer1.setSeriesPaint(0, Color.BLUE);    // Reward
        renderer1.setSeriesPaint(1, Color.RED);     // Loss
        
        XYLineAndShapeRenderer renderer2 = new XYLineAndShapeRenderer();
        renderer2.setSeriesPaint(0, Color.GREEN);   // Q-Max
        renderer2.setSeriesPaint(1, Color.ORANGE);  // Epsilon
        
        plot.setRenderer(0, renderer1);
        plot.setRenderer(1, renderer2);
        
        // Add chart to window
        ChartPanel chartPanel = new ChartPanel(chart);
        add(chartPanel, BorderLayout.CENTER);
        
        // Add episode label
        episodeLabel = new JLabel("Episode: 0");
        add(episodeLabel, BorderLayout.NORTH);
        
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);

        updateQueue = new ConcurrentLinkedQueue<>();
        running = true;
        
        // Start update thread
        updateThread = new Thread(this::processUpdates);
        updateThread.setDaemon(true);
        updateThread.start();

        // Add window listener to clean up thread
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running = false;
                updateThread.interrupt();
            }
        });
    }

    private void processUpdates() {
        while (running) {
            DataUpdate update = updateQueue.poll();
            if (update != null) {
                SwingUtilities.invokeLater(() -> {
                    episodeLabel.setText(String.format("Episode: %d", update.episode));
                    
                    rewardSeries.add(update.episode, update.avgScore);
                    lossSeries.add(update.episode, update.loss);
                    qMaxSeries.add(update.episode, update.qMax);
                    epsilonSeries.add(update.episode, update.epsilon);
                    
                    // Remove old points if needed
                    if (rewardSeries.getItemCount() > MAX_DATA_POINTS) {
                        rewardSeries.remove(0);
                        lossSeries.remove(0);
                        qMaxSeries.remove(0);
                        epsilonSeries.remove(0);
                    }
                });
            }
            try {
                Thread.sleep(Config.UI_PAUSE_INTERVAL_MS); // Use the configured update interval
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    public void updateData(int episode, double epsilon, double qMax, double loss, double avgScore) {
        updateQueue.offer(new DataUpdate(episode, epsilon, qMax, loss, avgScore));
    }
}