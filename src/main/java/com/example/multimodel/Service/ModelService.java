package com.example.multimodel.Service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.multimodel.Entities.Report;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ModelService {

    public record TargetOutput(double probability, boolean predictedPositive, double threshold, boolean lowConfidence) {}

    // order must match features.json exactly (55 inputs)
    private static final String[] FEATURES = {"Age","Weight","Length","Sex","BMI","DM","HTN","Current Smoker","EX-Smoker","FH",
            "Obesity","CRF","CVA","Airway disease","Thyroid Disease","CHF","DLP","BP","PR","Edema","Weak Peripheral Pulse",
            "Lung rales","Systolic Murmur","Diastolic Murmur","Typical Chest Pain","Dyspnea","Function Class","Atypical",
            "Nonanginal","Exertional CP","LowTH Ang","Q Wave","St Elevation","St Depression","Tinversion","LVH",
            "Poor R Progression","FBS","CR","TG","LDL","HDL","BUN","ESR","HB","K","Na","WBC","Lymph","Neut","PLT",
            "EF-TTE","Region RWMA","VHD","BBB_N","BBB_RBBB"};

    private static final Map<String, Double> THRESHOLDS = Map.of("CAD", 0.47, "LAD", 0.40, "LCX", 0.34, "RCA", 0.27);
    private static final Set<String> LOW_CONFIDENCE = Set.of("LCX", "RCA");

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final Map<String, OrtSession> sessions = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        try {
            for (String t : THRESHOLDS.keySet()) {
                byte[] bytes = new ClassPathResource("models/" + t + ".onnx").getInputStream().readAllBytes();
                sessions.put(t, env.createSession(bytes, new OrtSession.SessionOptions()));
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new IllegalStateException("Model load failed: " + e, e);
        }
    }

    public Map<String, TargetOutput> ModelOutput(Report r) throws OrtException {
        float[][] x = { toVector(r) };
        Map<String, TargetOutput> out = new LinkedHashMap<>();
        try (OnnxTensor in = OnnxTensor.createTensor(env, x)) {
            for (var e : sessions.entrySet()) {
                String name = e.getValue().getInputNames().iterator().next();
                try (OrtSession.Result res = e.getValue().run(Map.of(name, in))) {
                    float[][] probs = (float[][]) res.get(1).getValue();   // output 1 = probabilities [n][2]
                    double p = probs[0][1];
                    double th = THRESHOLDS.get(e.getKey());
                    out.put(e.getKey(), new TargetOutput(p, p >= th, th, LOW_CONFIDENCE.contains(e.getKey())));
                }
            }
        }
        return out;
    }

    private float[] toVector(Report r) {
        float[] v = new float[FEATURES.length];
        int i = 0;
        v[i++] = r.getAge();            v[i++] = r.getWeight();         v[i++] = r.getLength();
        v[i++] = r.getSex();            v[i++] = r.getBMI().floatValue(); v[i++] = r.getDM();
        v[i++] = r.getHTN();            v[i++] = r.getCurrent_smoker(); v[i++] = r.getEx_smoker();
        v[i++] = r.getFH();             v[i++] = r.getObesity();        v[i++] = r.getCRF();
        v[i++] = r.getCVA();            v[i++] = r.getAirway_disease(); v[i++] = r.getThyroid_disease();
        v[i++] = r.getCHF();            v[i++] = r.getDLP();            v[i++] = r.getBP();
        v[i++] = r.getPR();             v[i++] = r.getEdema();          v[i++] = r.getWeak_Peripheral_Pulse();
        v[i++] = r.getLung_rales();     v[i++] = r.getSystolic_Murmur(); v[i++] = r.getDiastolic_Murmur();
        v[i++] = r.getTypical_Chest_Pain(); v[i++] = r.getDyspnea();    v[i++] = r.getFunction_class();
        v[i++] = r.getAtypical();       v[i++] = r.getNonanginal();     v[i++] = r.getExertional_CP();
        v[i++] = r.getLowTH_Ang();      v[i++] = r.getQ_Wave();         v[i++] = r.getSt_Elevation();
        v[i++] = r.getSt_Depression();  v[i++] = r.getTinversion();     v[i++] = r.getLVH();
        v[i++] = 0f;                    // Poor R Progression: not in your entity yet (add it, 0/1)
        v[i++] = r.getFBS();            v[i++] = r.getCR().floatValue(); v[i++] = r.getTG();
        v[i++] = r.getLDL();            v[i++] = r.getHDL().floatValue(); v[i++] = r.getBUN();
        v[i++] = r.getESR();            v[i++] = r.getHB().floatValue(); v[i++] = r.getK().floatValue();
        v[i++] = r.getNa();             v[i++] = r.getWBC();            v[i++] = r.getLymph();
        v[i++] = r.getNeut();           v[i++] = r.getPLT();            v[i++] = r.getEF_TTE();
        v[i++] = r.getRegion_RWMA();    v[i++] = r.getVHD();
        v[i++] = r.getBBB() == 0 ? 1f : 0f;   // BBB_N    (0=N, 1=LBBB, 2=RBBB; LBBB is the dropped baseline)
        v[i++] = r.getBBB() == 2 ? 1f : 0f;   // BBB_RBBB
        return v;
    }
}
