# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository. For a human-readable version with build instructions, recipes, and the full release process, see [`docs/DEVELOPMENT.md`](./docs/DEVELOPMENT.md).

## What this project is

Invoker is a Kotlin Multiplatform / Compose for Desktop reimplementation of the **XMage Launcher** — a Java/Swing app that downloads, updates, and runs the XMage Magic: The Gathering client and server. The original Java launcher lives in the sibling directory `../XMageLauncher/` and remains the source of truth for behavior questions (config wire format, classpath convention, settings keys). Consult it before guessing.

## Common commands

- Build everything: `./gradlew.bat build` (Windows; `./gradlew build` on Unix)
- Run from source: `./gradlew.bat :desktopApp:run` (blocks until the window closes)
- Hot reload during dev: `./gradlew.bat :desktopApp:hotRun --auto`
- Compile-check only (fast): `./gradlew.bat :shared:compileKotlinJvm`
- Native installer for current OS: `./gradlew.bat :desktopApp:packageDistributionForCurrentOS` (MSI on Windows, DMG on macOS, DEB on Linux)
- App image with bundled JRE (faster, no installer wrap): `./gradlew.bat :desktopApp:createDistributable` — outputs to `desktopApp/build/compose/binaries/main/app/Invoker/Invoker.exe`
- Tests: `./gradlew.bat :shared:jvmTest` (currently no tests written)

## Architecture

### Module split — and where code actually lives

Two Gradle modules (`:shared` and `:desktopApp`) but the codebase is JVM-only. **Almost all code — including UI — lives in `shared/src/jvmMain/`, not `commonMain`.** Reason: `commonMain` in a KMP project is compiled to common metadata and cannot see Java APIs (`java.nio.file.Path`, `ProcessBuilder`, `java.util.Properties`) or JVM-only dependencies (Ktor CIO engine, Commons Compress, Maven Artifact). `commonMain` is reserved for pure-Kotlin DTOs and constants (`config/`, `settings/UserSettings.kt`, `ui/Strings.kt`). Anything touching files, processes, or the Java stdlib goes in `jvmMain`.

`:desktopApp` is just `main.kt` — it constructs the Compose `Window` and delegates to `App()` from `:shared`.

### Composition root

`AppEnvironment` (`shared/src/jvmMain/.../AppEnvironment.kt`) is the single composition root, built at `App()`. It owns `Paths`, `InstalledState`, `ConfigService`, `Downloader`, and `XMageRunner`. `MainViewModel` (an AndroidX `ViewModel`) takes the environment and drives everything. **No DI framework** — manual wiring; resist the temptation to add Hilt/Koin.

### Wire compatibility with xmage.today

The launcher fetches `https://xmage.today/config.json` and must parse it verbatim. `config/RemoteConfig.kt` mirrors the shape exactly: `JavaInfo.location` is a URL **prefix** to which `Platform.javaUrlSuffix` (e.g. `windows-x64.tar.gz`) is appended; `XMageInfo.locations` is the mirror-fallback array used by `Downloader.download(primary, mirrors, …)`. Do not "clean up" these DTO names — the field names and `@SerialName("XMage")`/`@SerialName("Launcher")` mappings are load-bearing.

### Two separate JREs

- The **launcher** ships with its own JRE via `compose.desktop.application.nativeDistributions` (jpackage). Users do not need Java installed.
- The **XMage client/server** still requires Java 8, downloaded separately at runtime per the config (`xmage/{role}/...` runs against `{installRoot}/java/jre{version}/`). On macOS the Java home is `jre{version}.jre/Contents/Home`.

When debugging "Java" things, identify which JRE you mean.

### Install layout

