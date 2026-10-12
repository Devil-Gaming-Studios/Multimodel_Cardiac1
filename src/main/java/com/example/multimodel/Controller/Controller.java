package com.example.multimodel.Controller;

import ai.onnxruntime.OrtException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.multimodel.Entities.Report;
import com.example.multimodel.Entities.Result;
import com.example.multimodel.Entities.User;
import com.example.multimodel.Records.ReportRequest;
import com.example.multimodel.Records.ResultResponse;
import com.example.multimodel.Service.ChatHistoryService;
import com.example.multimodel.Service.ChatService;
import com.example.multimodel.Service.ExplanationService;
import com.example.multimodel.Service.ReportService;
import com.example.multimodel.Service.TimelineService;
import com.example.multimodel.Service.UncertaintyService;
import com.example.multimodel.Service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;


@RestController
@CrossOrigin
public class Controller {
    @Autowired
    UserService userService;

    @Autowired
    ReportService reportService;

    @Autowired
    ExplanationService explanationService;

    @Autowired
    UncertaintyService uncertaintyService;

    @Autowired
    ChatService chatService;

    @Autowired
    ChatHistoryService chatHistory;

    @Autowired
    TimelineService timelineService;

    private static final ObjectMapper JSON = new ObjectMapper();

    public record AssessmentMeta(String label) {}

    public record ChatRequest(List<ChatService.Msg> messages, String context, Integer resultId) {}

    @GetMapping("/health")
    public ResponseEntity<String> health()
    {
        return new ResponseEntity<>("server is working", HttpStatus.OK);
    }

    @GetMapping("/me")
    public Map<String, String> me(Authentication auth) {
        return Map.of("username", auth.getName());
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user)
    {
        User u = userService.register(user);
        return new ResponseEntity<>(u.getUsername(), HttpStatus.OK);
    }

    // read the PDF only (no validation, no prediction) so the UI can let the user review/fill missing fields
    @PostMapping("/PDF/extract")
    public ReportRequest extract(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only .pdf files are accepted");
        return reportService.extractFromPdf(file);
    }

    @PostMapping("/PDF")
    public ResponseEntity<ResultResponse> pdf_upload(Authentication auth, @RequestParam("file") MultipartFile file)
    {

        if (file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");

        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "only .pdf files are accepted (got: " + name + ", type: " + file.getContentType() + ")");

        User user = userService.getUserByUsername(auth.getName());
        Result r = reportService.GenerateResultViaPdf(user,file);
        return new ResponseEntity<>(toResponse(r), HttpStatus.OK);
    }

    @PostMapping("/Report")
    public ResponseEntity<ResultResponse> getResult(Authentication auth, @Valid @RequestBody ReportRequest report)
    {
        Report rep = reportService.createReport(userService.getUserByUsername(auth.getName()), report);
        try {
            Result r = reportService.GenerateResult(rep);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(r));
        } catch (OrtException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.toString());
        }
    }

    // answers questions about ONE assessment with the local Ollama model
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest req) {
        if (req == null || req.messages() == null || req.messages().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no messages");
        return Map.of("reply", chatService.reply(req.messages(), req.context()));
    }

    // same as /chat but the answer arrives token by token (Server-Sent Events).
    // Written straight to the servlet response on purpose: no async dispatch, so Spring Security
    // never has to re-authorize the request when the stream ends.
    @PostMapping("/chat/stream")
    public void chatStream(Authentication auth, @RequestBody ChatRequest req, HttpServletResponse resp) throws IOException {
        if (req == null || req.messages() == null || req.messages().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no messages");

        // save the question that was just asked (the last "user" message); the browser sends the whole history each time
        String username = auth.getName();
        Result chatResult = req.resultId() == null ? null : chatHistory.ownedResult(username, req.resultId());   // 404 if not this user's
        for (int i = req.messages().size() - 1; i >= 0; i--) {
            ChatService.Msg m = req.messages().get(i);
            if (m != null && "user".equals(m.role())) {
                String q = m.content() == null ? "" : m.content();
                chatHistory.save(chatResult, "user", q.substring(0, Math.min(q.length(), 2000)));
                break;
            }
        }

        StringBuilder answer = new StringBuilder();
        PrintWriter[] out = new PrintWriter[1];            // opened lazily on the first token
        try {
            chatService.stream(req.messages(), req.context(), token -> {
                answer.append(token);
                try {
                    if (out[0] == null) out[0] = openSse(resp);
                    sse(out[0], null, Map.of("t", token));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);     // browser left / pressed Stop -> stop reading Ollama
                }
            });
            if (out[0] == null) out[0] = openSse(resp);
            sse(out[0], "done", Map.of());
        } catch (UncheckedIOException e) {
            // client disconnected: nothing to do
        } catch (ResponseStatusException e) {
            if (out[0] == null) throw e;                   // nothing sent yet -> normal 503/502 error response
            sse(out[0], "error", Map.of("message", e.getReason() == null ? "The answer was interrupted" : e.getReason()));
        } finally {
            // save the answer, or the part of it that arrived if the user pressed Stop / the stream broke
            chatHistory.save(chatResult, "assistant", answer.toString().trim());
        }
    }

    // the saved chat of one assessment, oldest message first
    @GetMapping("/chat/history")
    public List<Map<String, Object>> getChatHistory(Authentication auth, @RequestParam Integer resultId) {
        return chatHistory.list(auth.getName(), resultId);
    }

    // called when the user deletes an assessment from the history
    @DeleteMapping("/chat/history")
    public ResponseEntity<Void> deleteChatHistory(Authentication auth, @RequestParam Integer resultId) {
        chatHistory.delete(auth.getName(), resultId);
        return ResponseEntity.noContent().build();
    }

    // ---- serial comparison -------------------------------------------------------------------

    // the browser calls this right after /Report: it says which patient the new result belongs to
    @PutMapping("/assessments/{resultId}")
    public ResponseEntity<Void> registerAssessment(Authentication auth, @PathVariable Integer resultId,
                                                   @RequestBody AssessmentMeta meta) {
        timelineService.register(auth.getName(), resultId, meta == null ? "" : meta.label());
        return ResponseEntity.noContent().build();
    }

    // every visit of the logged-in user, oldest first (the browser groups them by patient label)
    @GetMapping("/timeline")
    public List<Map<String, Object>> timeline(Authentication auth) {
        return timelineService.timeline(auth.getName());
    }

    private PrintWriter openSse(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpStatus.OK.value());
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("X-Accel-Buffering", "no");         // harmless; stops nginx from buffering if you ever add one
        return resp.getWriter();
    }

    private void sse(PrintWriter w, String event, Object data) throws IOException {
        if (event != null) w.write("event: " + event + "\n");
        w.write("data: " + JSON.writeValueAsString(data) + "\n\n");   // JSON keeps newlines inside tokens safe
        w.flush();
        if (w.checkError()) throw new IOException("client closed the stream");
    }

    private ResultResponse toResponse(Result r) {
        return new ResultResponse(
                r.getId(), r.getCath(), r.getCath_prob(), r.getLAD(), r.getLAD_prob(),
                r.getLCX(), r.getLCX_prob(), r.getRCA(), r.getRCA_prob(),
                explanationService.explain(r.getReport(), 10),
                uncertaintyService.bands(r.getReport()));
    }
}
