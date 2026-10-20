# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Multi-version support: one shared codebase (Stonecutter) now builds a matching jar for
  the 26.1 patch line (26.1, 26.1.1, 26.1.2 - one file, built from 26.1.2 as far as
  upstream's own jars are concerned), 26.2 and 26.3 - every version Inspect
  Animations itself supports on Fabric. Release jars are named
  `inspect-animations-idle-<version>+<mc>.jar`; a GitHub release carries all of them.
- Dependency versions, MC compatibility ranges and the version set now live in
  `stonecutter.properties.toml` (one section per game version); adding a future
  Minecraft version is a config entry plus a tag regeneration.
- CI builds every supported version and publishes all per-version jars to the release.

### Changed

- The item tag generator writes its output per Minecraft version
  (`src/generated/tags/<mc>/`) and takes the matching vanilla jar for that version
  (via `--mc`); it no longer silently reuses a cached jar of a different game version.

### Fixed

- GitHub Releases now carry the matching CHANGELOG section as their description and
  exclude the dev-only `-sources` jars from the published assets (the game-install
  jars are unaffected; 1.1.0 published sources jars and shipped an empty description).

## [1.1.0] - 2026-10-20

### Added

- Regeneration tooling for the item tags: `tools/tags/generate.py` writes all four
  per-animation tags (`turn`, `toss`, `flip`, `flourish`) from any vanilla jar (default:
  the one Loom already has cached, otherwise downloaded from Mojang). The curated
  assignments live in the script's `FLIP`/`FLOURISH` lists and accept `#` tag references,
  which the script expands through vanilla data - so curated tags stay valid across
  Minecraft updates. `toss` lists every block that exists as an item; `turn` covers the
  rest of the item registry (minus the curated ones). The registry dump is fetched from
  misode/mcmeta (`<version>-registries`). Run it after every Minecraft version bump;
  `--check` verifies the committed files match the script.
- Per-trigger switches: every inactivity reset source can now be turned off individually
  in the config screen. Defaults: all six action triggers (slot switching, item use,
  attack presses and held attacks, offhand swap, held-item replacement) are on - they only
  fire on real player activity; the state triggers default to off so existing users see
  no new resets.
- New inactivity triggers (freely combinable; the state ones default to off):
  - replacing the item held in the selected hotbar slot or the offhand slot (any pickup,
    placement, drop or inventory click that changes the held stack);
  - sprinting, walking/moving, sneaking;
  - camera yaw turns beyond a configurable per-tick threshold (default 15°/tick);
  - creative/spectator flight and elytra gliding.
  State triggers are sampled once every 4 ticks to keep the per-tick cost minimal;
  very short stints (shorter than 4 ticks) may miss a reset by design.
- Two-tier timing: a *first* timeout charged after any activity, and a *subsequent* timeout
  charged after each fired auto-animation. Both are 1-120 s sliders, independent.
- Defaults retuned: 20 s first / 10 s subsequent timeouts (was one shared 16 s), and all
  six action triggers default to on; the state triggers stay off by default.
- Per-parameter "reset to default" button next to every setting in the config screen.
- Rebuilt Mod Menu config screen with **YetAnotherConfigLib** - the same config library and
  visual style Inspect Animations builds its own menu on: two tabs (basics + an
  "Experimental functions" tab for the triggers), scrollable option list, a description
  under every setting's name, and YACL's built-in per-option reset buttons. YACL is
  compile-only, never bundled, and declared as a hard dependency in `fabric.mod.json`.

### Changed

- Config file: `idleSeconds` is migrated to `firstSeconds`; `nextSeconds`, `headTurnDegrees`
  and the twelve `reset*` switches were added (missing keys fall back to defaults, so
  1.0.x configs load unchanged and stay editable by hand).
- In-game behaviour when menus/pause screens are open is unchanged: the timer keeps running,
  triggers are skipped; state triggers do not react to menu activity.
- AGENTS.md: architecture, tick budget and trigger model updated for 1.1.0; agent-hygiene
  rules added (explicit working directories for scratch commands, `git restore` over
  re-creation, changelog placement under `[Unreleased]`).

## [1.0.2] - 2026-10-20

### Added

- AGENTS.md.

### Changed

- Icon size reduced.

### Fixed

- The publishing of JAR files to GitHub.
- The version check for Inspect Animations.

## [1.0.1] - 2026-10-19

### Changed

- **Inspect Animations is now a required dependency** (moved from `recommends` to `depends`):
  the game refuses to start without it, with a clear message from the loader, instead of
silently running without the idle feature. Unsupported versions are rejected at startup
  by the loader instead of a runtime log note.
- Mod Menu is now a compile-time-only dependency (`compileOnly`): it is no longer pulled
  into every development run; at runtime it stays optional as before.
- Idle timeout is cached in ticks and refreshed when the config is loaded, saved or changed
  in the Mod Menu screen; the per-tick path only compares, never recomputes.

## [1.0.0] - 2026-10-17

### Added

- Idle auto-animations: after a configurable inaction timeout (1-180 seconds), a random
  Inspect Animations action plays - either the currently selected animation (as with `R`)
  or the `Turn` animation.
- Inactivity reset on item use (left/right click) and item switching (hotbar keys 1-9,
  mouse wheel, offhand swap with F).
- Config stored in `config/inspect-animations-idle.json`, editable via the Mod Menu config screen (toggle + timeout slider).
- Failsafe integration with Inspect Animations 0.2.x (`^0.2.5`): the feature disables itself with a logged warning on incompatible versions instead of crashing.
- Four item tags - one per Inspect Animations animation (`turn`, `toss`, `flip`, `flourish`)
  - for per-animation item tagging in the Inspect Animations config.
- Mod Menu integration (optional dependency).

[Unreleased]: https://github.com/a-karetka/inspect-animations-idle/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/a-karetka/inspect-animations-idle/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/a-karetka/inspect-animations-idle/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/a-karetka/inspect-animations-idle/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/a-karetka/inspect-animations-idle/releases/tag/v1.0.0
