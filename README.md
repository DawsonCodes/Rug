# Rug

A no-dependency Paper plugin that spawns Carpet-style **fake players** using reflective
NMS/`ServerPlayer` access, plus a small set of Carpet-inspired rules. Everything lives under
a single `/rug` command hub and an in-game chest GUI.

- **Target:** Paper `26.1.2`, Java `25+`
- **Runtime dependencies:** none. The Paper API is only a `provided` Maven dependency.
- **No** CommandAPI, ProtocolLib, Citizens, or packet libraries are used.

> ⚠️ **Alpha software.** The fake-player backend pokes at Paper/NMS internals via reflection.
> It can break on server updates and may leave stray entities behind. Use it on test servers.
> If something gets stuck, run `/rug player purge`.

## Install

1. Build the jar (see below) or grab `Rug-<version>.jar` from your build output.
2. Drop the jar into your server's `plugins/` folder.
3. Start the server on Paper `26.1.2` (Java 25+).
4. Manage everything with `/rug` (aliases: `/papy`, `/pap`).

## Build

```bash
mvn -B package
```

The shaded-free jar is written to `target/Rug-<version>.jar`. Built jars and the `target/`
folder are git-ignored and are never committed.

CI builds the same way on GitHub Actions (`.github/workflows/build.yml`) using Temurin Java 25.

## Releasing

Releases are automated by `.github/workflows/release.yml`:

1. Push a version tag like `v0.2.10-nms-alpha11` (tags matching `v*`).
2. GitHub Actions builds the plugin and attaches the jar to a GitHub Release for that tag automatically.
3. Tags containing `alpha`, `beta`, or `rc` are published as **prereleases**; any other `v*` tag is a full release.

```bash
git tag v0.2.10-nms-alpha11
git push origin v0.2.10-nms-alpha11
```

The workflow uses the built-in `GITHUB_TOKEN` and the official `gh release create`, so no third-party
actions or secrets are needed. Normal branch pushes and pull requests never create a release.

## Commands

Everything starts from `/rug`:

| Command | What it does |
| --- | --- |
| `/rug` | Open the chest control GUI (or print help from console) |
| `/rug gui` / `/rug menu` | Open the chest control GUI |
| `/rug help` | Main help |
| `/rug about` | Version / backend / skin info |
| `/rug rules` | List every rule and its current value |
| `/rug rule <name> <value>` | Change a rule (e.g. `/rug rule punchKnockback 1.25`) |
| `/rug skincheck <name>` | Test skin lookup for a name |

### Fake players

Strict syntax — the first token is always the player name, the second is the action.
Unknown actions show help and **never** spawn anything.

| Command | What it does |
| --- | --- |
| `/rug player <name> spawn [skinName]` | Spawn a fake player (optionally with another player's skin) |
| `/rug player <name> kill` | Kill it with a vanilla death message, then clean up the body |
| `/rug player <name> remove` | Quietly remove it |
| `/rug player <name> hand` | Force-refresh its held item / equipment |
| `/rug player <name> status` | Show tracking info |
| `/rug player <name> tp` | Move it to you |
| `/rug player list` | List tracked fake players |
| `/rug player removeall` | Remove all tracked fake players |
| `/rug player purge` | Force-remove stuck fake players **and leftover dead bodies** |

Example:

```
/rug player TestBot spawn
/rug player RugBot spawn
/rug player TestBot kill
```

Tab-completion suggests generic bot names such as `RugBot`, `TestBot`, `BuilderBot`,
and `MinerBot`.

Minecraft names are capped at 16 characters. If you ask for a longer name, Rug tells you
the truncated name it actually spawned, and commands/tab-completion resolve that real name.

## GUI

`/rug` (or `/rug gui` / `/rug menu`) opens a chest control panel:

- **Fake Players** – shows the spawn/manage commands and the tracked list
- **Rules** – opens a toggle page; click boolean rules to flip them, and click
  `punchKnockback` / `skinLayers` to cycle presets
- **Skin / Profile Tools** – runs a skin lookup for your name and shows the skin-layer mask
- **Cleanup / Purge** – purges stuck bots and leftover dead bodies
- **Help / About** – version and command help

All chat commands still work, so technical users can skip the GUI entirely.

## Rules

Rules live in `config.yml` under `rules:` and are edited with `/rug rule <name> <value>`
or the Rules GUI page. Highlights:

- `playerBackend` – `auto` (try real NMS, fall back to visual), `nms`, or `visual`
- `punchKnockback` – how hard hitting a fake player knocks it back (`0` disables)
- `skinLayers` – `all`, `none`, or a comma list like `cape,jacket,sleeves,pants,hat`
- `broadcastDeaths` / `sendQuitMessage` – fake death/leave chat lines
- `allowDuplicateOnlineNames` – allow risky duplicate names with real online players

## Known limitations

- The NMS backend is reflective and alpha-quality; it can break on Paper/NMS updates.
- Fake players are clientless, so movement (knockback, item pickup) is driven server-side
  by Rug rather than by a real client, and may look slightly less smooth than a real player.
- Skin lookups can be rate-limited by Mojang (HTTP 429). Rug caches skins for the session,
  reuses the cache, and falls back to the default skin instead of spamming logs.
- Removing a fake player may still write its player data to disk; this does not affect the
  in-world cleanup.

---

© 2026 DawsonCodes. Released under the MIT License (see `LICENSE`).
