#!/usr/bin/env python3
"""Сверка ключа подписи с прошлым выпуском.

Если ключ сменился, друзья не смогут поставить обновление поверх (Android отклонит
APK с другой подписью). Отпечаток ключа записан в описании каждого выпуска;
перед публикацией нового сравниваем его с отпечатком предыдущего. Главным считается файл в репозитории
(`release/key-fingerprint.txt`): описание выпуска можно поправить руками, файл меняется только коммитом.

Использование: key_continuity.py --current-tag v0.0.2 --cert-sha256 <hex> [--pinned-file release/key-fingerprint.txt]
"""
import argparse
import json
import re
import subprocess
import sys

MARKER = "Отпечаток ключа подписи (SHA-256)"


def fingerprint_from_notes(notes: str) -> str | None:
    """Достаёт отпечаток ключа из описания выпуска."""
    for line in notes.splitlines():
        if MARKER in line:
            match = re.search(r"\b[0-9a-f]{64}\b", line)
            if match:
                return match.group(0)
    return None


def pinned_fingerprint(text: str) -> str | None:
    """Отпечаток из файла репозитория: строка из 64 шестнадцатеричных знаков, строки с # пропускаются."""
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("#"):
            continue
        if re.fullmatch(r"[0-9a-f]{64}", line):
            return line
    return None


def verdict(previous_tag: str | None, previous_notes: str, current: str, pinned: str | None = None) -> tuple[str, str]:
    """Возвращает (уровень, сообщение): уровень ok, warning или error."""
    if pinned is not None and pinned != current:
        return "error", (
            f"В release/key-fingerprint.txt записан ключ {pinned}, а выпуск собран ключом {current}. "
            "Друзья не смогут поставить обновление поверх. Проверьте секреты подписи; если ключ менялся осознанно, "
            "поправьте файл отдельным коммитом."
        )
    if previous_tag is None:
        return "ok", f"Первый выпуск, сверять не с чем. Отпечаток ключа: {current}"
    previous = fingerprint_from_notes(previous_notes)
    if previous is None:
        if pinned is not None:
            return "ok", f"Ключ совпадает с release/key-fingerprint.txt (в описании {previous_tag} отпечатка нет)."
        return "warning", f"В выпуске {previous_tag} нет отпечатка ключа, сверка пропущена."
    if previous != current:
        return "error", (
            f"Выпуск {previous_tag} подписан ключом {previous}, а этот собран ключом {current}. "
            "Друзья не смогут поставить обновление поверх. Проверьте секреты подписи."
        )
    return "ok", f"Ключ совпадает с выпуском {previous_tag}."


def gh(*args: str) -> str:
    return subprocess.run(["gh", *args], capture_output=True, text=True, check=True).stdout


def previous_release(current_tag: str) -> tuple[str | None, str]:
    releases = json.loads(gh("release", "list", "--exclude-drafts", "--limit", "5", "--json", "tagName"))
    for release in releases:
        if release["tagName"] != current_tag:
            notes = json.loads(gh("release", "view", release["tagName"], "--json", "body"))["body"]
            return release["tagName"], notes
    return None, ""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--current-tag", required=True)
    parser.add_argument("--cert-sha256", required=True)
    parser.add_argument("--pinned-file")
    args = parser.parse_args()
    pinned = None
    if args.pinned_file:
        with open(args.pinned_file, encoding="utf-8") as handle:
            pinned = pinned_fingerprint(handle.read())
        if pinned is None:
            print(f"::error title=Сверка ключа::В {args.pinned_file} нет отпечатка (64 шестнадцатеричных знака).")
            return 1

    tag, notes = previous_release(args.current_tag)
    level, message = verdict(tag, notes, args.cert_sha256, pinned)
    if level == "ok":
        print(message)
        return 0
    print(f"::{level} title=Сверка ключа::{message}")
    return 1 if level == "error" else 0


if __name__ == "__main__":
    sys.exit(main())
