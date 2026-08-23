# Codex IDE Context Plugin

An IntelliJ Codex that provides IDE context to the active Codex CLI/App session.

_Inspired by Claude Code IDE integration for IntelliJ._

The bridge supports the active file, selected text and ranges, and open editor
tabs over Codex's local IDE-context IPC protocol. It works with:

- Codex CLI running in IntelliJ's terminal or another local terminal.
- The local Codex desktop app.

## Installation

1. Download `codex-intellij-<version>.zip` from the latest GitHub Release.
2. In your JetBrains IDE, open **Settings | Plugins**, select the gear menu,
   and choose **Install Plugin from Disk**.
3. Select the downloaded ZIP file and restart the IDE when prompted.

## Usage

1. Open a project in IntelliJ IDEA.
2. Run `codex` from that project directory.
3. In Codex CLI, enter `/ide on`. In the Codex app, enable **IDE context**.
4. Select code in IntelliJ and submit a prompt normally.

Both processes must run as the same operating-system user and use the same
`CODEX_HOME`. The bridge defaults to `~/.codex` when `CODEX_HOME` is unset.

## Development

```shell
./gradlew test
./gradlew buildPlugin
./gradlew runIde
```

The initial implementation uses local Unix-domain sockets on macOS and
Linux. Windows is not supported.
