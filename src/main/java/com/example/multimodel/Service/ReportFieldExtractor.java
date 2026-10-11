package com.example.multimodel.Service;

import com.example.multimodel.Records.ReportRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Service
public class ReportFieldExtractor {

    private static final String MODEL = "openai/gpt-oss-20b";
    private static final int MAX_CHARS = 24000;

    private static final String SYSTEM_PROMPT = """
        You extract structured data from a cardiology patient report. Reply with ONE JSON object only.
        Use exactly these keys. Use null for any value that is not stated in the report; never guess.
        Binary flags: 1 = present/yes/positive, 0 = absent/no/negative.
        Numbers only (no units).

        age, weight(kg), length(cm), sex(1=male,0=female), BMI(decimal),
        DM, HTN, current_smoker, ex_smoker, FH(family history of CAD), Obesity, CRF, CVA,
        Airway_disease, Thyroid_disease, CHF, DLP,
        BP(systolic mmHg), PR(pulse), Edema, Weak_Peripheral_Pulse, Lung_rales,
        Systolic_Murmur, Diastolic_Murmur, Typical_Chest_Pain, Dyspnea,
        Function_class(0-3), Atypical, Nonanginal, Exertional_CP, LowTH_Ang,
        Q_Wave, St_Elevation, St_Depression, Tinversion, LVH,
        BBB(0=none, 1=LBBB, 2=RBBB),
        FBS, CR(decimal), TG, LDL, HDL(decimal), BUN, ESR, HB(decimal), K(decimal), Na,
        WBC(cells/uL, e.g. 7500), Lymph(%), Neut(%), PLT(x1000),
        EF_TTE(%), Region_RWMA(0-4, number of regions with wall-motion abnormality),
        VHD(0=none,1=mild,2=moderate,3=severe)
        """;

    private final RestClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ReportFieldExtractor(@Value("${groq.api.key}") String apiKey) {
        this.client = RestClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @SuppressWarnings("unchecked")
    public ReportRequest extract(String reportText) {
        String text = reportText.length() > MAX_CHARS ? reportText.substring(0, MAX_CHARS) : reportText;

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", text)));

        try {
            Map<String, Object> res = client.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) res.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return mapper.readValue((String) message.get("content"), ReportRequest.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Field extraction failed");
        }
    }
}
