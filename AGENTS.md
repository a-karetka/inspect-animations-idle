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
- Mixin config `src/client/resources/inspect-animations-idle.client.mixins.json` uses `defaultRequire: 1` — injection failures crash the game. Targets `startUseItem`/`startAttack`/`continueAttack`, `Inventory#setSelectedSlot` and `Inventory#setItem` are `private` vanilla methods; mixin handles private targets fine. The `setItem` hook filters slots itself: only the selected hotbar slot and the offhand slot (40) may reach IdleInspector — armor and storage changes are not activity and must stay out.
- Item tags in `src/main/resources/data/inspect-animations-idle/tags/item/` (`turn`, `toss`, `flip`, `flourish`) are consumed by *Inspect Animations'* config, not by this mod.
  All four JSONs are **generated output** of `tools/tags/generate.py` (Python 3 std-lib only):
  the curated animation assignments live in the script's `FLIP`/`FLOURISH` lists (plain ids
  and/or `#` references, expanded through vanilla tag data); `toss` = all block ids that
  exist in the item registry ("every block you can hold"), `turn` = the full item registry
  minus toss minus the curated ones. The item registry itself comes from the community
  Mojang data dumps (misode/mcmeta, tag `<version>-registries`) — the jar ships tags, not
  the complete id list. Regenerate after every `minecraft_version`
  bump — never hand-edit the JSONs, regeneration destroys such edits. Full
  `minecraft:`-prefixed, sorted ids, `replace: false`; `--check` verifies files vs script.
- `README.md` / `README.ru.md`, `CHANGELOG.md` (Keep a Changelog format), CI in `.github/workflows/build.yml` — the update rules are in the sections below.

## Architecture: how the pieces connect

```
vanilla Minecraft ──mixins──▶ IdleInspector.on<Source>()   (flag-gated reset)
   InventoryMixin  ▶ setSelectedSlot → onSlotSwitch (keys 1-9, wheel, programmatic)
                     setItem       → onItemReplace (only slot == selected or slot 40)
   MinecraftMixin  ▶ startUseItem → onItemUse (RMB, re-fires while held)
                     startAttack / continueAttack → onAttack / onAttackHold (LMB)

per-tick (END_CLIENT_TICK) ▶ IdleInspector.tick()
   hot path: read enabled ▸ (resetOffhandSwap + keySwapOffhand.isDown() → armFirstWindow)
      ▸ ++idleTicks vs windowTicks ▸ below window: every 4th tick (STATE_CHECK_MASK)
        run the enabled state triggers (sprint/walk/crouch/head-turn/creative-fly/elytra)
   trigger path (once per window): armFirstWindow (counter 0, first window charged)
      ▸ skip if player/level null or paused ▸ resolveActions() once (reflection probe)
      ▸ invoke upstream AnimationHelper.sendAnimation ▸ only then charge the next window

IdleConfig.applyFirst/applyNext/applyHeadTurn — clamp and refresh the tick-domain caches
   (firstIntervalTicks / nextIntervalTicks / headTurnDegrees); live edits notify
   IdleInspector, so the currently charged window follows without waiting for a reset.
```

- `IdleInspector` resolves upstream `net.soundsofthesun.inspectanimations.client.AnimationHelper#sendAnimation`
  by **signature shape** (`static void (LocalPlayer)` and `static void (LocalPlayer, Animation)`),
  selected by name, and caches `Method` handles + the `TURN` enum constant. Any failure
  (mod absent, unsupported version, missing class/methods, invocation error) is routed to
  `disable(...)` — one warn log line, then the feature is off for the session. Keep it this way.
- The version gate `>=0.2.5, <0.3.0` mirrors the `depends` range; it stays as a defensive net.
- Reset entry points are named per source (`onSlotSwitch`, `onItemUse`, `onAttack`,
  `onAttackHold`, `onItemReplace`), each reading its own config flag. Mixins and the tick
  path call only these — nothing outside `IdleInspector` touches `idleTicks`/`windowTicks`.
- Window semantics (1.1.0): any activity charges the *first* window; only a fired
  auto-trigger charges the *subsequent* window. In menus/pause the counter keeps running
  and the trigger is skipped — deliberate, unchanged since 1.0.x; do not "fix" it.
- Minecraft 26.3 ships **unobfuscated** — vanilla class/method names in mixins are runtime
  names directly; there is no refmap/intermediary remapping. Do not add refmap configs.

## Performance invariants (do not regress)

The mod must remain practically resource-free. The per-tick baseline (as of 1.1.0):
master-switch flag read, counter increment, window compare, mask test — plus, per enabled
trigger, one boolean flag read (offhand adds one `isDown()`); state triggers additionally
pay one primitive condition per enabled trigger only on their 4-tick checkpoint. Nothing
else: no allocations, no I/O, no string building, and no reflection in the tick path
(reflection lives in the one-shot probe, executed off the tick path).

- Never add per-tick work to `IdleInspector.tick()` beyond field reads, comparisons and
  arithmetic. Conditions that *can* be events stay event-driven (mixins); state conditions
  that cannot be events are sampled via `IdleConfig.STATE_CHECK_MASK`, never run every tick.
- Every new per-tick check is feature-gated by its own config flag read in the tick path;
  a disabled trigger costs exactly one boolean read.
- Config values used per tick must be precomputed on change
  (`IdleConfig.applyFirst()`/`applyNext()`/`applyHeadTurn()`), never recalculated.
