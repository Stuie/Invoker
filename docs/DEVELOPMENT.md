# Development

Human-readable engineering guide for working on Invoker. For agent-focused architecture notes optimised for AI tooling, see [`CLAUDE.md`](../CLAUDE.md) at the repo root.

## Prerequisites

- **JDK 21**, Eclipse Temurin. The Gradle build auto-provisions this via the Foojay toolchain resolver — you don't need to install it manually if your machine has the foojay-resolver convention set up (it's wired in `settings.gradle.kts`).
- **Git**.
- **OS-specific packaging tools**:
  - **Windows**: WiX Toolset 3.11 is auto-downloaded by the Gradle build the first time you package.
  - **macOS**: built-in `productbuild` and friends.
  - **Linux**: `fakeroot` and `dpkg-dev` (for `.deb` packaging). Install via apt: `sudo apt install fakeroot dpkg-dev`.

## Build & run

```bash
./gradlew :desktopApp:run                          # launch the app from source
./gradlew :desktopApp:hotRun --auto                # hot-reload during development
./gradlew :shared:jvmTest                          # run unit tests
./gradlew :desktopApp:createDistributable          # build the app image only (no installer)
./gradlew :desktopApp:packageDistributionForCurrentOS   # build the OS-native installer
./gradlew :desktopApp:packageDistributionForCurrentOS -Pversion=1.0.0-rc1   # override version
```

On Windows use `./gradlew.bat` (or just `gradlew.bat` with no `./`).

## Project layout

Two-module Gradle project: `:shared` and `:desktopApp`.

- **`:desktopApp`** holds only `main.kt`: it constructs the Compose `Window` and delegates to `App()` from `:shared`.
- **`:shared`** holds everything else — UI, business logic, file IO, process spawning, tests.

`:shared` is a Kotlin Multiplatform module configured with one target (`jvm()`), so the source sets are:

| Source set | What goes here |
|---|---|
| `commonMain` | Pure-Kotlin code: serialisation DTOs (`config/`), `Strings.kt`, `UserSettings`, theme tokens. Nothing using `java.*` APIs. |
| `jvmMain` | Everything else, including all Compose UI files. The launcher only targets the JVM, so this is where the bulk of the codebase lives. |
| `commonTest` | Pure-Kotlin tests (`kotlinx.serialization` round-trips). |
| `jvmTest` | JVM tests using `java.nio.file`, `java.util.Properties`, etc. |

This is unusual for KMP, which typically puts UI in `commonMain`. The reason: `commonMain` is compiled to common metadata and can't see Java APIs — and our UI threads call out to `Desktop.getDesktop()`, `JFileChooser`, `ProcessBuilder`, file IO, etc. Keeping the whole UI in `jvmMain` avoids constant expect/actual gymnastics. If we ever add iOS or web targets, the lower-level layers (`config/`, `update/`, `Strings.kt`) are already common-friendly; only the UI would need attention.

## Architecture overview

### The composition root

`AppEnvironment` (`shared/src/jvmMain/.../AppEnvironment.kt`) wires together `Paths`, `InstalledState`, `ConfigService`, `Downloader`, `XMageRunner`, and `JavaDetector`. `MainViewModel` takes the environment as a constructor argument and exposes a single `StateFlow<UiState>` plus a `SharedFlow<String>` of snackbar messages. There's no DI framework — manual wiring is fine at this scale.

### Wire compatibility with `xmage.today`

The launcher fetches `${xmageHomeUrl}/config.json` and must parse it verbatim. `config/RemoteConfig.kt` mirrors the existing schema exactly, including `@SerialName("XMage")` and `@SerialName("Launcher")`. Don't "clean up" the field names or casing — the schema is owned by xmage.today and we have to match it. The `XMageInfo.locations` array is the mirror-fallback list used by `Downloader.download(primary, mirrors, …)`.

### Two separate JREs

This trips everyone up first time:

1. **The launcher's own runtime** is bundled by jpackage when we build the MSI/DMG/DEB. Users never need Java installed.
2. **XMage's runtime** is a Java 8 JRE downloaded from xmage.today at first run, unpacked into the user data directory, and used as `JAVA_HOME` when spawning `mage.client.MageFrame` / `mage.server.Main`.

The "Java for XMage" picker in Settings lets the user choose: bundled (default), a detected system Java, or a custom folder. None of these affect the launcher itself.

### Install layout

`Paths.defaultInstallRoot()` picks a per-OS user-data directory:

