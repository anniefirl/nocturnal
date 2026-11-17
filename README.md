# Nocturne Client

A new, independent Minecraft Java utility-client project for Fabric 26.3.

## Current status

This repository is the **build foundation** for Nocturne (0.1.0). It contains the Fabric client entrypoint, versioned build configuration, and a GitHub Actions workflow. It is not yet the finished feature-complete client or custom launcher.

## Target design

- Minimal black / graphite interface
- Custom dashboard main menu
- Large in-game dashboard
- FPS, ping, and CPS HUD
- Fullbright and hold-to-zoom modules
- Basic per-module settings
- Rebindable controls
- Persistent themes and profiles
- Lightweight client-side implementation

## Requirements

- Java 25
- Gradle 9.6.0
- Minecraft 26.3
- Fabric Loader 0.20.1 or newer compatible release
- Fabric API 0.166.2+26.3

The Minecraft 26.3 Fabric toolchain values are based on the Fabric development page and the Fabric API 26.3 branch. Check the official pages before upgrading versions.

## Build locally

From the repository root:

```bash
gradle build
```

The mod JAR will be created under `build/libs/`.

For local testing, install the matching Fabric Loader for Minecraft 26.3, then place the built JAR in that instance's `mods` folder along with Fabric API.

## Build on GitHub

1. Create a new **public or private** GitHub repository named `Nocturne-Client`.
2. Extract this project ZIP.
3. Upload the *contents* of this folder to the repository root (the `.github` folder must also be included).
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab.
6. Select **Build Nocturne Client** and wait for the workflow to finish.
7. Open the successful workflow run, scroll to **Artifacts**, and download `nocturne-client-build`.

Every push and pull request also triggers the build workflow automatically.

## Launcher and account support

The custom desktop launcher is a separate application and is not included in this initial Fabric build foundation. The planned launcher needs a proper account-authentication flow, version metadata/install management, Fabric profile setup, Java detection, RAM settings, and process launching.

Microsoft sign-in should use the supported Microsoft account flow. Ely.by support requires implementing its documented authentication flow and passing the resulting session to Minecraft; authlib-injector alone does not perform launcher login. Offline profiles should be limited to offline/local play and must not be presented as a way to access authenticated online servers.

Do not put account passwords, client secrets, or access tokens in source code or GitHub commits.

## Planned source layout

```text
src/main/java/dev/nocturne/client/
  NocturneClient.java
  config/
  gui/
  hud/
  input/
  module/
```

## License

MIT. Review and add a complete license file before publishing a public release.
