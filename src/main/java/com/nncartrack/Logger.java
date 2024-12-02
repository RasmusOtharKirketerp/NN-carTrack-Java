
package com.nncartrack;

public class Logger {
    private static Logger instance = new Logger();
    private boolean verbose = false;

    private Logger() {}

    public static Logger getInstance() {
        return instance;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void logCarFinish(double reward) {
        if (verbose) {
            System.out.println("Car finished with reward: " + reward);
        }
    }

    public void logEpisodeStats(int episode, double episodeTime, int carsFinished, int totalCars, 
                              double averageReward, double bestReward, double worstReward, double epsilon, double maxQ, double loss) {
        System.out.println("\n=== Episode " + episode + " Statistics ===");
        System.out.println("Time: " + String.format("%.2f", episodeTime) + "s");
        System.out.println("Cars completed: " + carsFinished + "/" + totalCars);
        System.out.println("Average Reward: " + String.format("%.2f", averageReward));
        System.out.println("Best Reward: " + String.format("%.2f", bestReward));
        System.out.println("Worst Reward: " + String.format("%.2f", worstReward));
        System.out.println("Epsilon: " + String.format("%.4f", epsilon));
        System.out.println("Max Q: " + String.format("%.2f", maxQ));
        System.out.println("Loss: " + String.format("%.6f", loss));
        System.out.println("================================\n");
    }
}