# Inspect Animations Idle

[English](README.md) | Русский

[![Build](https://github.com/a-karetka/inspect-animations-idle/actions/workflows/build.yml/badge.svg)](https://github.com/a-karetka/inspect-animations-idle/actions/workflows/build.yml)

Клиентский Fabric-мод для Minecraft 26.3, расширяющий [Inspect Animations](https://modrinth.com/mod/inspect-animations) **авто-анимациями при бездействии**: постойте без дела заданное время, и мод сам проиграет одно из действий Inspect Animations - вашу выбранную анимацию (как по нажатию **R**) или **Turn**.

Обычные действия - использование предметов, смена предметов, замена предмета в руке - сбрасывают таймер, поэтому анимации включаются только когда вы действительно бездействуете. Оба таймаута, этот список действий и несколько опциональных можно изменить.

## Настройки

Мод настраивается через **Mod Menu**.

## Требования

- [Inspect Animations](https://modrinth.com/mod/inspect-animations) от **SoundsoftheSun** - **обязателен**.
- [YetAnotherConfigLib](https://modrinth.com/mod/yacl) - **обязателен**.
- [Mod Menu](https://modrinth.com/mod/modmenu) - опционально (без него экран настроек недоступен).

## Свои анимации через теги предметов

Мод добавляет четыре тега предметов - по одному на каждую анимацию Inspect Animations. Предметы, помещённые в тег, проигрывают эту анимацию при осмотре, включая авто-анимации при бездействии:

| Тег | Анимация |
| --- | --- |
| `inspect-animations-idle:turn` | Turn |
| `inspect-animations-idle:toss` | Toss |
| `inspect-animations-idle:flip` | Flip |
| `inspect-animations-idle:flourish` | Flourish |

## Скачивание

Скачайте jar со страницы [GitHub Releases](https://github.com/a-karetka/inspect-animations-idle/releases) или с [Modrinth](https://modrinth.com/mod/inspect-animations-idle) и положите его в папку `mods` вместе с зависимостями.
