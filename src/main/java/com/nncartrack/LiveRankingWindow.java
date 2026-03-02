package com.nncartrack;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LiveRankingWindow extends JFrame {
    private final DashboardPanel dashboardPanel;
    private final List<RowData> rankedRows = new ArrayList<>();

    private static class RowData {
        final int place;
        final int snapshotId;
        final int episode;
        final int carIndex;
        final double score;

        RowData(int place, int snapshotId, int episode, int carIndex, double score) {
            this.place = place;
            this.snapshotId = snapshotId;
            this.episode = episode;
            this.carIndex = carIndex;
            this.score = score;
        }
    }

    private class DashboardPanel extends JPanel {
        DashboardPanel() {
            setBackground(new Color(8, 12, 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int pad = Math.max(10, Math.min(w, h) / 60);
            GradientPaint bg = new GradientPaint(0, 0, new Color(10, 14, 24), 0, h, new Color(4, 8, 14));
            g2d.setPaint(bg);
            g2d.fillRect(0, 0, w, h);

            int titleSize = Math.max(16, Math.min(28, h / 24));
            int headerSize = Math.max(11, Math.min(20, h / 42));
            int titleY = pad + titleSize;
            int headerY = titleY + headerSize + 6;
            g2d.setColor(new Color(0, 215, 255, 180));
            g2d.setFont(new Font("Monospaced", Font.BOLD, titleSize));
            g2d.drawString("BEST RUN INSTANCES TOP 15", pad, titleY);
            g2d.setColor(new Color(180, 200, 220, 170));
            g2d.setFont(new Font("Monospaced", Font.PLAIN, headerSize));

            if (rankedRows.isEmpty()) {
                return;
            }

            double topScore = rankedRows.get(0).score;
            double bottomScore = rankedRows.get(rankedRows.size() - 1).score;
            double span = Math.max(1.0, topScore - bottomScore);

            int contentTop = headerY + 8;
            int contentBottom = h - pad;
            int availableH = Math.max(80, contentBottom - contentTop);
            int rowH = Math.max(18, availableH / Math.max(1, rankedRows.size()));
            int startY = contentTop + rowH - 3;
            int textSize = Math.max(11, Math.min(22, rowH - 5));
            int barH = Math.max(8, rowH - 4);
            int badgeSize = Math.max(8, rowH - 8);
            int carDotSize = Math.max(7, rowH - 10);
            int rowLeft = pad;
            int rowWidth = w - 2 * pad;
            int colPos = rowLeft + 32;
            int colRun = rowLeft + 72;
            int colEp = rowLeft + 124;
            int colCar = rowLeft + 176;
            int colScore = rowLeft + 232;

            g2d.setColor(new Color(180, 200, 220, 170));
            g2d.setFont(new Font("Monospaced", Font.PLAIN, headerSize));
            g2d.drawString("POS", colPos, headerY);
            g2d.drawString("RUN", colRun, headerY);
            g2d.drawString("EP", colEp, headerY);
            g2d.drawString("CAR", colCar, headerY);
            g2d.drawString("SCORE", colScore, headerY);

            for (int i = 0; i < rankedRows.size(); i++) {
                RowData r = rankedRows.get(i);
                int y = startY + i * rowH;

                Color rowBg = (i % 2 == 0) ? new Color(255, 255, 255, 16) : new Color(255, 255, 255, 8);
                g2d.setColor(rowBg);
                g2d.fillRoundRect(rowLeft, y - rowH + 4, rowWidth, rowH - 2, 10, 10);

                float hue = (r.carIndex % Math.max(1, Config.numberOfCars())) / (float) Math.max(1, Config.numberOfCars());
                Color carColor = Color.getHSBColor(hue, 0.9f, 1.0f);

                double norm = (r.score - bottomScore) / span;
                int barW = (int) ((rowWidth - 6) * Math.max(0.0, Math.min(1.0, norm)));
                g2d.setColor(new Color(carColor.getRed(), carColor.getGreen(), carColor.getBlue(), 55));
                g2d.fillRoundRect(rowLeft + 3, y - rowH + 6, barW, barH, 8, 8);

                if (r.place <= 3) {
                    g2d.setColor(r.place == 1 ? new Color(255, 215, 0) : (r.place == 2 ? new Color(190, 200, 210) : new Color(210, 140, 70)));
                    g2d.fillOval(rowLeft + 4, y - rowH + (rowH - badgeSize) / 2, badgeSize, badgeSize);
                }
                g2d.setColor(carColor);
                g2d.fillOval(rowLeft + 18, y - rowH + (rowH - carDotSize) / 2, carDotSize, carDotSize);

                g2d.setColor(new Color(235, 242, 255, 220));
                g2d.setFont(new Font("Monospaced", Font.PLAIN, textSize));
                int ty = y - (rowH - textSize) / 3;
                g2d.drawString(String.format(Locale.US, "%2d", r.place), colPos, ty);
                g2d.drawString(String.format(Locale.US, "%3d", r.snapshotId), colRun, ty);
                g2d.drawString(String.format(Locale.US, "%3d", r.episode), colEp, ty);
                g2d.drawString(String.format(Locale.US, "C%-2d", r.carIndex), colCar, ty);
                g2d.drawString(String.format(Locale.US, "%8.0f", r.score), colScore, ty);
            }
        }
    }

    public LiveRankingWindow() {
        setTitle("F1-Style Live Ranking");
        setSize(460, 360);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        dashboardPanel = new DashboardPanel();
        add(dashboardPanel, BorderLayout.CENTER);
        setLocationByPlatform(true);
    }

    public void updateBestRuns(List<RunSnapshot> bestRuns) {
        if (bestRuns == null || bestRuns.isEmpty()) {
            return;
        }
        rankedRows.clear();
        int limit = Math.min(15, bestRuns.size());
        for (int i = 0; i < limit; i++) {
            RunSnapshot run = bestRuns.get(i);
            rankedRows.add(new RowData(
                i + 1,
                run.getSnapshotId(),
                run.getEpisode(),
                run.getCarIndex(),
                run.getScore()
            ));
        }
        dashboardPanel.repaint();
    }
}
