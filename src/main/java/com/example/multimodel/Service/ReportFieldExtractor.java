package com.example.multimodel.Service;

import com.example.multimodel.Records.ReportRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportFieldExtractor {

    private static final int MAX_CHARS = 8000;   // per chunk; fits an 8192-token context with the prompt

    private static final String SYSTEM_PROMPT = """
    Extract data from the cardiology patient report into ONE JSON object.
    Use EXACTLY this template: keep every key name unchanged, and replace each null with the value
    from the report (a plain number), or leave null if the report does not state it. Never guess.

    {"age":null,"weight":null,"length":null,"sex":null,"BMI":null,"DM":null,"HTN":null,
     "current_smoker":null,"ex_smoker":null,"FH":null,"Obesity":null,"CRF":null,"CVA":null,
     "Airway_disease":null,"Thyroid_disease":null,"CHF":null,"DLP":null,"BP":null,"PR":null,
     "Edema":null,"Weak_Peripheral_Pulse":null,"Lung_rales":null,"Systolic_Murmur":null,
     "Diastolic_Murmur":null,"Typical_Chest_Pain":null,"Dyspnea":null,"Function_class":null,
     "Atypical":null,"Nonanginal":null,"Exertional_CP":null,"LowTH_Ang":null,"Q_Wave":null,
     "St_Elevation":null,"St_Depression":null,"Tinversion":null,"LVH":null,"BBB":null,
     "FBS":null,"CR":null,"TG":null,"LDL":null,"HDL":null,"BUN":null,"ESR":null,"HB":null,
     "K":null,"Na":null,"WBC":null,"Lymph":null,"Neut":null,"PLT":null,"EF_TTE":null,
     "Region_RWMA":null,"VHD":null}

    Value rules (values must be plain numbers, no units, no text):
    - Yes/present/positive = 1, No/absent/negative = 0.
    - sex: male = 1, female = 0.
    - weight in kg, length (height) in cm, BP = systolic mmHg, PR = pulse per minute.
    - BBB: none = 0, LBBB = 1, RBBB = 2.
    - VHD: none = 0, mild = 1, moderate = 2, severe = 3.
    - Function_class: 0 to 3. Region_RWMA: number of regions with wall-motion abnormality, 0 to 4.
    - WBC in cells/uL (e.g. 7500), Lymph and Neut in percent, PLT in thousands (e.g. 250), EF_TTE in percent.
    - CR, HDL, HB, K, BMI may be decimals; everything else integers.
    """;

    private final RestClient client;
    private final String model;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ReportFieldExtractor(@Value("${ollama.url:http://localhost:11434}") String url,
                                @Value("${ollama.model:qwen2.5:7b-instruct}") String model) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMinutes(3));          // local models can be slow on CPU
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.model = model;
    }

    public ReportRequest extract(String reportText) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String chunk : chunk(reportText)) {
            Map<String, Object> part = callModel(chunk);
            part.forEach((k, v) -> { if (v != null) merged.putIfAbsent(k, v); });   // first value found wins
        }
        return mapper.convertValue(merged, ReportRequest.class);
    }

    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String page : text.split("(?=\\[Page \\d+)")) {
            if (cur.length() > 0 && cur.length() + page.length() > MAX_CHARS) {
                chunks.add(cur.toString());
                cur.setLength(0);
            }
            if (page.length() > MAX_CHARS) {
                for (int i = 0; i < page.length(); i += MAX_CHARS)
                    chunks.add(page.substring(i, Math.min(page.length(), i + MAX_CHARS)));
            } else {
                cur.append(page);
            }
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        return chunks;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callModel(String text) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "format", "json",
                "options", Map.of("temperature", 0, "num_ctx", 8192),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", text)));
        try {
            Map<String, Object> res = client.post().uri("/api/chat").body(body).retrieve().body(Map.class);
            Map<String, Object> message = (Map<String, Object>) res.get("message");
            return mapper.readValue((String) message.get("content"), Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Local model unavailable. Is Ollama running and the model pulled?");
        }
    }
}