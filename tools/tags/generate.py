#!/usr/bin/env python3
"""Regenerate all four per-animation item tags from a vanilla jar + item registry.

FLIP / FLOURISH below are the hand-curated inputs (plain ids and/or
`#minecraft:...` references, expanded through the jar's own tag data by this
script); everything else is mechanical:

  * `toss`     = every block whose id exists in the item registry (block items,
                 "things you can hold and toss"), minus the curated ones;
  * `flip`     = expanded FLIP (curated mining tools, weapons, ...);
  * `flourish` = expanded FLOURISH (curated swords, ...);
  * `turn`     = the full item registry minus toss minus the curated ones
                 (curated animations win over the default assignment).

The full item registry comes from the community-maintained Mojang data dumps
(misode/mcmeta, tag `<version>-registries`, file `item/data.min.json`) - the jar
itself only ships tags, not the complete id list. Run once per new Minecraft
version; `--check` verifies the committed files match a fresh regeneration.
Jar source, in order: `--jar PATH`, the jar Loom already downloaded
(`~/.gradle/caches/fabric-loom/**/minecraft-client.jar`), else from Mojang's
manifest. Std-lib only.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TAG_DIR = REPO / "src/main/resources/data/inspect-animations-idle/tags/item"

DEFAULT_MC = "26.3"
VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
REGISTRY_URL = "https://raw.githubusercontent.com/misode/mcmeta/{mc}-registries/item/data.min.json"
MOJANG_CACHE = Path.home() / ".cache/inspect-animations-idle-tags"

# --- hand-curated animation assignments (the only ever-edited part) ---------
FLIP = [
    "#minecraft:enchantable/mining",  # mining tools read better as a flip
    "#minecraft:spears",
    "minecraft:bow",
    "minecraft:crossbow",
    "minecraft:mace",
    "minecraft:trident",
]
FLOURISH = [
    "#minecraft:swords",
]


def warn(message: str) -> None:
    print(f"WARN: {message}", file=sys.stderr)


# ---------------------------------------------------------------------------
# Vanilla tag semantics: value -> (is_tag_ref, key, required), refs recursive
# ---------------------------------------------------------------------------

def normalize(value):
    if isinstance(value, dict):  # {"id": "...", "required": false} - optional refs
        value, required = value.get("id", ""), not value.get("required", True)
    else:
        required = True
    if not isinstance(value, str) or not value:
        warn(f"skipping unparsable tag value {value!r}")
        return None
    is_tag, body = value.startswith("#"), value.lstrip("#")
    if ":" in body:
        ns, _, key = body.partition(":")
        if ns != "minecraft":
            warn(f"skipping non-minecraft value {value!r}")
            return None
    else:
        key = body
    return is_tag, key, required


def make_resolver(item_raw: dict, block_raw: dict):
    """Recursive `#`-reference expansion with a cycle guard, per MC semantics."""
    memo: dict = {}
    in_progress: set = set()

    def resolve(values: list, side: str) -> set:
        table = item_raw if side == "item" else block_raw
        items = set()
        for value in values:
            parsed = normalize(value)
            if parsed is None:
                continue
            is_tag, key, required = parsed
            cell = (side, key)
            if not is_tag:
                items.add(key)
            elif cell in memo:
                items |= memo[cell]
            elif cell in in_progress:
                warn(f"cyclic tag reference ignored: '#minecraft:{key}'")
            elif key not in table:
                if required:
                    warn(f"required reference '#minecraft:{key}' is missing from the vanilla jar")
            else:
                in_progress.add(cell)
                resolved = resolve(table[key], side)
                in_progress.discard(cell)
                memo[cell] = resolved
                items |= resolved
        return items

    return resolve


def item_registry(mc: str) -> set:
    """The complete item id list for `mc` from the community Mojang data dumps.

    The jar ships tags only, not the registries; without this list the
    turn/toss rules could not know which block ids have item forms.
    """
    url = REGISTRY_URL.format(mc=mc)
    print(f"fetching item registry: {url} ...")
    try:
        return set(json.loads(urllib.request.urlopen(url).read()))
    except OSError as error:
        raise SystemExit(f"cannot fetch the item registry for {mc}: {error}\n"
                         f"(source: {url})") from error


def generate(item_raw: dict, block_raw: dict, items: set) -> dict[str, set]:
    resolve = make_resolver(item_raw, block_raw)
    flip = resolve(FLIP, "item")
    flourish = resolve(FLOURISH, "item")
    curated = flip | flourish
    blocks = set().union(*(resolve(values, "block") for values in block_raw.values())) & items
    toss = blocks - curated
    turn = items - toss - curated
    print(f"item tags: {len(item_raw)}, block tags: {len(block_raw)}, item registry: {len(items)}")
    print(f"flip: {len(flip)}, flourish: {len(flourish)}; "
          f"turn: {len(turn)}, toss: {len(toss)}")
    return {"flip": flip, "flourish": flourish, "toss": toss, "turn": turn}


def load_tags(jar: Path) -> tuple[dict, dict]:
    """Read every vanilla tag JSON out of the jar, in memory."""
    item_raw, block_raw = {}, {}
    with zipfile.ZipFile(jar) as zf:
        for name in zf.namelist():
            side = "item" if "/tags/item/" in name else "block" if "/tags/block/" in name else None
            if side and name.endswith(".json"):
                key = name.removeprefix(f"data/minecraft/tags/{side}/").removesuffix(".json")
                values = json.loads(zf.read(name)).get("values", [])
                (item_raw if side == "item" else block_raw)[key] = values
    return item_raw, block_raw


def write(name: str, items: set) -> None:
    path = TAG_DIR / f"{name}.json"
    values = sorted("minecraft:" + i for i in items)  # full ids: vanilla 26.x style
    path.write_text(json.dumps({"replace": False, "values": values}, indent="  ") + "\n",
                    encoding="utf-8")
    print(f"wrote {path.relative_to(REPO)} ({len(values)} values)")


def acquire_jar(mc: str, jar_path: str | None) -> Path:
    """--jar argument, then the Loom cache, then Mojang's manifest (cached)."""
    if jar_path:
        path = Path(jar_path).expanduser()
        if not path.exists():
            raise SystemExit(f"jar not found: {path}")
        return path
    loom = Path.home() / ".gradle" / "caches" / "fabric-loom"
    exact = loom / mc / "minecraft-client.jar"
    if exact.exists():
        return exact
    cached = sorted(loom.rglob("minecraft-client.jar"), key=lambda p: p.stat().st_mtime)
    if cached:
        return cached[-1]
    cache_file = MOJANG_CACHE / mc / "client.jar"
    if cache_file.exists():
        return cache_file
    manifest = json.load(urllib.request.urlopen(VERSION_MANIFEST))
    entry = next((v for v in manifest["versions"] if v["id"] == mc), None)
    if entry is None:
        raise SystemExit(f"Minecraft version {mc!r} not found in Mojang's manifest")
    meta = json.load(urllib.request.urlopen(entry["url"]))
    url = meta["downloads"]["client"]["url"]
    print(f"downloading {mc} client jar (~25 MB) ...")
    cache_file.parent.mkdir(parents=True, exist_ok=True)
    part = cache_file.with_suffix(".part")
    urllib.request.urlretrieve(url, part)  # no ~25 MB chunks -> plain retrieve is fine
    shutil.move(part, cache_file)
    return cache_file


