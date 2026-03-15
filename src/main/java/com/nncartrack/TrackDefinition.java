package com.nncartrack;

import java.util.ArrayList;
import java.util.List;

public class TrackDefinition {
    public static class Obstacle {
        private double x;
        private double y;
        private int width;
        private int height;

        public Obstacle() {
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    private String name;
    private double margin;
    private int width;
    private int height;
    private double startX;
    private double startY;
    private double finishX;
    private List<Obstacle> obstacles = new ArrayList<>();

    public TrackDefinition() {
    }

    public String getName() {
        return name == null || name.isBlank() ? "unnamed-track" : name;
    }

    public double getMargin() {
        return margin;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getStartX() {
        return startX;
    }

    public double getStartY() {
        return startY;
    }

    public double getFinishX() {
        return finishX;
    }

    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    public void validate(String sourcePath) {
        if (margin < 0.0) {
            throw new IllegalArgumentException("Track margin must be >= 0 in " + sourcePath);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Track size must be positive in " + sourcePath);
        }
        if (startX <= 0.0 || startY <= 0.0) {
            throw new IllegalArgumentException("Track start position must be positive in " + sourcePath);
        }
        if (finishX <= startX) {
            throw new IllegalArgumentException("Track finishX must be greater than startX in " + sourcePath);
        }
        if (obstacles == null) {
            obstacles = new ArrayList<>();
        }
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle obstacle = obstacles.get(i);
            if (obstacle == null) {
                throw new IllegalArgumentException("Track obstacle " + i + " is null in " + sourcePath);
            }
            if (obstacle.getWidth() <= 0 || obstacle.getHeight() <= 0) {
                throw new IllegalArgumentException("Track obstacle " + i + " has invalid size in " + sourcePath);
            }
        }
    }
}
