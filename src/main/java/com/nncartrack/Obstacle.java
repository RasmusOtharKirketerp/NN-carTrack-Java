package com.nncartrack;

import java.awt.Color;
import java.awt.Graphics;

public class Obstacle {
    private double x, y;

    public Obstacle(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // Render the obstacle
    public void draw(Graphics g) {
        g.setColor(Color.RED);
        int size = (int)Config.OBSTACLE_SIZE;
        g.fillRect((int) x - size/2, (int) y - size/2, size, size);
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
}
