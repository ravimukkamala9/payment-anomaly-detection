package com.anomalydetection.detectors;

import com.anomalydetection.utils.Metrics;

import java.util.*;

/**
 * From-scratch Isolation Forest, port-equivalent of sklearn.ensemble.IsolationForest
 * as used in detectors/isolation_forest.py.
 *
 * Algorithm:
 *  - Build n_estimators random isolation trees. Each tree is built on a bootstrap-free
 *    random sub-sample of size max_samples = min(256, n) (sklearn's "auto" default).
 *  - A tree recursively splits on a random feature with a random split value between
 *    the min/max of that feature in the current node's data, until max depth
 *    ceil(log2(max_samples)) is reached or the node has <= 1 point.
 *  - Anomaly score for a point = 2^(-E(h(x)) / c(n)), where E(h(x)) is the average
 *    path length across all trees (path length includes a c(size) correction term
 *    for the leaf's residual unsplit points), and c(n) is the standard average path
 *    length of an unsuccessful BST search normalization constant.
 *  - Points are ranked by score; the top `contamination` fraction (highest scores)
 *    are flagged as anomalies, matching sklearn's contamination-based threshold.
 */
public class IsolationForestDetector {

    private static class Node {
        boolean isLeaf;
        int size; // number of points at this leaf (for c(size) correction)
        int splitFeature;
        double splitValue;
        Node left, right;
    }

    private static class ITree {
        Node root;
        int maxDepth;
    }

    /** Average path length of unsuccessful search in a BST of n points (Isolation Forest c(n)). */
    private static double c(double n) {
        if (n <= 1) return 0.0;
        if (n == 2) return 1.0;
        return 2.0 * (Math.log(n - 1) + 0.5772156649) - (2.0 * (n - 1) / n);
    }

    private static Node buildTree(double[][] X, List<Integer> idx, int depth, int maxDepth, Random rng, int d) {
        Node node = new Node();
        if (depth >= maxDepth || idx.size() <= 1) {
            node.isLeaf = true;
            node.size = idx.size();
            return node;
        }
        // pick random feature with non-constant values
        int feature = -1;
        double minV = 0, maxV = 0;
        List<Integer> features = new ArrayList<>();
        for (int f = 0; f < d; f++) features.add(f);
        Collections.shuffle(features, rng);
        for (int f : features) {
            double mn = Double.POSITIVE_INFINITY, mx = Double.NEGATIVE_INFINITY;
            for (int i : idx) {
                double v = X[i][f];
                if (v < mn) mn = v;
                if (v > mx) mx = v;
            }
            if (mx > mn) {
                feature = f;
                minV = mn;
                maxV = mx;
                break;
            }
        }
        if (feature == -1) {
            node.isLeaf = true;
            node.size = idx.size();
            return node;
        }
        double splitValue = minV + rng.nextDouble() * (maxV - minV);
        List<Integer> leftIdx = new ArrayList<>();
        List<Integer> rightIdx = new ArrayList<>();
        for (int i : idx) {
            if (X[i][feature] < splitValue) leftIdx.add(i);
            else rightIdx.add(i);
        }
        if (leftIdx.isEmpty() || rightIdx.isEmpty()) {
            node.isLeaf = true;
            node.size = idx.size();
            return node;
        }
        node.isLeaf = false;
        node.splitFeature = feature;
        node.splitValue = splitValue;
        node.left = buildTree(X, leftIdx, depth + 1, maxDepth, rng, d);
        node.right = buildTree(X, rightIdx, depth + 1, maxDepth, rng, d);
        return node;
    }

    private static double pathLength(double[] x, Node node, int depth) {
        if (node.isLeaf) {
            return depth + c(node.size);
        }
        if (x[node.splitFeature] < node.splitValue) {
            return pathLength(x, node.left, depth + 1);
        } else {
            return pathLength(x, node.right, depth + 1);
        }
    }

    public static Map<String, Object> detect(double[][] X, int[] yTrue, double contamination,
                                               int nEstimators, long randomState) {
        long start = System.nanoTime();

        int n = X.length;
        int d = X[0].length;
        int maxSamples = Math.min(256, n);
        int maxDepth = (int) Math.ceil(Math.log(maxSamples) / Math.log(2));
        if (maxDepth < 1) maxDepth = 1;

        Random seedRng = new Random(randomState);
        List<ITree> trees = new ArrayList<>();
        for (int t = 0; t < nEstimators; t++) {
            Random treeRng = new Random(seedRng.nextLong());
            // random sub-sample without replacement of size maxSamples
            List<Integer> allIdx = new ArrayList<>();
            for (int i = 0; i < n; i++) allIdx.add(i);
            Collections.shuffle(allIdx, treeRng);
            List<Integer> sample = allIdx.subList(0, maxSamples);
            ITree tree = new ITree();
            tree.maxDepth = maxDepth;
            tree.root = buildTree(X, new ArrayList<>(sample), 0, maxDepth, treeRng, d);
            trees.add(tree);
        }

        double cN = c(maxSamples);
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            double avgPath = 0;
            for (ITree tree : trees) {
                avgPath += pathLength(X[i], tree.root, 0);
            }
            avgPath /= trees.size();
            scores[i] = Math.pow(2, -avgPath / cN); // higher = more anomalous
        }

        // contamination-based threshold: top `contamination` fraction flagged as anomalies
        double[] sortedScores = scores.clone();
        Arrays.sort(sortedScores);
        int nAnomExpected = (int) Math.round(contamination * n);
        if (nAnomExpected < 1) nAnomExpected = 1;
        if (nAnomExpected > n) nAnomExpected = n;
        double thresholdScore = sortedScores[n - nAnomExpected];

        int[] yPred = new int[n];
        for (int i = 0; i < n; i++) {
            yPred[i] = scores[i] >= thresholdScore ? 1 : 0;
        }

        double execTime = (System.nanoTime() - start) / 1e9;
        Map<String, Object> metrics = Metrics.computeMetrics(yTrue, yPred, execTime);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "Isolation Forest");
        result.put("predictions", yPred);
        result.put("scores", scores);
        result.put("threshold", contamination);
        result.put("metrics", metrics);
        return result;
    }
}
