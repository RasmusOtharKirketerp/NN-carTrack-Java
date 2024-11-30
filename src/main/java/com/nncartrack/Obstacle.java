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
        g.fillRect((int) x - 10, (int) y - 10, 20, 20);
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
}
