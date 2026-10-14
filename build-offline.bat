@echo off
setlocal
REM ===== Run from the project root (where gradlew.bat is). =====
REM Prereqs: JDK 21 (JAVA_HOME), Ollama zip extracted in bundle\ollama, models pulled into bundle\ollama-models,
REM          tessdata\eng.traineddata in bundle\tessdata, frontend built into src\main\resources\static.
set APP=CardioScopeV2
set BUNDLE=bundle
set OUT=D:\multi_model_offline\dist

if not exist %BUNDLE%\ollama\ollama.exe ( echo Missing %BUNDLE%\ollama\ollama.exe & exit /b 1 )

call gradlew.bat clean bootJar || exit /b 1
if exist %OUT% rmdir /s /q %OUT%
mkdir %OUT%\stage
for %%f in (build\libs\*.jar) do echo %%f | find "-plain" >nul || copy /y "%%f" %OUT%\stage\app.jar >nul
del /q %OUT%\stage\*-plain.jar 2>nul

jpackage --type app-image --name %APP% --dest %OUT% --input %OUT%\stage ^
  --main-jar app.jar --main-class org.springframework.boot.loader.launch.JarLauncher ^
  --runtime-image "%JAVA_HOME%" --win-console ^
  --java-options "-Xmx1g" ^
  --java-options "-Dapp.dir=$APPDIR" ^
  --java-options "-Dollama.url=http://127.0.0.1:11435" ^
  --java-options "-Dollama.model=llama3.1:8b" ^
  --java-options "-Dserver.address=127.0.0.1" || exit /b 1

xcopy /e /i /y %BUNDLE%\ollama        %OUT%\%APP%\app\ollama        >nul
if exist %BUNDLE%\ollama-models xcopy /e /i /y %BUNDLE%\ollama-models %OUT%\%APP%\app\ollama-models >nul
if exist %BUNDLE%\tessdata xcopy /e /i /y %BUNDLE%\tessdata %OUT%\%APP%\app\tessdata >nul

where iscc >nul 2>nul && iscc installer.iss
echo Done: %OUT%\%APP%\%APP%.exe
