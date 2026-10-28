#!/usr/bin/env python3
"""Проверка собранного APK: манифест (aapt2) и подпись (apksigner).

Сравнивает фактическое содержимое APK с ожидаемым и падает при любом расхождении.
Итог выводится в журнал и в аннотацию GitHub; отпечаток ключа подписи
пишется в $GITHUB_OUTPUT как cert_sha256.

Пример:
  inspect_apk.py app-release.apk --package io.github.dumahuch.pogodaapp \
      --version-name 0.0.2 --version-code 2 --min-sdk 36 --target-sdk 36 --require-signature
"""
import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

# Разрешение, которое AndroidX добавляет сам (защита приёмников от чужих приложений).
AUTO_PERMISSION_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
EXPECTED_LOCALES = {"de", "ru"}


def find_build_tools() -> Path:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    candidates = [
        d for d in (Path(sdk) / "build-tools").glob("*")
        if (d / "aapt2").exists() and (d / "apksigner").exists()
    ]
    if not candidates:
        sys.exit("::error::Не найден build-tools с aapt2 и apksigner (ANDROID_HOME не задан?)")
    return max(candidates, key=lambda d: tuple(int(x) for x in re.findall(r"\d+", d.name)))


def run(cmd: list[str]) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)}\n{result.stdout}\n{result.stderr}")
    return result.stdout


def find(pattern: str, text: str, what: str) -> re.Match:
    match = re.search(pattern, text, re.M)
    if match is None:
        raise RuntimeError(f"В выводе aapt2 не найдено: {what}\n--- вывод (начало) ---\n{text[:1500]}")
    return match


def icon_has_monochrome(aapt2: Path, apk: str, badging: str) -> bool:
    icon = find(r"^application: label='[^']*' icon='([^']+)'", badging, "иконка приложения").group(1)
    tree = run([str(aapt2), "dump", "xmltree", "--file", icon, apk])
    return "monochrome" in tree


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("--package", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--min-sdk", required=True, type=int)
    parser.add_argument("--target-sdk", required=True, type=int)
    parser.add_argument("--allow-permission", action="append", default=[])
    parser.add_argument("--require-signature", action="store_true")
    args = parser.parse_args()

    tools = find_build_tools()
    aapt2 = tools / "aapt2"
    problems: list[str] = []
    lines: list[str] = []

    try:
        badging = run([str(aapt2), "dump", "badging", args.apk])
        pkg = find(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, "строка package")
        name, code, vname = pkg.group(1), int(pkg.group(2)), pkg.group(3)
        min_sdk = int(find(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", badging, "minSdkVersion").group(1))
        target_sdk = int(find(r"^targetSdkVersion:'(\d+)'", badging, "targetSdkVersion").group(1))
        launcher = find(r"^launchable-activity: name='([^']+)'", badging, "launchable-activity").group(1)
        locales_line = find(r"^locales:(.*)$", badging, "locales").group(1)
        locales = set(re.findall(r"'([^']+)'", locales_line))
        permissions = sorted(re.findall(r"^uses-permission: name='([^']+)'", badging, re.M))
        monochrome = icon_has_monochrome(aapt2, args.apk, badging)
    except RuntimeError as error:
        print(f"::error title=Разбор APK::{str(error).replace('%', '%25').replace(chr(10), '%0A')}")
        return 1

    lines += [
        f"пакет: {name}",
        f"версия: {vname} (код {code})",
        f"minSdk: {min_sdk}, targetSdk: {target_sdk}",
        f"запуск: {launcher}",
        f"языки: {sorted(locales)}",
        f"разрешения: {permissions or 'нет'}",
        f"одноцветная иконка: {'есть' if monochrome else 'НЕТ'}",
    ]

    if name != args.package:
        problems.append(f"пакет {name}, ожидался {args.package}")
    if vname != args.version_name:
        problems.append(f"versionName {vname}, ожидалась {args.version_name}")
    if code != args.version_code:
        problems.append(f"versionCode {code}, ожидался {args.version_code}")
    if min_sdk != args.min_sdk:
        problems.append(f"minSdk {min_sdk}, ожидался {args.min_sdk}")
    if target_sdk != args.target_sdk:
        problems.append(f"targetSdk {target_sdk}, ожидался {args.target_sdk}")
    if not EXPECTED_LOCALES <= locales:
        problems.append(f"в APK нет языков {sorted(EXPECTED_LOCALES - locales)}")
    allowed = set(args.allow_permission)
    extra = [p for p in permissions if p not in allowed and not p.endswith(AUTO_PERMISSION_SUFFIX)]
    if extra:
        problems.append(f"лишние разрешения: {extra}")
    if not monochrome:
        problems.append("в иконке нет слоя monochrome")

    cert_sha256 = ""
    if args.require_signature:
        try:
            report = run([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", args.apk])
        except RuntimeError as error:
            problems.append(f"подпись не прошла проверку:\n{error}")
            report = ""
        v2 = "(APK Signature Scheme v2): true" in report
        v3 = "(APK Signature Scheme v3): true" in report
        cert = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})", report)
        cert_sha256 = cert.group(1) if cert else ""
        lines.append(f"подпись: v2={v2}, v3={v3}, отпечаток SHA-256: {cert_sha256 or 'не найден'}")
        if report and not (v2 and v3 and cert_sha256):
            problems.append("подпись есть, но нет схем v2/v3 или отпечатка ключа")

    summary = "\n".join(lines)
    print(summary)
    output = os.environ.get("GITHUB_OUTPUT")
    if output and cert_sha256:
        with open(output, "a", encoding="utf-8") as handle:
            handle.write(f"cert_sha256={cert_sha256}\n")

    if problems:
        text = "\n".join(problems)
        print(f"::error title=APK не прошёл проверку::{text.replace('%', '%25').replace(chr(10), '%0A')}")
        return 1
    print(f"::notice title=APK прошёл проверку ({Path(args.apk).name})::{summary.replace('%', '%25').replace(chr(10), '%0A')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