- Windows: `%LOCALAPPDATA%\Invoker`
- macOS: `~/Library/Application Support/Invoker`
- Linux: `$XDG_DATA_HOME/Invoker` or `~/.local/share/Invoker`

Inside it: `installed.properties`, `xmage_launcher.log`, `java/jre<version>/`, `xmage/{mage-client,mage-server}/`. The `installed.properties` keys match the legacy XMage Launcher 1:1 so migration is transparent.

### XMage process launch

`XMageRunner` mirrors `Utilities.launchProcess()` from the legacy launcher:

- Command: `<javaHome>/bin/java <jvmOpts> -cp <xmage>/<role>/lib/* <mainClass> [extraArgs]`
- Roles: `Client` → `mage.client.MageFrame`, `Server` → `mage.server.Main` (with `-testMode=true` if the setting is on)
- Working dir: `<xmage>/<role>` (not the install root)
- `JAVA_HOME` env set to the resolved Java home
- `redirectErrorStream(true)` — stdout and stderr arrive together on `process.inputStream` and stream into a `Flow<LogLine>` that the optional `ConsoleWindow` consumes.

Only one server may run; clients are tracked in a list. Process exit clears the corresponding slot via `process.onExit()`.

### XMage re-install preserves user data

`ArchiveExtractor.extractXMageZip()` skips any path inside `images/`, `gameLogs/`, or `backgrounds/` if that top-level directory already exists at the destination. Card images take a long time to download, and the legacy launcher had the same preservation logic — don't switch to a delete-then-extract approach or users will pay a multi-GB redownload after every update.

### Self-update is intentionally absent

The legacy launcher self-updated by swapping its own JAR. We dropped that because: (a) the launcher ships as an MSI / DMG / DEB now, so the OS-native installer handles upgrades; (b) the swap-JAR approach is fragile across platforms; (c) user data lives outside the install dir and survives reinstalls. If you find yourself adding launcher self-update, consider Sparkle / WiX MSI auto-update / Conveyor as alternatives — and update this doc.

### Strings and future i18n

Every user-facing string lives in `shared/src/commonMain/.../ui/Strings.kt`, grouped by surface. UI files call `Strings.X` directly, with `String.format(Strings.X_TEMPLATE, args)` for templated messages. There is currently no locale switching — adding it later means swapping `Strings.kt` for Compose Resources `stringResource(Res.string.x)` calls (a single-file refactor of `Strings.kt`'s impl plus a one-time call-site rewrite). The benefit of doing the centralisation now is that the rewrite stays mechanical instead of a grep-through-the-codebase hunt.

### Icons

The picker uses Material Symbols (Outlined) from the community CMP port: `com.composables:icons-material-symbols-outlined-cmp`. To restyle the entire app, change the artifact suffix to `-rounded-cmp` or `-sharp-cmp` (optionally `-filled`). Icon names follow `MaterialSymbols.Outlined.Play_arrow` — underscored Pascal case, matching Google's source naming. Look up the available icons at [fonts.google.com/icons](https://fonts.google.com/icons).

## Common recipes

### Adding a new pane

1. Add a `Destination` entry in `ui/Destination.kt` with a Material Symbols icon and a `Strings.X` label.
2. Create a `MyPane.kt` composable in `ui/`. Use `PaneHeader(Strings.X)` for the top.
3. Wire it into `MainScreen.kt`'s `when (state.destination)` block.

### Adding a new settings row

Use `SettingRow(label, desc, control)` from `SettingsPane.kt`. If the control needs to expand below the row (like the Java picker), use the four-arg form with the optional `detail` slot. Add new strings to `Strings.kt` under the `// Settings pane` group.

### Adding a new user-facing string

Add it to `Strings.kt`, grouped by surface. If it has variable parts, use `%s` / `%d` and call via `String.format(...)`. Avoid English-style `(s)` pluralisation; use separate singular/plural constants where it matters.

## Release process

Tag-driven. Push a tag of the form `v1.x.y` to `main`; `.github/workflows/release.yml` builds the four installers (Windows MSI, Linux DEB, macOS Intel DMG, macOS Apple Silicon DMG) on platform-specific runners and uploads them to a GitHub Release auto-generated from the PR titles since the previous tag.

To verify locally before tagging:

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS -Pversion=1.0.0-rc1
```

The resulting installer ends up under `desktopApp/build/compose/binaries/main/<format>/`.

## Tests

`./gradlew :shared:jvmTest` runs the JVM test suite (5 files, ~22 tests today). The intent is to cover business logic — version comparison, settings persistence, archive extraction, config deserialisation, Java version-string parsing — not the UI. Compose UI tests are out of scope for v1.
