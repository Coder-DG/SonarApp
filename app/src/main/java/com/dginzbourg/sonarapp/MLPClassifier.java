package com.dginzbourg.sonarapp;

import com.google.gson.Gson;

class MLPClassifier {

    private enum Activation {IDENTITY, LOGISTIC, RELU, TANH, SOFTMAX}

    private Activation hidden;
    private Activation output;
    private double[][] network;
    private double[][][] weights;
    private double[][] bias;

    public MLPClassifier(String hidden, String output, int[] layers, double[][][] weights, double[][] bias) {
        this.hidden = Activation.valueOf(hidden.toUpperCase());
        this.output = Activation.valueOf(output.toUpperCase());
        this.network = new double[layers.length + 1][];
        for (int i = 0, l = layers.length; i < l; i++) {
            this.network[i + 1] = new double[layers[i]];
        }
        this.weights = weights;
        this.bias = bias;
    }

    public MLPClassifier(String hidden, String output, int neurons, double[][][] weights, double[][] bias) {
        this(hidden, output, new int[]{neurons}, weights, bias);
    }

    public static double getDistance(int prediction) {
        // Prediction is in cm
        return prediction / 10.0;
    }

    private double[] compute(Activation activation, double[] v) {
        switch (activation) {
            case LOGISTIC:
                for (int i = 0, l = v.length; i < l; i++) {
                    v[i] = 1. / (1. + Math.exp(-v[i]));
                }
                break;
            case RELU:
                for (int i = 0, l = v.length; i < l; i++) {
                    v[i] = Math.max(0, v[i]);
                }
                break;
            case TANH:
                for (int i = 0, l = v.length; i < l; i++) {
                    v[i] = Math.tanh(v[i]);
                }
                break;
            case SOFTMAX:
                double max = Double.NEGATIVE_INFINITY;
                for (double x : v) {
                    if (x > max) {
                        max = x;
                    }
                }
                for (int i = 0, l = v.length; i < l; i++) {
                    v[i] = Math.exp(v[i] - max);
                }
                double sum = 0.;
                for (double x : v) {
                    sum += x;
                }
                for (int i = 0, l = v.length; i < l; i++) {
                    v[i] /= sum;
                }
                break;
        }
        return v;
    }

    public int predict(double[] neurons) {
        this.network[0] = neurons;

        for (int i = 0; i < this.network.length - 1; i++) {
            for (int j = 0; j < this.network[i + 1].length; j++) {
                this.network[i + 1][j] = this.bias[i][j];
                for (int l = 0; l < this.network[i].length; l++) {
                    this.network[i + 1][j] += this.network[i][l] * this.weights[i][l][j];
                }
            }
            if ((i + 1) < (this.network.length - 1)) {
                this.network[i + 1] = this.compute(this.hidden, this.network[i + 1]);
            }
        }
        this.network[this.network.length - 1] = this.compute(this.output, this.network[this.network.length - 1]);

        if (this.network[this.network.length - 1].length == 1) {
            if (this.network[this.network.length - 1][0] > .5) {
                return 1;
            }
            return 0;
        } else {
            int classIdx = 0;
            for (int i = 0; i < this.network[this.network.length - 1].length; i++) {
                classIdx = this.network[this.network.length - 1][i] > this.network[this.network.length - 1][classIdx] ? i : classIdx;
            }
            return classIdx;
        }

    }

    public static int classify(double[] features, double[][][] weights, double[][] bias) {
        // Parameters:
        int[] layers = {800, 34};

        // Prediction:
        MLPClassifier clf = new MLPClassifier("relu", "softmax", layers, weights, bias);
        return clf.predict(features);
    }
}