#define MyAppName "Wake Remote"
#define MyAppVersion "1.0.2"
#define MyAppExeName "WakeRemote.exe"
[Setup]
AppId={{4BD37056-7275-4B84-B52D-E855CA49F1B0}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=Apex Tech Labs
DefaultDirName={autopf}\Wake Remote
DefaultGroupName=Wake Remote
OutputDir=artifacts
OutputBaseFilename=WakeRemote-Setup-{#MyAppVersion}-x64
SetupIconFile=WakeRemote\Assets\setup.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
[Files]
Source: "WakeRemote\bin\Release\net8.0-windows\win-x64\publish\WakeRemote.exe"; DestDir: "{app}"; Flags: ignoreversion
; The app embeds its art, so these are a redundant on-disk copy rather than a hard
; dependency. Kept so the installed layout matches the portable one.
Source: "WakeRemote\Assets\app.ico"; DestDir: "{app}\Assets"; Flags: ignoreversion
Source: "WakeRemote\Assets\icon.png"; DestDir: "{app}\Assets"; Flags: ignoreversion
Source: "WakeRemote\Assets\apex-shield.png"; DestDir: "{app}\Assets"; Flags: ignoreversion
[Icons]
Name: "{autoprograms}\Wake Remote"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\Wake Remote"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon
[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked
[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch Wake Remote"; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent
