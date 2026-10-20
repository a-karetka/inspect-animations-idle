# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.2] - 2026-10-20

### Added

- AGENTS.md.

### Changed

- Fixed the publishing of JAR files to GitHub.
- Icon size reduced.
- Fixed the version check for Inspect Animations.

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

[Unreleased]: https://github.com/a-karetka/inspect-animations-idle/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/a-karetka/inspect-animations-idle/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/a-karetka/inspect-animations-idle/releases/tag/v1.0.0
