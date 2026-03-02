package com.nncartrack;

import javax.swing.*;
import java.awt.*;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.xy.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.axis.NumberAxis;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LiveDataWindow extends JFrame {
    private static final double LOG_SWITCH_THRESHOLD = 1000.0;
    private JLabel episodeLabel;
    private XYSeries rewardSeries;
    private XYSeries lossSeries;
    private XYSeries qMaxSeries;
    private XYSeries epsilonSeries;
    private static final int MAX_DATA_POINTS = Config.MAX_DATA_POINTS;
    private final Queue<DataUpdate> updateQueue;
    private final Timer updateTimer;
    private final List<DataUpdate> history = new ArrayList<>();
    private NumberAxis primaryAxis;
    private boolean useSymlog = false;

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
        setTitle("NN Telemetry Console");
        setSize(Config.LIVE_DATA_WINDOW_WIDTH, Config.LIVE_DATA_WINDOW_HEIGHT);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(8, 12, 24));
        
        // Create data series
        rewardSeries = new XYSeries("Average Reward");
        lossSeries = new XYSeries("Loss");
        qMaxSeries = new XYSeries("Q-Max");
        epsilonSeries = new XYSeries("Epsilon");
        
        // Create separate datasets for different value ranges
        XYSeriesCollection dataset1 = new XYSeriesCollection();
        dataset1.addSeries(rewardSeries);
        dataset1.addSeries(lossSeries);
        dataset1.addSeries(qMaxSeries);
        
        XYSeriesCollection dataset2 = new XYSeriesCollection();
        dataset2.addSeries(epsilonSeries);
        
        // Create chart with primary dataset
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Neural Network Telemetry",
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
        primaryAxis = (NumberAxis) plot.getRangeAxis();
        NumberAxis axis2 = new NumberAxis("Epsilon (0-1)");
        axis2.setRange(0.0, 1.0);
        plot.setRangeAxis(1, axis2);
        plot.setDataset(1, dataset2);
        plot.mapDatasetToRangeAxis(1, 1);
        styleChart(chart, plot, axis2);
        
        // Create and customize renderers
        XYLineAndShapeRenderer renderer1 = new XYLineAndShapeRenderer();
        renderer1.setDefaultShapesVisible(false);
        renderer1.setSeriesPaint(0, new Color(0, 224, 255));   // Reward
        renderer1.setSeriesPaint(1, new Color(255, 77, 109));  // Loss
        renderer1.setSeriesPaint(2, new Color(57, 255, 20));   // Q-Max
        renderer1.setSeriesStroke(0, new BasicStroke(2.2f));
        renderer1.setSeriesStroke(1, new BasicStroke(2.0f));
        renderer1.setSeriesStroke(2, new BasicStroke(1.8f));
        
        XYLineAndShapeRenderer renderer2 = new XYLineAndShapeRenderer();
        renderer2.setDefaultShapesVisible(false);
        renderer2.setSeriesPaint(0, new Color(255, 196, 0));   // Epsilon
        renderer2.setSeriesStroke(0, new BasicStroke(1.8f));
        
        plot.setRenderer(0, renderer1);
        plot.setRenderer(1, renderer2);
        
        // Add chart to window
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setBackground(new Color(8, 12, 24));
        add(chartPanel, BorderLayout.CENTER);
        
        // Add top telemetry label
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(8, 12, 24));
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        episodeLabel = new JLabel("EPISODE 0  |  MODE: TRAINING");
        episodeLabel.setForeground(new Color(98, 245, 255));
        episodeLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        header.add(episodeLabel, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);

        updateQueue = new ConcurrentLinkedQueue<>();
        updateTimer = new Timer(Config.UI_PAUSE_INTERVAL_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processUpdates();
            }
        });
        updateTimer.start();

        // Stop async updater when closing.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                updateTimer.stop();
            }
        });
    }

    private void processUpdates() {
        DataUpdate update;
        while ((update = updateQueue.poll()) != null) {
            episodeLabel.setText(String.format("EPISODE %d  |  MODE: TRAINING", update.episode));
            history.add(update);
            if (history.size() > MAX_DATA_POINTS) {
                history.remove(0);
            }

            boolean shouldUseSymlog = shouldUseSymlog(history);
            if (shouldUseSymlog != useSymlog) {
                useSymlog = shouldUseSymlog;
                primaryAxis.setLabel(useSymlog ? "Value (symlog)" : "Value");
            }

            rewardSeries.clear();
            lossSeries.clear();
            qMaxSeries.clear();
            epsilonSeries.clear();
            for (DataUpdate h : history) {
                rewardSeries.add(h.episode, displayValue(h.avgScore));
                lossSeries.add(h.episode, displayValue(h.loss));
                qMaxSeries.add(h.episode, displayValue(h.qMax));
                epsilonSeries.add(h.episode, h.epsilon);
            }
        }
    }
    
    public void updateData(int episode, double epsilon, double qMax, double loss, double avgScore) {
        updateQueue.offer(new DataUpdate(episode, epsilon, qMax, loss, avgScore));
    }

    private boolean shouldUseSymlog(List<DataUpdate> updates) {
        for (DataUpdate u : updates) {
            if (Math.abs(u.avgScore) >= LOG_SWITCH_THRESHOLD
                || Math.abs(u.loss) >= LOG_SWITCH_THRESHOLD
                || Math.abs(u.qMax) >= LOG_SWITCH_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private double displayValue(double raw) {
        if (!useSymlog) {
            return raw;
        }
        double sign = raw < 0 ? -1.0 : 1.0;
        return sign * Math.log10(1.0 + Math.abs(raw));
    }

    private void styleChart(JFreeChart chart, XYPlot plot, NumberAxis axis2) {
        chart.setBackgroundPaint(new Color(8, 12, 24));
        chart.getTitle().setPaint(new Color(224, 244, 255));
        chart.getTitle().setFont(new Font("Monospaced", Font.BOLD, 22));
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(new Color(12, 20, 36));
            chart.getLegend().setItemPaint(new Color(220, 230, 255));
            chart.getLegend().setItemFont(new Font("Monospaced", Font.PLAIN, 12));
        }

        plot.setBackgroundPaint(new Color(14, 22, 40));
        plot.setDomainGridlinePaint(new Color(40, 62, 95));
        plot.setRangeGridlinePaint(new Color(40, 62, 95));
        plot.setOutlinePaint(new Color(98, 245, 255));
        plot.getDomainAxis().setLabelPaint(new Color(173, 220, 255));
        plot.getRangeAxis().setLabelPaint(new Color(173, 220, 255));
        plot.getDomainAxis().setTickLabelPaint(new Color(176, 195, 222));
        plot.getRangeAxis().setTickLabelPaint(new Color(176, 195, 222));
        plot.getDomainAxis().setAxisLinePaint(new Color(98, 245, 255));
        plot.getRangeAxis().setAxisLinePaint(new Color(98, 245, 255));
        axis2.setLabelPaint(new Color(173, 220, 255));
        axis2.setTickLabelPaint(new Color(176, 195, 222));
        axis2.setAxisLinePaint(new Color(98, 245, 255));
    }
}
