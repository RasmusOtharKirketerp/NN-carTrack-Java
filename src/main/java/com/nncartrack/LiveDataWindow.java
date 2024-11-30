package com.nncartrack;

import javax.swing.*;
import java.awt.*;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.xy.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;

public class LiveDataWindow extends JFrame {
    private JLabel episodeLabel;
    private XYSeries rewardSeries;
    private XYSeries lossSeries;
    private XYSeries qMaxSeries;
    private XYSeries epsilonSeries;
    private static final int MAX_DATA_POINTS = 100;
    
    public LiveDataWindow() {
        setTitle("DQN Live Data");
        setSize(800, 600);
        setLayout(new BorderLayout());
        
        // Create data series
        rewardSeries = new XYSeries("Average Reward");
        lossSeries = new XYSeries("Loss");
        qMaxSeries = new XYSeries("Q-Max");
        epsilonSeries = new XYSeries("Epsilon");
        
        // Create datasets
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(rewardSeries);
        dataset.addSeries(lossSeries);
        dataset.addSeries(qMaxSeries);
        dataset.addSeries(epsilonSeries);
        
        // Create chart
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Training Metrics",
            "Episode",
            "Value",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        
        // Customize renderer
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);    // Reward
        renderer.setSeriesPaint(1, Color.RED);     // Loss
        renderer.setSeriesPaint(2, Color.GREEN);   // Q-Max
        renderer.setSeriesPaint(3, Color.ORANGE);  // Epsilon
        plot.setRenderer(renderer);
        
        // Add chart to window
        ChartPanel chartPanel = new ChartPanel(chart);
        add(chartPanel, BorderLayout.CENTER);
        
        // Add episode label
        episodeLabel = new JLabel("Episode: 0");
        add(episodeLabel, BorderLayout.NORTH);
        
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);
    }
    
    public void updateData(int episode, double epsilon, double qMax, double loss, double avgScore) {
        episodeLabel.setText(String.format("Episode: %d", episode));
        
        // Add new data points
        rewardSeries.add(episode, avgScore);
        lossSeries.add(episode, loss);
        qMaxSeries.add(episode, qMax);
        epsilonSeries.add(episode, epsilon);
        
        // Remove old points if needed
        if (rewardSeries.getItemCount() > MAX_DATA_POINTS) {
            rewardSeries.remove(0);
            lossSeries.remove(0);
            qMaxSeries.remove(0);
            epsilonSeries.remove(0);
        }
    }
}