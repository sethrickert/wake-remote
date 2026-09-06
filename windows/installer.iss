#define MyAppName "Wake Remote"
#define MyAppVersion "3.0.0"
#define MyAppExeName "WakeRemote.exe"
[Setup]
AppId={{4BD37056-7275-4B84-B52D-E855CA49F1B0}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
DefaultDirName={autopf}\Wake Remote
DefaultGroupName=Wake Remote
OutputDir=artifacts
OutputBaseFilename=WakeRemote-Setup-3.0.0-x64
SetupIconFile=WakeRemote\Assets\setup.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
Compression=lzma2
SolidCompression=yes
[Files]
Source: "WakeRemote\bin\Release\net8.0-windows\win-x64\publish\WakeRemote.exe"; DestDir: "{app}"; Flags: ignoreversion
[Icons]
Name: "{autoprograms}\Wake Remote"; Filename: "{app}\{#MyAppExeName}"
[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch Wake Remote"; Flags: nowait postinstall skipifsilent