- Mixin hooks stay flag-read + a single `static` call to a named `on<Source>` entry point;
  verify with `javap` on the built jar when in doubt.
- Widening the tick budget is allowed only when no event-level alternative exists, and must
  update this section and the `tick()` docstring in the **same** change — an undocumented
  budget change counts as a regression whatever its absolute cost.

## How to make and verify changes

1. Build: `./gradlew build` (artifacts land in `build/libs/inspect-animations-idle-<version>.jar` —
   the plain one is the release jar; `-sources` is optional).
2. Inspect bytecode when touching hot paths or mixins — unzip into `build/` (gitignored),
   never into the repository root:
   `unzip build/libs/*.jar -d build/jarcheck && javap -c -p build/jarcheck/<class>`.
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
  precompute on change (`IdleConfig.applyFirst()`-style caches), never recompute in a hot path.
- The per-tick path allows only: field reads, comparisons, arithmetic. Allocations,
  string building, I/O, reflection, `Optional`/streams are off-limits there.
- A feature that adds any per-tick cost must be avoided; if it is unavoidable, it must be
  sampled (`STATE_CHECK_MASK`) and feature-gated behind a flag read in the tick path.
- "It's just one call" is not an argument: the tick budget is a contract. Widening it
  without the documented-deviation note (see «Performance invariants») is a regression even
  when the absolute cost is negligible. Measure doubt with `javap` on the built jar.

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
- Keep the dependency set closed: vanilla + Fabric API + compile-only APIs that are
  guaranteed present at runtime. Two compile-time-only integrations exist: Mod Menu
  (its own entrypoint, loaded only when Mod Menu is installed) and YetAnotherConfigLib
  (the config screen API; compile-only and never bundled, declared as a hard `depends`
  in `fabric.mod.json` - Inspect Animations hard-depends on it too, so real installs
  have it anyway). Anything else must be re-thought.
- Failure handling follows the existing fail-safe pattern: log once at warn level and
  degrade (see the `warnedIo`/`disabled` latch style in `IdleConfig`/`IdleInspector`),
  never throw into the game loop.

## What to update when changing behavior

- **Code comments** that explain the relevant invariant (see `IdleInspector`, `InventoryMixin` docstrings).
- **README.md and README.ru.md** — must stay in sync, for any user-visible change.
  Write for a *player*, not a developer: describe what the mod does, how to configure it,
  requirements and the item tags - and stop there. Leave out implementation details that
  a player does not care about (fail-safe probe mechanics, per-tick sampling, config
  file paths, loader internals, version-range notations) - they live in AGENTS.md,
  Javadoc and CHANGELOG instead.
- **Lang files** — both `en_us.json` and `ru_ru.json` when adding/editing strings.
- **CHANGELOG.md** — add under `[Unreleased]`, grouped as Added/Changed/Fixed, no git internals.
  Entries move from `[Unreleased]` into a dated section only during the release checklist —
  never as part of a feature change, however "done" the feature feels.
- **AGENTS.md** — keep in sync **in the same change** whenever behavior alters the
  architecture, the tick budget, the trigger model or the config format described here;
  a stale guide misleads every agent after you exactly like a stale `why`-comment.
- **`gradle.properties` version** — bump per release (semver: patch = fixes, minor = features,
  major = breaking/target-MC change). Tag names `v<version>`, and CI enforces tag == version.
  The version bump may happen in a feature change; the changelog section rename and its
  compare link follow only at release time.

## Release checklist

1. Bump `version` in `gradle.properties` if it does not already match the release version; move
   `Unreleased` changelog notes under a dated `## [x.y.z]` section (the only legitimate moment
   for that move) and add the matching compare link line below the file.
2. `./gradlew build` (keep the `-sources` jar out of game installs).
3. Commit, `git tag -a vX.Y.Z`, push with tags.
4. CI publishes the jars to the GitHub Release automatically. Upload the same jar to
   Modrinth manually: version number `X.Y.Z`, loader Fabric, game versions 26.3,
   dependencies: Inspect Animations (required), YetAnotherConfigLib (required),
   Fabric API (required), Mod Menu (optional).
5. Verify in CI logs that mixins applied (no injection failures on startup).

## Known pitfalls

- Command hygiene: run every shell command with an explicit working directory; commands
  that unpack or generate files (unzip, class extraction, scratch scripts) belong in a
  temp directory or under gitignored `build/`, never in the repository root — a stray
  `net/minecraft/…` extracted from a vanilla jar looks like source contamination.
- Working-tree truth: HEAD is the source of truth, the working tree is not. If a tracked
  file unexpectedly disappears, `git restore <path>` first and only then investigate;
  never silently "clean up" a versioned guide as part of unrelated work (this cost one
  session a restored AGENTS.md after it had been dropped from the tree).
- Stale artifacts from renamed eras may remain in `build/libs/` (e.g. old `inspect-animations-fix-*`
  jars); don't confuse them with current output — `gradle clean` removes them.
- Upstream mid-release behavior changes (signature renames in `AnimationHelper`) degrade to
  "idle disabled + log", not crashes — a crash there is always this mod's bug.
- The project uses Fabric Loom's split source sets (`splitEnvironmentSourceSets()`): client
  mixins must stay in the `client` source set, be listed in the mixin config, and the mixin
  config is declared with `"environment": "client"` in `fabric.mod.json`.
