package com.example.multimodel.Service;

import com.example.multimodel.Entities.Report;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Input-sensitivity range for each prediction. We re-run the models on many slightly perturbed copies of the
 * patient (lab/vital values vary by typical measurement error; a few yes/no findings are misrecorded) and report the
 * 5th-95th percentile of the resulting probabilities. This is NOT a statistical confidence interval; it shows how
 * fragile the estimate is to small input errors.
 */
@Service
public class UncertaintyService {

    public record Band(double low, double median, double high) {}

    // order must match models/features.json (55 features)
    private static final String[] F = {"Age", "Weight", "Length", "Sex", "BMI", "DM", "HTN", "Current Smoker", "EX-Smoker", "FH",
            "Obesity", "CRF", "CVA", "Airway disease", "Thyroid Disease", "CHF", "DLP", "BP", "PR", "Edema",
            "Weak Peripheral Pulse", "Lung rales", "Systolic Murmur", "Diastolic Murmur", "Typical Chest Pain", "Dyspnea",
            "Function Class", "Atypical", "Nonanginal", "Exertional CP", "LowTH Ang", "Q Wave", "St Elevation", "St Depression",
            "Tinversion", "LVH", "Poor R Progression", "FBS", "CR", "TG", "LDL", "HDL", "BUN", "ESR", "HB", "K", "Na", "WBC",
            "Lymph", "Neut", "PLT", "EF-TTE", "Region RWMA", "VHD", "BBB_N", "BBB_RBBB"};

    // relative standard deviation of a plausible measurement/recording error
    private static final Map<String, Double> REL_SD = Map.ofEntries(
            Map.entry("Weight", 0.01), Map.entry("BMI", 0.02), Map.entry("BP", 0.04), Map.entry("PR", 0.05),
            Map.entry("FBS", 0.05), Map.entry("CR", 0.08), Map.entry("TG", 0.08), Map.entry("LDL", 0.06),
            Map.entry("HDL", 0.06), Map.entry("BUN", 0.08), Map.entry("ESR", 0.15), Map.entry("HB", 0.03),
            Map.entry("K", 0.03), Map.entry("Na", 0.01), Map.entry("WBC", 0.08), Map.entry("Lymph", 0.08),
            Map.entry("Neut", 0.08), Map.entry("PLT", 0.06), Map.entry("EF-TTE", 0.05));

    private static final Set<String> FLIP = Set.of("DM", "HTN", "Current Smoker", "EX-Smoker", "FH", "Obesity", "CRF", "CVA",
            "Airway disease", "Thyroid Disease", "CHF", "DLP", "Edema", "Weak Peripheral Pulse", "Lung rales", "Systolic Murmur",
            "Diastolic Murmur", "Typical Chest Pain", "Dyspnea", "Atypical", "Nonanginal", "Exertional CP", "LowTH Ang",
            "Q Wave", "St Elevation", "St Depression", "Tinversion", "LVH");

    private static final double FLIP_PROB = 0.03;
    private static final int N = 200;
    private static final String[] TARGETS = {"CAD", "LAD", "LCX", "RCA"};

    private final ModelService modelService;

    public UncertaintyService(ModelService modelService) {
        this.modelService = modelService;
    }

    public Map<String, Band> bands(Report report) {
        try {
            float[] x = modelService.toVector(report);
            if (x.length != F.length) return Map.of();           // feature layout changed: skip rather than guess
            Random rnd = new Random(7);                           // deterministic for the same patient
            float[][] rows = new float[N][];
            for (int n = 0; n < N; n++) {
                float[] row = x.clone();
                for (int k = 0; k < F.length; k++) {
                    Double sd = REL_SD.get(F[k]);
                    if (sd != null) row[k] = (float) Math.max(0, x[k] * (1 + sd * rnd.nextGaussian()));
                    else if (FLIP.contains(F[k]) && rnd.nextDouble() < FLIP_PROB) row[k] = 1 - x[k];
                }
                rows[n] = row;
            }
            Map<String, Band> out = new LinkedHashMap<>();
            for (String t : TARGETS) {
                double[] p = modelService.predictBatch(t, rows).clone();
                Arrays.sort(p);
                out.put(t, new Band(p[(int) (0.05 * N)], p[N / 2], p[(int) (0.95 * N) - 1]));
            }
            return out;
        } catch (Exception e) {
            return Map.of();                                      // never break the main result because of this extra
        }
    }
}
