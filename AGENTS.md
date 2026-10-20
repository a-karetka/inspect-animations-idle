# AGENTS.md — project guide for coding agents

Guidance for AI agents (and humans) working in this repository. Read it before making changes.

## What this project is

**Inspect Animations Idle** — a client-only Fabric addon mod for Minecraft 26.3 (Java 25),
extending the third-party mod [Inspect Animations](https://codeberg.org/SoundsoftheSun/inspect-animations)
with idle auto-animations: after a configurable inaction timeout it plays one of two actions
(the currently selected animation, as if `R` was pressed, or the `Turn` animation).

Key property: it integrates with a mod it does not control, through a **fail-safe reflection probe**
— never through a hard compile-time API, because upstream has no public API.

## Repository layout

- **All versions live in `gradle.properties` only** (`version`, `minecraft_version`, `loader_version`, `loom_version`, `fabric_api_version`, `modmenu_version`); `build.gradle` wires them in.
- `src/main/java` holds only the common entrypoint (`com.github.akaretka.InspectAnimationsIdle`, the shared `LOGGER`) — the mod is client-only (`"environment": "client"` in `fabric.mod.json`); never add server-side logic there.
- `src/client/java/com/github/akaretka/client/` holds all runtime logic: `mixin/` (vanilla hooks), `idle/` (`IdleInspector`, `IdleConfig`), `screen/`, the client entrypoint and Mod Menu integration.
- Dependency model in `src/main/resources/fabric.mod.json`: `inspectanimations ^0.2.5` is a *hard* `depends`; `modmenu` is `suggests` (optional).
- Mixin config `src/client/resources/inspect-animations-idle.client.mixins.json` uses `defaultRequire: 1` — injection failures crash the game. Targets `startUseItem`/`startAttack`/`continueAttack`/`setSelectedSlot` are `private` vanilla methods; mixin handles private targets fine.
- Item tags in `src/main/resources/data/inspect-animations-idle/tags/item/` (`turn`, `toss`, `flip`, `flourish`) are consumed by *Inspect Animations'* config, not by this mod.
- `README.md` / `README.ru.md`, `CHANGELOG.md` (Keep a Changelog format), CI in `.github/workflows/build.yml` — the update rules are in the sections below.

## Architecture: how the pieces connect

```
vanilla Minecraft ──mixins──▶ IdleInspector.onActivity()  (reset counter)
   InventoryMixin  ▶ Inventory#setSelectedSlot   (hotbar keys 1-9, wheel, programmatic)
   MinecraftMixin  ▶ startUseItem (RMB) / startAttack (LMB) / continueAttack (held LMB)

per-tick (END_CLIENT_TICK) ▶ IdleInspector.tick()
   hot path: read enabled ▸ keySwapOffhand.isDown() ▸ ++idleTicks vs IdleConfig.intervalTicks
   trigger path (once per timeout): reset counter ▸ skip if player/level null or paused
     ▸ resolveActions() once (reflection probe) ▸ invoke upstream AnimationHelper.sendAnimation

IdleConfig.apply(seconds)  — the single entry point for specifying the timeout;
   clamps into [MIN_IDLE_SECONDS; MAX_IDLE_SECONDS] and refreshes the cached
   intervalTicks (= seconds * 20) so readers never recompute per tick.
```

- `IdleInspector` resolves upstream `net.soundsofthesun.inspectanimations.client.AnimationHelper#sendAnimation`
  by **signature shape** (`static void (LocalPlayer)` and `static void (LocalPlayer, Animation)`),
  selected by name, and caches `Method` handles + the `TURN` enum constant. Any failure
  (mod absent, unsupported version, missing class/methods, invocation error) is routed to
  `disable(...)` — one warn log line, then the feature is off for the session. Keep it this way.
- The version gate `>=0.2.5, <0.3.0` mirrors the `depends` range; it stays as a defensive net.
- Minecraft 26.3 ships **unobfuscated** — vanilla class/method names in mixins are runtime
  names directly; there is no refmap/intermediary remapping. Do not add refmap configs.

## Performance invariants (do not regress)

The mod must remain practically resource-free. The per-tick hot path performs:
one static field read, one `KeyMapping.isDown()`, one counter increment, one comparison —
and nothing else. No allocations, no I/O, no per-tick computation.

- Never add per-tick work to `IdleInspector.tick()` beyond counter/compare logic.
- Config values used per tick must be precomputed on change (`IdleConfig.apply()`), not recalculated.
- Mixin hooks must stay a single `static` call; verify with `javap` on the built jar when in doubt.

## How to make and verify changes

1. Build: `./gradlew build` (artifacts land in `build/libs/inspect-animations-idle-<version>.jar` —
   the plain one is the release jar; `-sources` is optional).
2. Inspect bytecode when touching hot paths or mixins:
   `unzip build/libs/*.jar && javap -c -p <class>`.
3. Local game run: `./gradlew runClient`. Notes:
   - Since `inspectanimations` is a hard dependency, put its jar into `libs/`,
     uncomment `flatDir { dirs 'libs' }` and the `localRuntime` line in `build.gradle`;
     otherwise resolution fails by design.
   - Mod Menu is `compileOnly` — the config screen only exists when Mod Menu is present;
     to test it locally, add its jar to `run/mods/`.
   - Without Inspect Animations installed, the launch is *expected to fail* (hard `depends`).
4. Java version: 25 (toolchain pins `options.release = 25`). Loom 1.18 has no
   `modImplementation`/`modCompileOnly` configurations — use plain `implementation`,
   `compileOnly`, `localRuntime`; a mod.jar in those configs is auto-detected.

## Code style and comment policy

The dominating constraint of this codebase: **it must be practically free at runtime.**
Write every new piece of code with that in mind, not just existing files.

**Performance thinking (before writing anything):**
- Ask yourself where the code runs: per tick (runs ~1 200 times a minute), per player
  action, per trigger, or once per session. Push cost to the coldest frequency possible;
  precompute on change (`IdleConfig.apply()` style), never recompute in a hot path.
- The per-tick path allows only: field reads, comparisons, arithmetic. Allocations,
  string building, I/O, reflection, `Optional`/streams are off-limits there.
- A feature that adds any per-tick cost must be avoided; if it is unavoidable, it must be
  guarded behind a flag already read in the tick path and feature-gated in the config.
- "It's just one call" is not an argument: this codebase treats five new operations per
  tick as a regression. Measure doubt with `javap` on the built jar.

**Comments (why, not what):**
- Every class and every public method gets Javadoc stating behavior *and rationale*.
  This codebase documents design decisions inside the code (why the probe is
  signature-based, why the offhand key is handled in the tick, why the counter resets
  before trigger). Continue that pattern: extend or update the comment whenever you
  change the behavior it describes — stale `why`-comments are a bug.
- Prefer comments that encode invariants over prose. If a check looks redundant, the
  comment must say why it is not.

**Formatting and naming:**
- Follow the surrounding files exactly: tabs for indentation in Java (mirrors existing
  sources), private static fields for cached state, `final` utility classes.
- Mixin handler methods use the `idle$` prefix naming scheme; constants in
  `UPPER_SNAKE_CASE` with Javadoc.
- Do not introduce new libraries or dependencies: vanilla + Fabric API + the compile-time
  Mod Menu API are all the project uses; a feature that needs more should be re-thought.
- Failure handling follows the existing fail-safe pattern: log once at warn level and
  degrade (see the `warnedIo`/`disabled` latch style in `IdleConfig`/`IdleInspector`),
  never throw into the game loop.

## What to update when changing behavior

- **Code comments** that explain the relevant invariant (see `IdleInspector`, `InventoryMixin` docstrings).
- **README.md and README.ru.md** — must stay in sync, for any user-visible change.
- **Lang files** — both `en_us.json` and `ru_ru.json` when adding/editing strings.
- **CHANGELOG.md** — add under `[Unreleased]`, grouped as Added/Changed/Fixed, no git internals.
- **`gradle.properties` version** — bump per release (semver: patch = fixes, minor = features,
  major = breaking/target-MC change). Tag names `v<version>`, and CI enforces tag == version.

## Release checklist

1. Bump `version` in `gradle.properties`; move `Unreleased` changelog notes under a dated `## [x.y.z]` section.
2. `./gradlew build` (keep the `-sources` jar out of game installs).
3. Commit, `git tag -a vX.Y.Z`, push with tags.
4. CI publishes the jars to the GitHub Release automatically. Upload the same jar to
   Modrinth manually: version number `X.Y.Z`, loader Fabric, game versions 26.3,
   dependencies: Inspect Animations (required), Fabric API (required), Mod Menu (optional).
5. Verify in CI logs that mixins applied (no injection failures on startup).

## Known pitfalls

- Stale artifacts from renamed eras may remain in `build/libs/` (e.g. old `inspect-animations-fix-*`
  jars); don't confuse them with current output — `gradle clean` removes them.
- Upstream mid-release behavior changes (signature renames in `AnimationHelper`) degrade to
  "idle disabled + log", not crashes — a crash there is always this mod's bug.
- The project uses Fabric Loom's split source sets (`splitEnvironmentSourceSets()`): client
  mixins must stay in the `client` source set, be listed in the mixin config, and the mixin
  config is declared with `"environment": "client"` in `fabric.mod.json`.
