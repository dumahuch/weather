#!/usr/bin/env python3
"""Защита от опечатки в номере выпуска.

29.10.2026 в поле «version» набрали 4.2.2 вместо 0.2.2: вышла версия с номером 4002002, поверх неё Android
не ставит версии с меньшим номером, а Obtainium считает её самой новой. Поэтому перед сборкой сверяем номер
с уже выпущенными: он обязан быть следующим шагом после самого высокого из них (+1 к последней цифре,
+1 к средней с нулём в последней, +1 к первой с двумя нулями).

Использование: version_step.py --version 0.2.3 --current-tag v0.2.3
"""
import argparse
import json
import re
import subprocess
import sys

Version = tuple[int, int, int]


def parse(text: str) -> Version | None:
    """Разбирает «1.2.3» или «v1.2.3»; всё остальное (чужие метки) даёт None."""
    match = re.fullmatch(r"v?(\d{1,3})\.(\d{1,3})\.(\d{1,3})", text)
    if match is None:
        return None
    return int(match.group(1)), int(match.group(2)), int(match.group(3))


def show(version: Version) -> str:
    return ".".join(str(part) for part in version)


def next_steps(version: Version) -> list[Version]:
    major, minor, patch = version
    return [(major, minor, patch + 1), (major, minor + 1, 0), (major + 1, 0, 0)]


def verdict(new: Version, released: list[Version]) -> tuple[str, str]:
    """Возвращает (уровень, сообщение): ok или error."""
    if not released:
        return "ok", f"Первый выпуск {show(new)}."
    top = max(released)
    if new in released:
        return "error", f"Выпуск {show(new)} уже есть. Удалите его на GitHub или назовите следующий номер."
    if new < top:
        return "error", (
            f"Номер {show(new)} ниже уже выпущенного {show(top)}: Android не поставит его поверх, а Obtainium "
            f"будет считать новее {show(top)}. Если {show(top)} выпущен по ошибке, удалите его выпуск на GitHub."
        )
    allowed = next_steps(top)
    if new not in allowed:
        options = ", ".join(show(step) for step in allowed)
        return "error", (
            f"После {show(top)} следующим может быть только один из номеров: {options}. "
            f"Набрано {show(new)}: проверьте, нет ли опечатки."
        )
    return "ok", f"Номер {show(new)} следует за {show(top)}."


def released_versions(current_tag: str) -> list[Version]:
    out = subprocess.run(
        ["gh", "release", "list", "--exclude-drafts", "--limit", "200", "--json", "tagName"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    versions = []
    for release in json.loads(out):
        if release["tagName"] == current_tag:
            continue
        version = parse(release["tagName"])
        if version is not None:
            versions.append(version)
    return versions


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--current-tag", required=True)
    args = parser.parse_args()

    new = parse(args.version)
    if new is None:
        print(f"::error title=Номер версии::'{args.version}' должно выглядеть как 1.2.3")
        return 1
    level, message = verdict(new, released_versions(args.current_tag))
    if level == "ok":
        print(message)
        return 0
    print(f"::error title=Номер версии::{message}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
