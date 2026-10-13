package com.example.multimodel.Controller;

import com.example.multimodel.Config.OllamaLauncher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetupController {
    private final OllamaLauncher launcher;
    public SetupController(OllamaLauncher launcher) { this.launcher = launcher; }

    /** Public (no login): the UI polls this to show model download progress. */
    @GetMapping("/setup/status")
    public OllamaLauncher.Status status() { return launcher.status(); }
}
