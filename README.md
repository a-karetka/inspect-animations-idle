# Inspect Animations Idle

English | [Русский](README.ru.md)

[![Build](https://github.com/a-karetka/inspect-animations-idle/actions/workflows/build.yml/badge.svg)](https://github.com/a-karetka/inspect-animations-idle/actions/workflows/build.yml)

A client-side Fabric mod for Minecraft 26.3 that expands [Inspect Animations](https://modrinth.com/mod/inspect-animations) with **idle auto-animations**: stay idle for a set amount of time, and the mod automatically plays one of the Inspect Animations actions - your currently selected animation (like pressing **R**) or the **Turn** animation.

Ordinary actions - using items, switching items, replacing the held item - reset the timer, so animations play only while you are actually doing nothing. Both timeouts, that list of actions and a few optional ones are adjustable.

## Settings

The mod can be configured through **Mod Menu**.

## Requirements

- [Inspect Animations](https://modrinth.com/mod/inspect-animations) by **SoundsoftheSun** - **required**.
- [YetAnotherConfigLib](https://modrinth.com/mod/yacl) - **required**.
- [Mod Menu](https://modrinth.com/mod/modmenu) - optional (the config screen exists only with Mod Menu installed).

## Custom animations via item tags

The mod adds four item tags - one per Inspect Animations animation. Items placed into a tag play that animation on inspection, including the idle auto-animations:

| Tag | Animation |
| --- | --- |
| `inspect-animations-idle:turn` | Turn |
| `inspect-animations-idle:toss` | Toss |
| `inspect-animations-idle:flip` | Flip |
| `inspect-animations-idle:flourish` | Flourish |

## Downloading

Grab the latest jar from the [GitHub Releases](https://github.com/a-karetka/inspect-animations-idle/releases) or from [Modrinth](https://modrinth.com/mod/inspect-animations-idle) and drop it into your `mods` folder along with its dependencies.
