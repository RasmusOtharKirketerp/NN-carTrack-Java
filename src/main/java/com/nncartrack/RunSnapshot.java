package com.nncartrack;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class RunSnapshot {
    private final int snapshotId;
    private final int episode;
    private final int carIndex;
    private final double score;
    private final List<Point2D.Double> trail;

    public RunSnapshot(int snapshotId, int episode, int carIndex, double score, List<Point2D.Double> trail) {
        this.snapshotId = snapshotId;
        this.episode = episode;
        this.carIndex = carIndex;
        this.score = score;
        this.trail = new ArrayList<>(trail);
    }

    public int getSnapshotId() {
        return snapshotId;
    }

    public int getEpisode() {
        return episode;
    }

    public int getCarIndex() {
        return carIndex;
    }

    public double getScore() {
        return score;
    }

    public List<Point2D.Double> getTrail() {
        return trail;
    }
}
