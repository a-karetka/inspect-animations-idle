# Inspect Animations Idle

English | [Русский](README.ru.md)

[![Build](https://github.com/a-karetka/inspect-animations-idle/actions/workflows/build.yml/badge.svg)](https://github.com/a-karetka/inspect-animations-idle/actions/workflows/build.yml)

A client-side Fabric mod for Minecraft 26.3 that expands [Inspect Animations](https://modrinth.com/mod/inspect-animations) with **idle auto-animations**: if you do nothing for a configurable amount of time, the mod automatically plays one of the Inspect Animations actions at random - either the currently selected animation (the same thing that happens when you press **R**) or the **Turn** animation.

Inactivity is reset by any of the following player actions:

- Using an item (**left click** or **right click**)
- Switching items (**hotbar keys 1-9**, **mouse wheel**, **offhand swap** with F)

Everything is configurable from the Mod Menu config screen: the feature can be toggled and the idle timeout can be changed (1-180 seconds). The setting is stored in `config/inspect-animations-idle.json`.

## Requirements

- [Inspect Animations](https://modrinth.com/mod/inspect-animations) by **[SoundsoftheSun](https://codeberg.org/SoundsoftheSun/inspect-animations)** - a **required dependency**, the mod will not load without it. Supported versions: **0.2.5 and any later 0.2.x** (`^0.2.5`); on an unsupported version the game refuses to start with a clear loader message about the version mismatch.
- [Mod Menu](https://modrinth.com/mod/modmenu) by **TerraformersMC** (optional) - used only to expose the config screen.

This mod integrates with Inspect Animations via a fail-safe runtime probe: if the version installed does not provide the expected methods, the mod disables itself and logs a warning instead of crashing.

## Item tags

The mod also ships four item tags - one per Inspect Animations animation - so items can be assigned an animation by simply tagging them:

| Tag | Animation |
| --- | --- |
| `inspect-animations-idle:turn` | Turn |
| `inspect-animations-idle:toss` | Toss |
| `inspect-animations-idle:flip` | Flip |
| `inspect-animations-idle:flourish` | Flourish |

To use them, open the Inspect Animations config (**Animations for specific items**) and add the tag for the animation you want, prefixed with `#`:

- Turn: `#inspect-animations-idle:turn`
- Toss: `#inspect-animations-idle:toss`
- Flip: `#inspect-animations-idle:flip`
- Flourish: `#inspect-animations-idle:flourish`

Every item you add to a tag file plays that animation when inspected - including the idle auto-animations triggered by this mod. Tag files can contain plain item IDs (`minecraft:diamond_sword`) or references to other tags prefixed with `#` (for example `#minecraft:swords`).