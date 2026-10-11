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
