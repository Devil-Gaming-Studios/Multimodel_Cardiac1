; Inno Setup 6.5.2+
[Setup]
AppName=CardioScope
AppVersion=1.0
DefaultDirName={autopf}\CardioScope
DefaultGroupName=CardioScope
OutputDir=out\installer
OutputBaseFilename=CardioScope-Setup
Compression=lzma2
SolidCompression=no
DiskSpanning=yes
DiskSliceSize=max
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest

[Files]
; model blobs are already compressed -> store as-is
Source: "out\CardioScope\app\ollama-models\*"; DestDir: "{app}\app\ollama-models"; Flags: recursesubdirs ignoreversion nocompression skipifsourcedoesntexist
Source: "out\CardioScope\*"; Excludes: "ollama-models\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{group}\CardioScope"; Filename: "{app}\CardioScope.exe"
Name: "{autodesktop}\CardioScope"; Filename: "{app}\CardioScope.exe"

[Run]
Filename: "{app}\CardioScope.exe"; Description: "Launch CardioScope"; Flags: postinstall nowait skipifsilent
