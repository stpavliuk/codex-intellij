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

Both processes must run as the same operating-system user. On macOS and Linux,
they must also use the same `CODEX_HOME`; the bridge defaults to `~/.codex`
when it is unset.

## Development

```shell
./gradlew test
./gradlew buildPlugin
./gradlew runIde
```

On Windows, the plugin connects to Codex's local named pipe at
`\\.\pipe\codex-ipc`. Keep the Codex desktop app or another Codex IDE
integration running so the pipe is available. The plugin retries the
connection once per second.

In the Codex desktop app, enable IDE context with `/ide-context`. Open a
local task for the same project as your JetBrains IDE, select code, and
submit a prompt. Codex receives the selection when the prompt is sent;
it does not appear in the composer beforehand.
