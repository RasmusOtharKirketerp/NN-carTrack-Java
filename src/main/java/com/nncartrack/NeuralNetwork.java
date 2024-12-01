package com.nncartrack;

import java.util.*;

public class NeuralNetwork {
    private int inputSize = Config.INPUT_SIZE;
    private int hiddenSize = Config.HIDDEN_SIZE;
    private int outputSize = Config.OUTPUT_SIZE;
    private double learningRate = Config.LEARNING_RATE;
    
    private double[][] weightsInputHidden;
    private double[][] weightsHiddenOutput;
    
    private PrioritizedReplayMemory memory;
    private Random rand = new Random();
    private double epsilon;
    
    // Add new fields for metrics
    private double currentLoss = 0;
    private double maxQValue = 0;
    
    public NeuralNetwork() {
        weightsInputHidden = new double[inputSize][hiddenSize];
        weightsHiddenOutput = new double[hiddenSize][outputSize];
        memory = PrioritizedReplayMemory.getInstance();
        epsilon = Config.EPSILON_START;
        
        initializeWeights();
    }
    
    private void initializeWeights() {
        for (int i = 0; i < inputSize; i++)
            for (int j = 0; j < hiddenSize; j++)
                weightsInputHidden[i][j] = rand.nextGaussian() * 
                    Math.sqrt(Config.WEIGHT_INIT_STD / inputSize);
                
        for (int i = 0; i < hiddenSize; i++)
            for (int j = 0; j < outputSize; j++)
                weightsHiddenOutput[i][j] = rand.nextGaussian() * 
                    Math.sqrt(Config.WEIGHT_INIT_STD / hiddenSize);
    }
    
    public int selectAction(double[] state) {
        if (rand.nextDouble() < epsilon) {
            return rand.nextInt(outputSize);
        }
        double[] qValues = forward(state);
        return maxIndex(qValues);
    }
    
    public void train() {
        if (memory.size() < Config.BATCH_SIZE) return;
        
        List<PrioritizedReplayMemory.Experience> batch = memory.sample(Config.BATCH_SIZE);
        List<Integer> sampledIndices = memory.getSampledIndices();
        List<Double> tdErrors = new ArrayList<>();
        
        // Get current beta for importance sampling
        double beta = memory.getBeta();
        
        // Calculate importance sampling weights
        double maxWeight = 0.0;
        double[] weights = new double[batch.size()];
        double sumPriorities = memory.getSumPriorities();
        
        for (int i = 0; i < batch.size(); i++) {
            // Calculate importance sampling weight for this experience
            double priority = memory.getPriority(sampledIndices.get(i));
            double prob = priority / sumPriorities;
            weights[i] = Math.pow(prob * batch.size(), -beta);
            maxWeight = Math.max(maxWeight, weights[i]);
        }
        
        // Normalize weights
        for (int i = 0; i < weights.length; i++) {
            weights[i] = weights[i] / maxWeight;
        }
        
        double totalLoss = 0.0;
        double maxQValue = Double.NEGATIVE_INFINITY;
        
        for (int i = 0; i < batch.size(); i++) {
            PrioritizedReplayMemory.Experience exp = batch.get(i);
            double[] currentQ = forward(exp.state);
            double[] targetQ = currentQ.clone();
            
            if (exp.done) {
                targetQ[exp.action] = exp.reward;
            } else {
                double[] nextQ = forward(exp.nextState);
                targetQ[exp.action] = exp.reward + Config.GAMMA * max(nextQ);
            }
            
            double tdError = 0.0;
            for (int j = 0; j < outputSize; j++) {
                // Apply importance sampling weight to the error
                tdError += weights[i] * Math.pow(targetQ[j] - currentQ[j], 2);
                totalLoss += Math.pow(targetQ[j] - currentQ[j], 2);
                maxQValue = Math.max(maxQValue, currentQ[j]);
            }
            tdError = Math.sqrt(tdError);
            
            tdErrors.add(tdError);
            
            // Apply importance sampling weight to the backpropagation
            for (int j = 0; j < outputSize; j++) {
                targetQ[j] = currentQ[j] + weights[i] * (targetQ[j] - currentQ[j]);
            }
            
            backpropagate(exp.state, targetQ);
        }
        
        currentLoss = totalLoss / batch.size();
        this.maxQValue = maxQValue;
        
        memory.updatePriorities(sampledIndices, tdErrors);
    }
    
