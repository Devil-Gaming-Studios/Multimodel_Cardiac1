package com.example.multimodel.Config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Starts the bundled Ollama next to the app (only when -Dapp.dir is set, i.e. the packaged build),
 * waits until it is ready, preloads the model and opens the browser. Stops Ollama on exit.
 * In dev (no app.dir) it does nothing and you use your own Ollama.
 */
@Component
public class OllamaLauncher {

    @Value("${app.dir:}")            private String appDir;
    @Value("${ollama.url:http://127.0.0.1:11435}") private String ollamaUrl;
    @Value("${ollama.model:llama3.1:8b}")          private String model;
    @Value("${server.port:8080}")    private int serverPort;
    @Value("${app.open-browser:true}") private boolean openBrowser;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private Process process;

    private boolean up() {
        try {
            var r = http.send(HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200;
        } catch (Exception e) { return false; }
    }

    @PostConstruct
    public void start() {
        if (appDir == null || appDir.isBlank()) return;                 // dev mode
        if (up()) return;                                               // something already answers there
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        Path base = Path.of(appDir);
        Path exe = base.resolve("ollama").resolve(win ? "ollama.exe" : "ollama");
        if (!Files.isRegularFile(exe)) { System.err.println("[launcher] bundled Ollama not found: " + exe); return; }
        try {
            URI u = URI.create(ollamaUrl);
            ProcessBuilder pb = new ProcessBuilder(exe.toString(), "serve");
            pb.directory(exe.getParent().toFile());
            Map<String, String> env = pb.environment();
            env.put("OLLAMA_HOST", u.getHost() + ":" + u.getPort());
            env.put("OLLAMA_MODELS", base.resolve("ollama-models").toString());
            env.put("OLLAMA_KEEP_ALIVE", "30m");
            env.put("OLLAMA_NUM_PARALLEL", "1");
            env.put("OLLAMA_MAX_LOADED_MODELS", "1");
            env.put("OLLAMA_NO_CLOUD", "1");
            env.put("OLLAMA_NOPRUNE", "1");
            Path logDir = Path.of(System.getProperty("user.home"), ".multimodel");
            Files.createDirectories(logDir);
            File log = logDir.resolve("ollama.log").toFile();
            pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            process = pb.start();
        } catch (Exception e) {
            System.err.println("[launcher] could not start Ollama: " + e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread t = new Thread(() -> {
            if (appDir != null && !appDir.isBlank()) {
                for (int i = 0; i < 120 && !up(); i++) sleep(500);
                if (up()) preload();
            }
            if (openBrowser && appDir != null && !appDir.isBlank()) openBrowser();
        }, "ollama-warmup");
        t.setDaemon(true);
        t.start();
    }

    private void preload() {
        try {
            // empty generate request loads the model into memory and keeps it there
            String body = "{\"model\":\"" + model + "\",\"keep_alive\":\"30m\"}";
            http.send(HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/generate"))
                    .timeout(Duration.ofMinutes(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }

    private void openBrowser() {
        try {
            String url = "http://localhost:" + serverPort;
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(URI.create(url));
            else if (System.getProperty("os.name").toLowerCase().contains("win"))
                new ProcessBuilder("cmd", "/c", "start", url).start();
        } catch (Exception ignored) { }
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    @PreDestroy
    public void stop() {
        if (process == null) return;
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
    }
}
