# Rug

A no-dependency Paper plugin that spawns Carpet-style **fake players** using reflective
NMS/`ServerPlayer` access, plus a small set of Carpet-inspired rules. Everything lives under
a single `/rug` command hub and an in-game chest GUI.

- **Version:** `v0.2.13-nms-alpha14`
- **Target:** Paper `26.1.x` (built/tested on `26.1.2`), Java `25+`
- **Runtime dependencies:** none. The Paper API is only a `provided` Maven dependency.
- **No** CommandAPI, ProtocolLib, Citizens, or packet libraries are used.

> ⚠️ **Alpha software.** The fake-player backend pokes at Paper/NMS internals via reflection.
> It can break on server updates and may leave stray entities behind. Use it on test servers.
> If something gets stuck, run `/rug player purge`.

## Install

1. Build the jar (see below) or grab `Rug-<version>.jar` from your build output.
2. Drop the jar into your server's `plugins/` folder.
3. Start the server on Paper `26.1.x` (Java 25+).
4. Manage everything with `/rug`. (There is intentionally no `/r` alias — it
   clashes with common reply/PM commands; `/rug` is the only command.)

## Build

```bash
mvn -B package
```

The shaded-free jar is written to `target/Rug-<version>.jar`. Built jars and the `target/`
folder are git-ignored and are never committed.

CI builds the same way on GitHub Actions (`.github/workflows/build.yml`) using Temurin Java 25.

## Releasing

Releases are automated by `.github/workflows/release.yml`:

1. Push a version tag like `v0.2.13-nms-alpha14` (tags matching `v*`).
2. GitHub Actions builds the plugin and attaches the jar (named `Rug-<tag>.jar`,
   e.g. `Rug-v0.2.13-nms-alpha14.jar`) to a GitHub Release for that tag automatically.
3. Tags containing `alpha`, `beta`, or `rc` are published as **prereleases**; any other `v*` tag is a full release.

```bash
git tag v0.2.13-nms-alpha14
git push origin v0.2.13-nms-alpha14
```

The workflow uses the built-in `GITHUB_TOKEN` and the official `gh release create`, so no third-party
actions or secrets are needed. Normal branch pushes and pull requests never create a release.

## Commands

Everything starts from `/rug`:

| Command | What it does |
| --- | --- |
| `/rug` | Open the chest control GUI (or print help from console) |
| `/rug gui` | Open the chest control GUI |
| `/rug help` | Main help |
| `/rug about` | Version / backend / skin info |
| `/rug players` | List tracked fake players |
| `/rug rules` | List every rule and its current value |
| `/rug rule <name> <value>` | Change a rule (e.g. `/rug rule punchKnockback 1.25`) |
| `/rug skincheck <name>` | Test skin lookup for a name |
| `/rug purge` | Force-remove stuck fake players and leftover bodies |

> `/rug menu` still works as a hidden alias of `/rug gui`, but it is no longer
> advertised in tab-completion to keep suggestions clean.

### Fake players

Strict syntax — the first token is always the player name, the second is the action.
Unknown actions show help and **never** spawn anything.