`Paths.defaultInstallRoot()` picks a platform-appropriate user-data directory (`%LOCALAPPDATA%\Invoker`, `~/Library/Application Support/Invoker`, `$XDG_DATA_HOME/Invoker` or `~/.local/share/Invoker`). Inside: `java/jre{version}/`, `xmage/{mage-client,mage-server,lib/*}/`, `installed.properties`, `xmage_launcher.log`. `InstalledState` persists `UserSettings` + `InstalledVersions` to `installed.properties` — when the file is corrupt, settings reset but **branch and home URL are preserved** (matches the Java launcher).

### XMage process launch

`XMageRunner` mirrors `Utilities.launchProcess()` from the Java launcher:
- Command: `{javaHome}/bin/java {jvmOpts} -cp {xmage}/{role}/lib/* {mainClass} [extraArgs]`
- Roles: `Client` → `mage.client.MageFrame`, `Server` → `mage.server.Main` (with `-testMode=true` if enabled)
- Working dir: `{xmage}/{role}` (not the install root)
- `JAVA_HOME` env set to the resolved Java home
- `redirectErrorStream(true)` — both stdout and stderr arrive on `process.inputStream` and are consumed by `ProcessLog` into a `SharedFlow<LogLine>` that the `ConsoleWindow` renders.

Only one server may run; clients are tracked in a list. Process exit clears the relevant slot via `process.onExit()`.

### XMage re-install preserves user data

`ArchiveExtractor.extractXMageZip` deliberately skips `images/`, `gameLogs/`, `backgrounds/` if they already exist at the destination — same behavior as the Java launcher (card images take ages to redownload). Don't switch this to a delete-then-extract or you'll cost users a multi-GB re-download.

### Navigation structure

Material 3 `NavigationRail` on the left, content pane on the right. Destinations live in `ui/Destination.kt` (`Home`, `Settings`, `Community`, `About`). Each destination has its own `*Pane.kt` composable. **There are no modal dialogs** for in-app navigation — Settings and About are inline panes, not `AlertDialog`s. `ConsoleWindow` is the only separate OS window (one per running client + one for the server, gated by `UserSettings.showClientConsole` / `showServerConsole`).

### Icons (non-obvious choice)

JetBrains stopped publishing `org.jetbrains.compose.material:material-icons-*` after 1.7.3 and now points users at Material Symbols. We use the community CMP port: **`com.composables:icons-material-symbols-outlined-cmp:2.2.1`**. API: `MaterialSymbols.Outlined.Home` (etc.), each icon is a per-file `ImageVector` extension property. To restyle the whole app, swap the artifact suffix (`-outlined-cmp` → `-rounded-cmp` / `-sharp-cmp`, ± `-filled`). **Do not** add `androidx.compose.material:material-icons-*` or the JetBrains 1.7.3 artifact back — the Symbols line is the active one.

### Assets

Drawable assets (the 17 backgrounds, the XMage label, `icon_mage*`) live in `shared/src/commonMain/composeResources/drawable/` and are accessed via the generated `invoker.shared.generated.resources.Res.drawable.bg_1` etc. (this is why filenames had to be renamed to valid Kotlin identifiers — leading digits aren't allowed). Add new drawables with snake_case names, then import the generated accessor.

### Self-update is intentionally absent

The original Java launcher self-updated by swapping its own JAR. We dropped that for v1 because the app ships as MSI/DMG/DEB — platform installers handle updates. If you're tempted to add a `LauncherSelfUpdate` task, check first whether a Conveyor/Sparkle/MSIX channel is the better fit.

## Reference files in `../XMageLauncher/`

When porting behavior or chasing edge cases:
- `src/main/java/com/xmage/launcher/XMageLauncher.java` — main flow, update chain, button wiring
- `src/main/java/com/xmage/launcher/Utilities.java` — OS/arch detection, process launch
- `src/main/java/com/xmage/launcher/Config.java` — settings keys, defaults
- `src/main/java/com/xmage/launcher/DownloadTask.java` — download + extract + mirror fallback
- `config_example/config.json` — the wire format DTOs must match
- `src/main/resources/MessagesBundle*.properties` — i18n source if you port strings to Compose Resources `strings.xml`