def main() -> int:
    parser = argparse.ArgumentParser(description="Regenerate the per-animation item tags")
    parser.add_argument("--mc", default=DEFAULT_MC, help=f"Minecraft version (default {DEFAULT_MC})")
    parser.add_argument("--jar", help="explicit path to a vanilla client jar")
    parser.add_argument("--check", action="store_true",
                        help="exit 1 if the committed tag files differ from a regeneration")
    args = parser.parse_args()

    jar = acquire_jar(args.mc, args.jar)
    print(f"source jar: {jar}")
    item_raw, block_raw = load_tags(jar)
    if not item_raw:
        raise SystemExit("no data/minecraft/tags/item in the jar - not a vanilla client jar?")
    tags = generate(item_raw, block_raw, item_registry(args.mc))
    if args.check:
        for name, items in tags.items():
            path = TAG_DIR / f"{name}.json"
            committed = set(json.loads(path.read_text()).get("values", [])) if path.exists() else set()
            if committed != set("minecraft:" + i for i in items):
                warn(f"{name}.json differs from regeneration")
                return 1
        return 0
    for name, items in tags.items():
        if not items:
            raise SystemExit(f"empty {name} tag - rules or curated inputs are broken?")
        write(name, items)
    return 0


if __name__ == "__main__":
    sys.exit(main())
