package com.example.multimodel.Service;

import com.example.multimodel.Entities.Report;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Local explanations for the calibrated ONNX models using permutation-sampling Shapley values
 * (SHAP family, model-agnostic). Each sample walks features from a random real background patient
 * to the current patient, so contributions add up to: probability - average background probability.
 */
@Service
public class ExplanationService {

    public record Contribution(String feature, double value, double contribution, String direction) {}

    public record TargetExplanation(double baseProbability, double probability, List<Contribution> contributions) {}

    private record Background(List<String> features, List<List<Double>> background) {}

    private static final List<String> TARGETS = List.of("CAD", "LAD", "LCX", "RCA");
    private static final int PERMUTATIONS = 60;

    private final ModelService modelService;
    private String[] features;
    private float[][] background;

    public ExplanationService(ModelService modelService) {
        this.modelService = modelService;
    }

    @PostConstruct
    void load() {
        try (var in = new ClassPathResource("models/background.json").getInputStream()) {
            Background b = JsonMapper.builder().build().readValue(in, Background.class);
            features = b.features().toArray(new String[0]);
            background = new float[b.background().size()][];
            for (int i = 0; i < background.length; i++) {
                List<Double> row = b.background().get(i);
                background[i] = new float[row.size()];
                for (int j = 0; j < row.size(); j++) background[i][j] = row.get(j).floatValue();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not load models/background.json: " + e, e);
        }
    }

    public Map<String, TargetExplanation> explain(Report report, int topK) {
        try {
            float[] x = modelService.toVector(report);
            Map<String, TargetExplanation> out = new LinkedHashMap<>();
            for (String target : TARGETS) out.put(target, explainTarget(target, x, topK));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Explanation failed: " + e, e);
        }
    }

    private TargetExplanation explainTarget(String target, float[] x, int topK) throws Exception {
        int f = x.length;
        Random rnd = new Random(42);                       // deterministic output for the same input
        float[][] rows = new float[PERMUTATIONS * f + 1 + PERMUTATIONS][];
        int[][] perms = new int[PERMUTATIONS][];
        int r = 0;

        for (int m = 0; m < PERMUTATIONS; m++) {
            int[] perm = new int[f];
            for (int i = 0; i < f; i++) perm[i] = i;
            for (int i = f - 1; i > 0; i--) {              // Fisher-Yates shuffle
                int j = rnd.nextInt(i + 1);
                int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
            }
            perms[m] = perm;
            float[] cur = background[rnd.nextInt(background.length)].clone();
            rows[PERMUTATIONS * f + 1 + m] = cur.clone();  // baseline row of this sample
            for (int k : perm) {
                cur[k] = x[k];
                rows[r++] = cur.clone();
            }
        }
        rows[PERMUTATIONS * f] = x;                        // the real patient

        double[] p = modelService.predictBatch(target, rows);
        double pFull = p[PERMUTATIONS * f];
        double[] phi = new double[f];
        double baseSum = 0;
        int idx = 0;
        for (int m = 0; m < PERMUTATIONS; m++) {
            double prev = p[PERMUTATIONS * f + 1 + m];
            baseSum += prev;
            for (int k : perms[m]) {
                phi[k] += p[idx] - prev;
                prev = p[idx];
                idx++;
            }
        }

        List<Contribution> all = new ArrayList<>();
        for (int k = 0; k < f; k++) {
            double c = phi[k] / PERMUTATIONS;
            if (c != 0.0) all.add(new Contribution(features[k], x[k], c, c > 0 ? "increases risk" : "decreases risk"));
        }
        all.sort(Comparator.comparingDouble((Contribution c) -> Math.abs(c.contribution())).reversed());
        return new TargetExplanation(baseSum / PERMUTATIONS, pFull, all.subList(0, Math.min(topK, all.size())));
    }
}
