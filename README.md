# Invoker

**A modern launcher for [XMage](https://xmage.today/).**

[![Build](https://github.com/essteeyou/invoker/actions/workflows/build.yml/badge.svg)](https://github.com/essteeyou/invoker/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](./LICENSE)

Invoker downloads, updates, and launches the XMage client and server. It replaces the legacy XMage Launcher (last GitHub release in 2014) with a native, modern desktop app — no Java required on your machine, no JAR-file friction, no console of log lines staring at you on startup.

## What is XMage?

XMage is a free, open-source, community-built [Magic: The Gathering](https://magic.wizards.com/) client. The game and rules engine are maintained by volunteers at [magefree/mage](https://github.com/magefree/mage). XMage itself is unaffiliated with Wizards of the Coast.

## Screenshots

> _Coming soon — screenshots will be added after the first tagged release._

<!-- ![Home](docs/screenshots/home.png) -->
<!-- ![Settings](docs/screenshots/settings.png) -->

## Install

Grab the right installer for your platform from the [latest release](https://github.com/essteeyou/invoker/releases/latest).

| Platform | File | Notes |
|---|---|---|
| **Windows** | `Invoker-*-windows-x86_64.msi` | Double-click to install. Windows SmartScreen will warn on first run — click **More info** → **Run anyway**. |
| **macOS (Apple Silicon)** | `Invoker-*-macos-arm64.dmg` | Drag Invoker.app to Applications. First launch shows a Gatekeeper warning — **right-click → Open** instead of double-clicking, then click **Open** on the prompt. |
| **Linux** | `Invoker-*-linux-amd64.deb` | `sudo dpkg -i Invoker-*-linux-amd64.deb` then launch from your application menu. |

> **No Intel macOS build.** Apple Silicon DMG only. If you're on an Intel Mac you'll need to build from source — see [`docs/DEVELOPMENT.md`](./docs/DEVELOPMENT.md).

You do **not** need Java installed. The launcher ships with its own bundled runtime. XMage itself uses Java 8 — Invoker fetches that for you on first run.

## What's new vs. the legacy XMage Launcher

- **Native installers with a bundled runtime.** No JAR-file dance, no separate Java install.
- **Modern UI.** Left-rail navigation, status chips, snackbar feedback, no on-screen log spam.
- **Flexible Java for XMage.** Use the version Invoker downloads, point at a system-installed JDK, or pick a custom folder. (The launcher itself always uses its bundled runtime; this setting is for XMage only.)
- **Refuses to update XMage while a client is running.** The legacy launcher would happily corrupt a live process's classpath; Invoker checks first and asks you to close anything that's open.
- **Per-launch consoles** (optional). Show stdout/stderr from the client or server in a separate window when you need to debug something.

## Reporting issues / contributing

Bug reports, feature requests, and pull requests are welcome via [GitHub Issues](https://github.com/essteeyou/invoker/issues). For development setup and architecture notes, see [docs/DEVELOPMENT.md](./docs/DEVELOPMENT.md). For security disclosures, see [docs/SECURITY.md](./docs/SECURITY.md).

## Trademarks

Invoker is an independent launcher for XMage, a volunteer-built open-source Magic: The Gathering client. *Magic: The Gathering* and all related properties are trademarks of Wizards of the Coast. Neither Invoker nor XMage is affiliated with Wizards of the Coast.

## License

[MIT](./LICENSE) — © 2026 Stuart Gilbert.
