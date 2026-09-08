#define MyAppName "Wake Remote"
#define MyAppVersion "1.0.2"
#define MyAppExeName "WakeRemote.exe"

; Published output directory, relative to this .iss file. Defined once here and
; overridable with `iscc /DPublishDir=...`, so a future .NET TFM or RID change cannot
; rebreak [Files] the way the previous hardcoded
; WakeRemote\bin\Release\net8.0-windows\win-x64\publish path would have. The release
; workflow publishes with a matching explicit -o.
#ifndef PublishDir
  #define PublishDir "publish"
#endif

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
; This reads the ORIGINAL WakeRemote.exe. The portable release asset is produced as a
; copy named WakeRemote-Portable.exe, never by renaming this file, so building the
; installer and labelling the portable build cannot interfere with each other.
Source: "{#PublishDir}\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
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
