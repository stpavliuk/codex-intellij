# Codex IDE Context Plugin

An IntelliJ Codex that provides IDE context to the active Codex CLI/App session.
**Inspired by Claude Code IDE integration for IntelliJ.**

The bridge supports the active file, selected text and ranges, and open editor
tabs over Codex's local IDE-context IPC protocol. It works with:

- Codex CLI running in IntelliJ's terminal or another local terminal.
- The local Codex desktop app.

## Usage

1. Build with `./gradlew buildPlugin`, then install
   `build/distributions/codex-intellij-0.1.0.zip` using **Settings | Plugins |
   Install Plugin from Disk**.
2. Open a project in IntelliJ IDEA.
3. Run `codex` from that project directory.
4. In Codex CLI, enter `/ide on`. In the Codex app, enable **IDE context**.
5. Select code in IntelliJ and submit a prompt normally.

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
