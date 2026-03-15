package com.nncartrack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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
    private double sumPriorities;

    private PrioritizedReplayMemory() {
        this.capacity = com.nncartrack.Config.MEMORY_SIZE;
        this.alpha = com.nncartrack.Config.PER_ALPHA;
        this.epsilon = com.nncartrack.Config.PER_EPSILON;
        this.beta = com.nncartrack.Config.PER_BETA_START;
        this.betaIncrement = com.nncartrack.Config.PER_BETA_INCREMENT;
        this.experiences = new ArrayList<>(capacity);
        this.priorities = new ArrayList<>(capacity);
        this.sumPriorities = 0.0;
    }

    public static PrioritizedReplayMemory getInstance() {
        return instance;
    }

    public synchronized void add(Experience experience, double priority) {
        if (experiences.size() >= capacity) {
            sumPriorities -= priorities.get(0);
            experiences.remove(0);
            priorities.remove(0);
        }
        experiences.add(experience);
        double adjustedPriority = Math.pow(priority + epsilon, alpha);
        priorities.add(adjustedPriority);
        sumPriorities += adjustedPriority;
    }

    public synchronized List<Experience> sample(int batchSize) {
        sampledIndices.clear();
        List<Experience> batch = new ArrayList<>(batchSize);
        if (experiences.isEmpty()) {
            return batch;
        }
        
        double total = sumPriorities;
        if (total <= 0.0) {
            total = priorities.size();
            for (int i = 0; i < priorities.size(); i++) {
                priorities.set(i, 1.0);
            }
            sumPriorities = total;
        }

        double[] cumulativeSum = new double[priorities.size()];
        double running = 0.0;
        for (int i = 0; i < priorities.size(); i++) {
            running += priorities.get(i) / total;
            cumulativeSum[i] = running;
        }
        cumulativeSum[cumulativeSum.length - 1] = 1.0;

        for (int i = 0; i < batchSize; i++) {
            double randVal = ThreadLocalRandom.current().nextDouble();
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
            sumPriorities += priority - priorities.get(idx);
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
        return sumPriorities;
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
