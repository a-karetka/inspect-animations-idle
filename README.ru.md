# Inspect Animations Fix

[English](README.md) | Русский

Клиентский мод для Fabric (Minecraft 26.3), расширяющий [Inspect Animations](https://modrinth.com/mod/inspect-animations) **авто-анимациями при бездействии**: если ничего не делать заданное время, мод сам проигрывает случайное из двух действий Inspect Animations - текущую выбранную анимацию (как при нажатии **R**) или анимацию **Turn**.

Бездействие сбрасывается следующими действиями игрока:

- Использование предмета (**ЛКМ** или **ПКМ**)
- Смена предмета (**цифры 1-9**, **колёсико мыши**, **свап в offhand**)

Настройки доступны через Mod Menu: включение/выключение и таймаут бездействия (1-180 сек). Настройка хранится в `config/inspect-animations-fix.json`.

### Требования

- [Inspect Animations](https://modrinth.com/mod/inspect-animations) от **[SoundsoftheSun](https://codeberg.org/SoundsoftheSun/inspect-animations)** - мод подыгрывает его клавишам. Без него авто-анимации отключаются. Поддерживаемые версии: **0.2.5 и любые более поздние 0.2.x** (`^0.2.5`); на остальных версиях функция бездействия отключается с записью в лог.
- [Mod Menu](https://modrinth.com/mod/modmenu) от **TerraformersMC** (опционально) - только для экрана настроек.

Мод интегрируется с Inspect Animations через отказоустойчивый рантайм-зонд: если установленная версия не содержит ожидаемых методов, мод отключает сам себя с записью в лог, не падая.

### Теги предметов

Мод также добавляет четыре тега предметов - по одному на каждую анимацию Inspect Animations, чтобы назначать предметам анимацию простым тегированием:

| Тег | Анимация |
| --- | --- |
| `inspect-animations-fix:turn` | Turn |
| `inspect-animations-fix:toss` | Toss |
| `inspect-animations-fix:flip` | Flip |
| `inspect-animations-fix:flourish` | Flourish |

Чтобы использовать: откройте конфиг Inspect Animations (**Animations for specific items**) и добавьте тег к нужной анимации с префиксом `#`:

- Turn: `#inspect-animations-fix:turn`
- Toss: `#inspect-animations-fix:toss`
- Flip: `#inspect-animations-fix:flip`
- Flourish: `#inspect-animations-fix:flourish`

Каждый предмет из файла тега будет проигрывать эту анимацию при осмотре - в том числе через авто-анимации при бездействии. В файлах тегов можно перечислять прямые id предметов (`minecraft:diamond_sword`) или ссылки на другие теги с префиксом `#` (например `#minecraft:swords`).

### Сборка

Нужен JDK 25:

```sh
./gradlew build
```

Готовый jar появится в `build/libs/`.

### Лицензия

Распространяется по [лицензии MIT](LICENSE).
