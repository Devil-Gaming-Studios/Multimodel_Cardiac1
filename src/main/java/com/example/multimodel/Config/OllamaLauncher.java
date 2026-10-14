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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    @Value("${app.window:true}")       private boolean appWindow;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
    private Process process;

    // ---- model setup state, read by SetupController -> shown in the UI ----
    public record Status(String state, String message, long completed, long total, int percent) {}
    private volatile Status status = new Status("STARTING", "Starting local AI engine…", 0, 0, 0);
    public Status status() { return status; }
    private void set(String state, String msg, long done, long total) {
        int pct = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
        status = new Status(state, msg, done, total, pct);
    }
    private static final Pattern NUM_TOTAL = Pattern.compile("\"total\"\\s*:\\s*(\\d+)");
    private static final Pattern NUM_DONE  = Pattern.compile("\"completed\"\\s*:\\s*(\\d+)");
    private static final Pattern STATUS    = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ERROR     = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]*)\"");

    private void log(String m) {
        try {
            Path d = Path.of(System.getProperty("user.home"), ".multimodel");
            Files.createDirectories(d);
            Files.writeString(d.resolve("launcher.log"), java.time.LocalTime.now() + " " + m + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

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
        boolean packaged = appDir != null && !appDir.isBlank();
        log("onReady packaged=" + packaged + " appDir='" + appDir + "'");
        if (!packaged) { set("READY", "", 0, 0); return; }
        Thread t = new Thread(() -> {
            if (openBrowser) {                                    // own thread: openBrowser() blocks until the window is closed
                Thread w = new Thread(this::openBrowser, "app-window");
                w.setDaemon(true);
                w.start();
            }
            for (int i = 0; i < 120 && !up(); i++) sleep(500);
            if (!up()) { set("ERROR", "The local AI engine could not start. See ~/.multimodel/ollama.log", 0, 0); return; }
            log("ollama up at " + ollamaUrl + ", appDir=" + appDir);
            if (!hasModel()) { log("model missing -> pulling"); pull(); }
            if (!"ERROR".equals(status.state())) {
                set("LOADING", "Loading the AI model into memory…", 0, 0);
                preload();
                set("READY", "", 0, 0);
            }
        }, "ollama-warmup");
        t.setDaemon(true);
        t.start();
    }

    private boolean hasModel() {
        try {
            var r = http.send(HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/show"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + model + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
            log("hasModel(" + model + ") -> HTTP " + r.statusCode());
            return r.statusCode() == 200;
        } catch (Exception e) { log("hasModel error: " + e); return false; }
    }

    /** First launch only: downloads the model through Ollama (needs internet once). */
    private void pull() {
        set("DOWNLOADING", "First launch: downloading the AI model (one time, needs internet)…", 0, 0);
        String body = "{\"model\":\"" + model + "\",\"stream\":true}";
        try {
            var res = http.send(HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/pull"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofLines());
            if (res.statusCode() != 200) { set("ERROR", "Model download failed (HTTP " + res.statusCode() + ").", 0, 0); return; }
            boolean ok = false;
            try (var lines = res.body()) {
                for (var it = lines.iterator(); it.hasNext(); ) {
                    String line = it.next();
                    Matcher e = ERROR.matcher(line);
                    if (e.find()) { set("ERROR", "Model download failed: " + e.group(1) + " (check your internet connection and reopen the app)", 0, 0); return; }
                    Matcher st = STATUS.matcher(line), tt = NUM_TOTAL.matcher(line), dd = NUM_DONE.matcher(line);
                    String s = st.find() ? st.group(1) : "";
                    if (s.equals("success")) ok = true;
                    long total = tt.find() ? Long.parseLong(tt.group(1)) : 0;
                    long done = dd.find() ? Long.parseLong(dd.group(1)) : 0;
                    if (total > 0) set("DOWNLOADING", "Downloading the AI model… " + (done / 1_000_000) + " / " + (total / 1_000_000) + " MB", done, total);
                    else if (!s.isEmpty()) set("DOWNLOADING", "Preparing model: " + s, 0, 0);
                }
            }
            if (!ok && !hasModel()) set("ERROR", "Model download was interrupted. Reopen the app with internet to resume.", 0, 0);
        } catch (Exception ex) {
            log("pull error: " + ex);
            set("ERROR", "No internet? The model could not be downloaded. Connect once and reopen the app.", 0, 0);
        }
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

    /** Opens the UI in its own app-style window (Edge/Chrome --app mode: no tabs, no address bar).
     *  Closing that window quits CardioScope (and the bundled Ollama). Falls back to the default browser. */
    private void openBrowser() {
        String url = "http://localhost:" + serverPort;
        try {
            if (appWindow) {
                String exe = findAppBrowser();
                if (exe != null) {
                    Path profile = Path.of(System.getProperty("user.home"), ".multimodel", "window");
                    Files.createDirectories(profile);
                    Process w = new ProcessBuilder(exe, "--app=" + url, "--user-data-dir=" + profile,
                            "--window-size=1360,900", "--no-first-run", "--no-default-browser-check")
                            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                    log("opened app window with " + exe);
                    long t0 = System.currentTimeMillis();
                    w.waitFor();                                      // blocks until the window is closed
                    if (System.currentTimeMillis() - t0 > 5000) { log("window closed -> exiting"); System.exit(0); }
                    return;
                }
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(URI.create(url));
            else if (System.getProperty("os.name").toLowerCase().contains("win"))
                new ProcessBuilder("cmd", "/c", "start", url).start();
        } catch (InterruptedException ie) { Thread.currentThread().interrupt();
        } catch (Exception e) { log("openBrowser error: " + e); }
    }

    private static String findAppBrowser() {
        String[] candidates = {
                System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe",
                "/usr/bin/microsoft-edge", "/usr/bin/google-chrome", "/usr/bin/chromium"
        };
        for (String c : candidates) if (c != null && !c.startsWith("null") && Files.isRegularFile(Path.of(c))) return c;
        return null;
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    @PreDestroy
    public void stop() {
        if (process == null) return;
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
    }
}