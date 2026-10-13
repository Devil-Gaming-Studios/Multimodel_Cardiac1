# CardioScope – offline Windows build

1. Frontend: `cd mm-frontend && npm i && npm run build`, copy `dist/*` into `src/main/resources/static/`.
2. Add `ChatService.java` (replace), `ChatStreamController.java`, `OllamaLauncher.java` to the project.
3. Ollama: download `ollama-windows-amd64.zip` from the Ollama GitHub releases, extract the WHOLE folder to `bundle\ollama` (ollama.exe + lib\ollama must stay together).
4. Model (clean folder so only this model ships):
   `set OLLAMA_MODELS=%CD%\bundle\ollama-models` then, with a normal Ollama running under that env, `ollama pull llama3.1:8b` (~5 GB).
5. OCR: put `eng.traineddata` in `bundle\tessdata` and point Tess4J datapath to `System.getProperty("app.dir") + "/tessdata"`.
6. Remove debug properties and `System.out.println` of patient data; change DB password placeholders.
7. Run `build-offline.bat` (needs JDK 21 with jpackage; Inno Setup optional for `CardioScope-Setup.exe`, with disk spanning for >4 GB).
8. Test on a clean PC with no internet: double-click CardioScope.exe; the browser opens at http://localhost:8080.

Notes: bundled Ollama uses port 11435 so it never clashes with an installed Ollama; closing the console window stops the app and Ollama.

## Small installer (download model on first launch)
Skip step 4 and the `ollama-models` copy (also remove the `ollama-models` line from installer.iss and the check in build-offline.bat).
On first launch OllamaLauncher pulls the model into `app\ollama-models` and the UI shows a progress banner (needs internet once).
Add `"/setup/status"` to the `permitAll()` list in SecurityConfig and add `SetupController.java` (package `Controller`, adjust to yours).
