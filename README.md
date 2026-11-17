# Nocturne — black-and-gray utility client (Fabric 26.3) + standalone launcher

Nocturne is a lightweight client-side utility mod with a custom dashboard,
HUD counters (FPS, ping, CPS), Fullbright, Zoom, themes, profiles, per-module
keybinds and persistent settings — plus a standalone Windows launcher with
Microsoft, Ely.by and offline accounts.

## Project layout

- `src/` — the Fabric mod (`com.nocturne`, entrypoint `NocturneClient`)
- `launcher/` — the standalone launcher (`com.nocturne.launcher`, Swing + FlatLaf)
- `.github/workflows/build.yml` — builds the mod jar (Linux) and the
  Windows launcher (app-image), then publishes both to the `latest` release

## Build the mod (cloud, recommended)

1. Push this folder to the root of a GitHub repository.
2. Open the repo's **Actions** tab — the `build` workflow runs automatically.
3. Download the **Nocturne-Mod** artifact, or grab `nocturne-*.jar` from the
   `latest` release.
4. Put the jar in `mods/` next to Fabric API, launch with Java 25.

Local build: install JDK 25 + Gradle 9.8.2 and run `gradle build` in the
project root. The jar lands in `build/libs/`.

## Build the launcher (cloud)

The same workflow builds it on Windows: download the **Nocturne-Windows**
artifact (or the zip from the `latest` release), unzip, run `Nocturne.exe`.
Local build on Windows:

```
cd launcher
gradle installDist
jpackage --type app-image --dest launcher-dist --name Nocturne ^
  --input build/install/nocturne-launcher/lib ^
  --main-jar nocturne-launcher-1.0.0.jar ^
  --main-class com.nocturne.launcher.LauncherApp
```

The launcher runs on any JRE 17+.

## Using the launcher

1. **Accounts tab** — add a Microsoft account (approve the code in your
   browser; the launcher never sees your password), an Ely.by account, or an
   offline name for local play. Offline and Ely.by accounts cannot join
   online-mode servers that they do not own — server authentication is never
   bypassed.
2. **Play tab** — pick a Minecraft version (26.3 default), an optional pinned
   Fabric loader version, RAM, then press PLAY. The launcher downloads the
   game, Fabric, Fabric API and the Nocturne mod on first run and caches
   everything for offline launches.
3. **Settings tab** — game folder and (advanced) your own Azure client ID
   for Microsoft login.

## In-game client

- Right Shift opens the Nocturne dashboard (non-pausing overlay).
- Tabs: Modules (search + toggles), Keybinds (click a binding, press a key),
  Themes (4 gray presets + custom hex), Profiles (create/rename/delete/load),
  Settings (scale, opacity, animations, HUD corner + offsets, zoom level).
- Title screen is replaced with a Nocturne dashboard: Singleplayer,
  Multiplayer, Options, Nocturne, Quit — all fully working.
- Every toggle, setting and keybind persists across restarts.

## Requirements

- Minecraft 26.3, Fabric Loader 0.20.1+, Java 25 (mod).
- JRE 17+ (launcher).
