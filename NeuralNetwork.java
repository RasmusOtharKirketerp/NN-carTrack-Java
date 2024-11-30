import java.util.*;

public class NeuralNetwork {
    private int inputSize = 5;
    private int hiddenSize = 24;  // Increased for more capacity
    private int outputSize = 2;
    private double learningRate = 0.001;
    
    private double[][] weightsInputHidden;
    private double[][] weightsHiddenOutput;
    
    private List<Experience> replayMemory;
    private Random rand = new Random();
    private double epsilon;
    
    private static class Experience {
        double[] state;
        int action;
        double reward;
        double[] nextState;
        boolean done;
        
        Experience(double[] state, int action, double reward, double[] nextState, boolean done) {
            this.state = state.clone();
            this.action = action;
            this.reward = reward;
            this.nextState = nextState.clone();
            this.done = done;
        }
    }
    
    public NeuralNetwork() {
        weightsInputHidden = new double[inputSize][hiddenSize];
        weightsHiddenOutput = new double[hiddenSize][outputSize];
        replayMemory = new ArrayList<>();
        epsilon = Config.EPSILON_START;
        
        initializeWeights();
    }
    
    private void initializeWeights() {
        for (int i = 0; i < inputSize; i++)
            for (int j = 0; j < hiddenSize; j++)
                weightsInputHidden[i][j] = rand.nextGaussian() * Math.sqrt(2.0 / inputSize);
                
        for (int i = 0; i < hiddenSize; i++)
            for (int j = 0; j < outputSize; j++)
                weightsHiddenOutput[i][j] = rand.nextGaussian() * Math.sqrt(2.0 / hiddenSize);
    }
    
    public int selectAction(double[] state) {
        if (rand.nextDouble() < epsilon) {
            return rand.nextInt(outputSize);
        }
        double[] qValues = forward(state);
        return maxIndex(qValues);
    }
    
    public void addExperience(double[] state, int action, double reward, double[] nextState, boolean done) {
        if (replayMemory.size() >= Config.MEMORY_SIZE) {
            replayMemory.remove(0);
        }
        replayMemory.add(new Experience(state, action, reward, nextState, done));
    }
    
    public void train() {
        if (replayMemory.size() < Config.BATCH_SIZE) return;
        
        // Sample random batch
        List<Experience> batch = sampleBatch();
        
        for (Experience exp : batch) {
            double[] currentQ = forward(exp.state);
            double[] targetQ = currentQ.clone();
            
            if (exp.done) {
                targetQ[exp.action] = exp.reward;
            } else {
                double[] nextQ = forward(exp.nextState);
                targetQ[exp.action] = exp.reward + Config.GAMMA * max(nextQ);
            }
            
            backpropagate(exp.state, targetQ);
        }
        
        // Decay epsilon
        epsilon = Math.max(Config.EPSILON_MIN, epsilon * Config.EPSILON_DECAY);
    }
    
    // Helper methods...
    private List<Experience> sampleBatch() {
        List<Experience> batch = new ArrayList<>();
        for (int i = 0; i < Config.BATCH_SIZE; i++) {
            int index = rand.nextInt(replayMemory.size());
            batch.add(replayMemory.get(index));
        }
        return batch;
    }
    
    private int maxIndex(double[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) maxIndex = i;
        }
        return maxIndex;
    }
    
    private double max(double[] array) {
        double maxVal = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxVal) maxVal = array[i];
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

    public void trainWithReward(double[] state, double reward) {
        double[] outputs = forward(state);
        double[] targetOutputs = outputs.clone();
        
        // Modify target outputs based on reward
        for (int i = 0; i < outputSize; i++) {
            if (reward > 0) {
                // For positive rewards, reinforce the current output
                targetOutputs[i] = outputs[i] + (reward * learningRate * (1 - outputs[i]));
            } else {
                // For negative rewards, push output in opposite direction
                targetOutputs[i] = outputs[i] + (reward * learningRate * outputs[i]);
            }
            // Ensure outputs stay in [0,1] range
            targetOutputs[i] = Math.max(0, Math.min(1, targetOutputs[i]));
        }
        
        // Update weights using backpropagation
        backpropagate(state, targetOutputs);
    }
    
    public double getEpsilon() {
        return epsilon;
    }
}
