package com.nncartrack;

import java.util.*;

public class PrioritizedReplayMemory {
    private static PrioritizedReplayMemory instance = new PrioritizedReplayMemory();
    private List<Experience> experiences;
    private List<Double> priorities;
    private int capacity;
    private double alpha;
    private double epsilon;
    private double beta;
    private double betaIncrement;
    private List<Integer> sampledIndices = new ArrayList<>();  // To store sampled indices

    private PrioritizedReplayMemory() {
        this.capacity = com.nncartrack.Config.MEMORY_SIZE;
        this.alpha = com.nncartrack.Config.PER_ALPHA;
        this.epsilon = com.nncartrack.Config.PER_EPSILON;
        this.beta = com.nncartrack.Config.PER_BETA_START;
        this.betaIncrement = com.nncartrack.Config.PER_BETA_INCREMENT;
        this.experiences = new ArrayList<>();
        this.priorities = new ArrayList<>();
    }

    public static PrioritizedReplayMemory getInstance() {
        return instance;
    }

    public synchronized void add(Experience experience, double priority) {
        if (experiences.size() >= capacity) {
            experiences.remove(0);
            priorities.remove(0);
        }
        experiences.add(experience);
        priorities.add(Math.pow(priority + epsilon, alpha));
    }

    public synchronized List<Experience> sample(int batchSize) {
        sampledIndices.clear();
        List<Experience> batch = new ArrayList<>();
        if (experiences.isEmpty()) {
            return batch;
        }
        
        // Calculate sampling probabilities
        double total = priorities.stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) {
            // Fallback to uniform probabilities in degenerate cases.
            total = priorities.size();
            for (int i = 0; i < priorities.size(); i++) {
                priorities.set(i, 1.0);
            }
        }
        final double normalizationTotal = total;
        double[] probabilities = priorities.stream()
            .mapToDouble(p -> p / normalizationTotal)
            .toArray();
        
        // Create cumulative sum for sampling
        double[] cumulativeSum = new double[probabilities.length];
        cumulativeSum[0] = probabilities[0];
        for (int i = 1; i < probabilities.length; i++) {
            cumulativeSum[i] = cumulativeSum[i-1] + probabilities[i];
        }

        // Sample experiences
        for (int i = 0; i < batchSize; i++) {
            double randVal = Math.random();
            int index = Arrays.binarySearch(cumulativeSum, randVal);
            if (index < 0) {
                index = -index - 1;
            }
            if (index >= experiences.size()) {
                index = experiences.size() - 1;
            }
            batch.add(experiences.get(index));
            sampledIndices.add(index);
        }

        // Increment beta
        beta = Math.min(1.0, beta + betaIncrement);

        return batch;
    }

    public synchronized double getBeta() {
        return beta;
    }

    public synchronized List<Integer> getSampledIndices() {
        return new ArrayList<>(sampledIndices);
    }

    public synchronized int size() {
        return experiences.size();
    }

    // Update priorities based on sampled indices
    public synchronized void updatePriorities(List<Integer> indices, List<Double> newPriorities) {
        for (int i = 0; i < indices.size(); i++) {
            int idx = indices.get(i);
            double priority = Math.pow(newPriorities.get(i) + epsilon, alpha);
            priorities.set(idx, priority);
        }
    }

    public Experience get(int index) {
        return experiences.get(index);
    }

    public synchronized double getPriority(int index) {
        return priorities.get(index);
    }
    
    public synchronized double getSumPriorities() {
        return priorities.stream().mapToDouble(Double::doubleValue).sum();
    }

    // Experience inner class
    public static class Experience {
        double[] state;
        int action;
        double reward;
        double[] nextState;
        boolean done;

        public Experience(double[] state, int action, double reward, double[] nextState, boolean done) {
            this.state = state.clone();
            this.action = action;
            this.reward = reward;
            this.nextState = nextState.clone();
            this.done = done;
        }
    }
}
