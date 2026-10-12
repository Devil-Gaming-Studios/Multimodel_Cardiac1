package com.example.multimodel.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Chat about ONE assessment, answered by the local Ollama model. Nothing leaves this computer. */
@Service
public class ChatService {

    public record Msg(String role, String content) {}

    private static final String SYSTEM = """
            You are a careful assistant inside a cardiovascular risk research prototype. You explain the results below to a patient or clinician in plain, calm language.
            Rules:
            - Use ONLY the numbers in the PATIENT RESULTS section. Never invent values, never guess missing data.
            - The models estimate the probability that coronary stenosis is present. They do not measure severity and they are not a diagnosis.
            - LCX and RCA estimates are less reliable; say so when they come up.
            - Do not give medication names or doses, and do not tell the user to start, stop or change treatment. Suggest discussing options with their doctor.
            - If asked something outside these results, say you can only discuss this assessment.
            - If the user describes chest pain, fainting or an emergency, tell them to seek emergency care now.
            - Keep answers short (under 150 words) unless asked for more detail.
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient client;
    private final String model;

    public ChatService(@Value("${ollama.url:http://localhost:11434}") String url,
                       @Value("${ollama.model:llama3.1:8b}") String model) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMinutes(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.model = model;
    }

    private Map<String, Object> buildBody(List<Msg> history, String context, boolean stream) {
        List<Map<String, String>> msgs = new ArrayList<>();
        String ctx = context == null ? "" : context.substring(0, Math.min(context.length(), 6000));
        msgs.add(Map.of("role", "system", "content", SYSTEM + "\nPATIENT RESULTS:\n" + ctx));
        int from = Math.max(0, history.size() - 12);                       // last 12 turns keep the prompt small
        for (Msg m : history.subList(from, history.size())) {
            if (m == null || m.content() == null) continue;
            String role = "assistant".equals(m.role()) ? "assistant" : "user";
            msgs.add(Map.of("role", role, "content", m.content().substring(0, Math.min(m.content().length(), 2000))));
        }
        return Map.of("model", model, "stream", stream,
                "options", Map.of("temperature", 0.3, "num_ctx", 8192), "messages", msgs);
    }

    /** Whole answer at once (kept for the old /chat endpoint). */
    public String reply(List<Msg> history, String context) {
        Map<String, Object> body = buildBody(history, context, false);
        try {
            Map<?, ?> res = client.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
            Object message = res == null ? null : res.get("message");
            Object content = message instanceof Map<?, ?> mm ? mm.get("content") : null;
            if (content == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty answer from local model");
            return content.toString().trim();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Local AI model is not available");
        }
    }

    /**
     * Token-by-token answer. Ollama sends one JSON object per line while it generates;
     * every piece of text is handed to onToken as soon as it arrives.
     * If onToken throws (e.g. the browser closed the connection) we stop reading, which closes the
     * connection to Ollama and makes it stop generating.
     */
    public void stream(List<Msg> history, String context, Consumer<String> onToken) {
        Map<String, Object> body = buildBody(history, context, true);
        try {
            client.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON).body(body)
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError())
                            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Local AI model is not available");
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                if (line.isBlank()) continue;
                                Map<?, ?> chunk = MAPPER.readValue(line, Map.class);
                                if (chunk.get("error") != null)
                                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, chunk.get("error").toString());
                                if (chunk.get("message") instanceof Map<?, ?> mm && mm.get("content") != null) {
                                    String tok = mm.get("content").toString();
                                    if (!tok.isEmpty()) onToken.accept(tok);
                                }
                                if (Boolean.TRUE.equals(chunk.get("done"))) break;
                            }
                        }
                        return null;
                    });
        } catch (ResponseStatusException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Local AI model is not available");
        }
    }
}