| Command | What it does |
| --- | --- |
| `/rug player <name> spawn [skinName]` | Spawn a fake player (optionally with another player's skin) |
| `/rug player <name> kill` | Kill it with a vanilla death message, then fully clean up the body |
| `/rug player <name> remove` | Quietly remove it |
| `/rug player <name> hand` | Force-refresh its held item / equipment |
| `/rug player <name> status` | Show backend / skin / tracking info |
| `/rug player <name> inventory` | Open an editor for its main inventory, hotbar, armor, and off-hand (no item duplication) |
| `/rug player <name> skin <skinName>` | Re-spawn it in place with a new skin |
| `/rug player <name> tp` | Move it to you |
| `/rug player removeall` | Remove all tracked fake players |
| `/rug player purge` | Force-remove stuck fake players **and leftover dead bodies** |

Spawn output is concise by default — one line per spawn. Set
`/rug rule verboseMessages true` for extra backend/skin detail.

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

`/rug` (or `/rug gui`) opens a chest control panel:

- **Fake Players** – a page of clickable player heads, one per tracked fake,
  showing state (alive/dead), backend, skin, and location. Click a head to open
  a **detail page** with: Kill, Remove, Refresh hand, Open inventory, Teleport to
  it, Bring it to you, and Change-skin instructions.
- **Rules** – a toggle page; click boolean rules to flip them, click
  `punchKnockback` / `skinLayers` / `playerBackend` to cycle presets, and click
  `deathAlertSound` to open a **death-sound picker** with ~20 sounds (wither is
  the default) that previews each one when selected
- **Skin / Profile Tools** – skincheck yourself, cycle skin layers, and skin-change instructions
- **Cleanup / Purge** – buttons to purge stuck/dead bots, remove all bots, and refresh the registry
- **Help / About** – version and command help

The inventory editor shows the fake player's **main inventory, hotbar, armor, and
off-hand** with labelled empty slots. It runs on a dedicated inventory holder and
writes back **only the real mapped slots**, so GUI buttons/labels can never leak
into the fake's inventory or drops. Armor slots only accept the right armor type
(helmet/chestplate/leggings/boots) and the off-hand accepts any item; invalid
items are rejected without being deleted or duplicated. Every click path
(shift-click, number-key swap, drag, double-click) is handled, edits are written
back on close/Back without duplication, armor is broadcast so it appears on the
fake, and it is guarded so it never acts on a dead/removed fake. GUI navigation is
silent and GUI buttons hide vanilla item stats (no stray "attack damage"
tooltips). All chat commands still work, so technical users can skip the GUI entirely.

## Rules

Rules live in `config.yml` under `rules:` and are edited with `/rug rule <name> <value>`
or the Rules GUI page. Highlights:

- `playerBackend` – `auto` (try real NMS, fall back to visual), `nms`, or `visual`
- `verboseMessages` – `false` (clean, one-line spawns) or `true` (extra detail)
- `keepInventory` – `false` drops a fake's items on death (default), `true` keeps them
- `punchKnockback` – how far a punched fake player slides (`0` disables)
- `skinLayers` – `all`, `none`, or a comma list like `cape,jacket,sleeves,pants,hat`
- `broadcastDeaths` / `sendQuitMessage` – fake death/leave chat lines
- `deathAlertSound` – death sound key (see the GUI picker); `summonAlertSound` for visual spawns
- `allowDuplicateOnlineNames` – allow risky duplicate names with real online players

## Skins and capes

- `/rug player <name> skin <skinName>` and `/rug skincheck <name>` resolve a real
  profile texture (online player → Paper profile → Mojang session) and apply it to
  the fake player and the visual-backend head.
- **Capes are not arbitrary.** Rug cannot grant official Minecraft capes that a
  profile does not already own. If the source profile's texture includes a cape,
  it is copied along with the skin and shown where the client renders it
  (controlled by `skinLayers`, which includes `cape`). There is no way to attach a
  cape a player does not have without custom resource packs or client mods, so Rug
  does not pretend to.

## Project structure

A single-file plugin today; the package split into `command/`, `fakeplayer/`,
`nms/`, `gui/`, `rules/`, `skin/`, and `util/` is planned for the next refactor.

```text
src/main/java/com/dawsoncodes/rug/Rug.java   ~4,300 lines
src/main/resources/plugin.yml
src/main/resources/config.yml
```

Approximate Java LOC:

- `Rug.java`: ~4,300
- Total Java LOC: ~4,300

Refresh the count any time with:

```bash
find src -name '*.java' -print0 | xargs -0 wc -l
```

## Known limitations

- The NMS backend is reflective and alpha-quality; it can break on Paper/NMS updates.
- Fake players are clientless, so movement (knockback, item pickup) is driven server-side
  by Rug rather than by a real client, and may look slightly less smooth than a real player.
- Skin lookups can be rate-limited by Mojang (HTTP 429). Rug caches skins for the session,
  reuses the cache, and falls back to the default skin instead of spamming logs. Skin/rate-limit
  notices only appear when `verboseMessages` is on.
- On death (or `/rug player <name> kill`), Rug intercepts the lethal hit, shows the death
  message once, drops the fake's inventory/armor/off-hand like a normal survival death
  (unless `keepInventory` is set), then fully removes it from the world, player list, and
  client view (player-info-remove + remove-entity packets) and emits a clean leave line —
  so the fake actually leaves the server instead of getting stuck as a corpse.

---

© 2026 DawsonCodes. Released under the MIT License (see `LICENSE`).
