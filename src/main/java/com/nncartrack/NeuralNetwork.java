package com.nncartrack;

import java.util.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    private int trainBatchCounter = 0;
    private Logger logger = Logger.getInstance();
    private final boolean inferenceOnly = Config.isInferenceOnly();
    
    public NeuralNetwork() {
        weightsInputHidden = new double[inputSize][hiddenSize];
        weightsHiddenOutput = new double[hiddenSize][outputSize];
        memory = PrioritizedReplayMemory.getInstance();
        epsilon = inferenceOnly ? 0.0 : Config.EPSILON_START;

        if (inferenceOnly && loadModel(Config.MODEL_LOAD_FILE_PATH)) {
            // Loaded persisted weights.
        } else {
            initializeWeights();
        }
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
        if (inferenceOnly) return;
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
        double sumQValues = 0.0;
        int qCount = 0;
        int doneCount = 0;
        int[] actionCounts = new int[Config.OUTPUT_SIZE];
        
        for (int i = 0; i < batch.size(); i++) {
            PrioritizedReplayMemory.Experience exp = batch.get(i);
            if (exp.done) {
                doneCount++;
            }
            if (exp.action >= 0 && exp.action < actionCounts.length) {
                actionCounts[exp.action]++;
            }
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
                sumQValues += currentQ[j];
                qCount++;
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

        trainBatchCounter++;
        if (trainBatchCounter % Config.TRAIN_LOG_EVERY_N_BATCHES == 0) {
            double tdErrorSum = 0.0;
            double tdErrorMax = 0.0;
            for (double tdError : tdErrors) {
                tdErrorSum += tdError;
                tdErrorMax = Math.max(tdErrorMax, tdError);
            }
            double meanTdError = tdErrors.isEmpty() ? 0.0 : tdErrorSum / tdErrors.size();
            double meanQ = qCount == 0 ? 0.0 : sumQValues / qCount;
            double doneRatio = batch.isEmpty() ? 0.0 : doneCount / (double) batch.size();
            logger.logTrainingBatch(
                batch.size(),
                memory.size(),
                beta,
                sumPriorities,
                epsilon,
                currentLoss,
                this.maxQValue,
                meanQ,
                meanTdError,
                tdErrorMax,
                doneRatio,
                actionCounts
            );
        }
    }

    public void trainMultiple(int batches) {
        if (batches <= 0) {
            return;
        }
        for (int i = 0; i < batches; i++) {
            train();
        }
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

        // Output layer activations (linear for Q-values)
        double[] outputs = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            double sum = 0;
            for (int j = 0; j < hiddenSize; j++) {
                sum += hidden[j] * weightsHiddenOutput[j][i];
            }
            outputs[i] = sum;
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
            outputs[i] = sum;
        }

        // Linear output layer => derivative is 1
        double[] outputGradients = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            outputGradients[i] = (target[i] - outputs[i]);
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
        if (inferenceOnly) return;
        addExperience(state, reward, nextState, done);
        train();
    }

    public void addExperience(double[] state, double reward, double[] nextState, boolean done) {
        if (inferenceOnly) return;
        double priority = Math.abs(reward);
        PrioritizedReplayMemory.Experience experience = new PrioritizedReplayMemory.Experience(
            state, selectAction(state), reward, nextState, done);
        memory.add(experience, priority);
    }

    // Add new method for episode end
    public void onEpisodeEnd() {
        if (inferenceOnly) return;
        // Decay epsilon only once per episode
        epsilon = Math.max(Config.EPSILON_MIN, epsilon * Config.EPSILON_DECAY);
    }

    public boolean saveModel(String filePath) {
        Path path = Paths.get(filePath);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
                out.writeInt(inputSize);
                out.writeInt(hiddenSize);
                out.writeInt(outputSize);
                for (int i = 0; i < inputSize; i++) {
                    for (int j = 0; j < hiddenSize; j++) {
                        out.writeDouble(weightsInputHidden[i][j]);
                    }
                }
                for (int i = 0; i < hiddenSize; i++) {
                    for (int j = 0; j < outputSize; j++) {
                        out.writeDouble(weightsHiddenOutput[i][j]);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean loadModel(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int inInput = in.readInt();
            int inHidden = in.readInt();
            int inOutput = in.readInt();
            if (inInput != inputSize || inHidden != hiddenSize || inOutput != outputSize) {
                return false;
            }
            for (int i = 0; i < inputSize; i++) {
                for (int j = 0; j < hiddenSize; j++) {
                    weightsInputHidden[i][j] = in.readDouble();
                }
            }
            for (int i = 0; i < hiddenSize; i++) {
                for (int j = 0; j < outputSize; j++) {
                    weightsHiddenOutput[i][j] = in.readDouble();
                }
            }
            epsilon = 0.0;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Add getters for metrics
    public double getCurrentLoss() { return currentLoss; }
    public double getMaxQValue() { return maxQValue; }
    
    public double getEpsilon() {
        return epsilon;
    }
}
