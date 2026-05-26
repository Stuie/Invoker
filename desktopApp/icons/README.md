# App icons

This directory holds the launcher's source artwork and the generated platform-specific icon files.

| File | Role |
|---|---|
| `rocket.svg` | Vector source (committed). Edit this for art changes. |
| `rocket.png` | 1024×1024 RGBA raster of the SVG. Used as the input to the icon generator. |
| `icon.png` | Generated. 512×512. Used for the Linux DEB build. |
| `icon.ico` | Generated. Windows multi-resolution (16/24/32/48/64/128/256), PNG-embedded. |
| `icon.icns` | Generated. macOS ICNS with 128/256/512/1024 entries (`ic07`/`ic08`/`ic09`/`ic10`). |

## Regenerating

The three `icon.*` files are produced from `rocket.png` by a small Gradle task. Run it when the source artwork changes; commit the regenerated files.

```bash
./gradlew :desktopApp:generateIcons
```

The generator is pure Kotlin/JVM (lives at `buildSrc/src/main/kotlin/icons/IconGenerator.kt`) — no external tools needed. It uses Java's `ImageIO` to load the source PNG, resizes with bicubic interpolation, and writes the ICO/ICNS containers byte-by-byte from the format specs. Both formats accept raw PNG payloads, so each platform-specific resolution is just a re-encoded PNG inside the right container.

To use a different source PNG without renaming, pass `-PiconSource=…`:

```bash
./gradlew :desktopApp:generateIcons -PiconSource=icons/alternative.png
```

## How the build picks these up

`desktopApp/build.gradle.kts` wires the three generated files into `compose.desktop.application.nativeDistributions` per platform:

- Windows → `iconFile.set(icon.ico)`
- macOS → `iconFile.set(icon.icns)`
- Linux → `iconFile.set(icon.png)`

The wiring is conditional on each file existing, so the build still succeeds before `generateIcons` has been run — the corresponding installer just falls back to the default Java icon. Once the three files are present, jpackage embeds them and the app shows the icon in the Windows taskbar, macOS Dock, and Linux app launcher.