    // Helper methods...
    
    private int maxIndex(double[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) maxIndex = i;  // Fix comparison
        }
        return maxIndex;
    }
    
    // Fix the max method to return the maximum value
    private double max(double[] array) {
        double maxVal = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxVal) maxVal = array[i];  // Corrected from maxVal = i
        }
        return maxVal;
    }
    
    // Keep existing forward() and backpropagate() methods...
    // ...existing code...
    // Forward propagation
    public double[] forward(double[] inputs) {
        // Hidden layer activations
        double[] hidden = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            double sum = 0;
            for (int j = 0; j < inputSize; j++) {
                sum += inputs[j] * weightsInputHidden[j][i];
            }
            hidden[i] = sigmoid(sum);
        }

        // Output layer activations
        double[] outputs = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            double sum = 0;
            for (int j = 0; j < hiddenSize; j++) {
                sum += hidden[j] * weightsHiddenOutput[j][i];
            }
            outputs[i] = sigmoid(sum);
        }

        return outputs;
    }

    // Activation function (Sigmoid)
    private double sigmoid(double x) {
        return 1 / (1 + Math.exp(-x));
    }

    // Derivative of the sigmoid function
    private double sigmoidDerivative(double x) {
        return x * (1 - x);
    }

    // Backpropagation
    private void backpropagate(double[] inputs, double[] target) {
        // Forward pass to get activations
        double[] hidden = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            double sum = 0;
            for (int j = 0; j < inputSize; j++) {
                sum += inputs[j] * weightsInputHidden[j][i];
            }
            hidden[i] = sigmoid(sum);
        }

        double[] outputs = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            double sum = 0;
            for (int j = 0; j < hiddenSize; j++) {
                sum += hidden[j] * weightsHiddenOutput[j][i];
            }
            outputs[i] = sigmoid(sum);
        }

        // Calculate output layer gradients
        double[] outputGradients = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            outputGradients[i] = (target[i] - outputs[i]) * sigmoidDerivative(outputs[i]);
        }

        // Hidden layer gradients
        double[] hiddenGradients = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            double sum = 0;
            for (int j = 0; j < outputSize; j++) {
                sum += outputGradients[j] * weightsHiddenOutput[i][j];
            }
            hiddenGradients[i] = sum * sigmoidDerivative(hidden[i]);
        }

        // Update weights Hidden to Output
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                double delta = learningRate * outputGradients[j] * hidden[i];
                weightsHiddenOutput[i][j] += delta;
            }
        }

        // Update weights Input to Hidden
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                double delta = learningRate * hiddenGradients[j] * inputs[i];
                weightsInputHidden[i][j] += delta;
            }
        }
    }

    public void trainWithReward(double[] state, double reward, double[] nextState, boolean done) {
        double priority = Math.abs(reward);
        PrioritizedReplayMemory.Experience experience = new PrioritizedReplayMemory.Experience(
            state, selectAction(state), reward, nextState, done);
        memory.add(experience, priority);
        
        train();
    }

    // Add new method for episode end
    public void onEpisodeEnd() {
        // Decay epsilon only once per episode
        epsilon = Math.max(Config.EPSILON_MIN, epsilon * Config.EPSILON_DECAY);
    }

    // Add getters for metrics
    public double getCurrentLoss() { return currentLoss; }
    public double getMaxQValue() { return maxQValue; }
    
    public double getEpsilon() {
        return epsilon;
    }
}
