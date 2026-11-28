#!/usr/bin/env python3
"""Сверка цифр: измеряет проект и сравнивает с тем, что о нём заявлено.

«Заявлено» хранится в release/expected-numbers.json (права, адреса, зависимости, пределы, версии системы).
Новое право, адрес или библиотека останавливают проверку: файл надо поправить осознанно, в том же шаге,
вместе с claude/topics/security.md. Меняющиеся цифры (число файлов, строк) только показываются.

Что измеряется:
  1. Сборка: minSdk, targetSdk, compileSdk, пакет, языки, процессоры; согласие README и DEVELOPMENT.md.
  2. Манифест: права, открытые наружу части, защита служб, резервные копии; те же права в ci.yml и release.yml.
  3. Зависимости: список и лицензии, закреплённые версии, объявленные и неиспользуемые.
  4. Сеть: все адреса в коде, незащищённые адреса, пределы размера, времени и вложенности.
  5. Приватность: округление координат, координаты и названия мест в журнале.
  6. Переводы: ключи, подстановки и множественные числа en/ru/de; русские строки прямо в коде.
  7. Размер кода и тестов; чистота кода (code_hygiene.py).
  8. CI: действия закреплены по хешу, права, сумма Gradle, dependabot.
  9. По запросу: результаты тестов (--junit) и собранный APK (--apk).

Запуск:
  python3 .github/scripts/verify_numbers.py [корень] [--claims файл] [--report файл.md]
      [--junit каталог] [--apk файл.apk --apk-kind debug|release] [--only-built]
Код выхода 1, если есть строка с итогом FAIL. Итог пишется в журнал и в $GITHUB_STEP_SUMMARY.
"""
import argparse
import ast
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from pathlib import Path

OK, FAIL, WARN, INFO, SKIP = "OK", "FAIL", "WARN", "INFO", "SKIP"
MARK = {OK: "✅", FAIL: "❌", WARN: "⚠️", INFO: "ℹ️", SKIP: "⏭️"}
PACKAGE_DIR = "app/src/main/kotlin/io/github/dumahuch/pogodaapp"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
TOOLS_NS = "{http://schemas.android.com/tools}"
LOOPBACK_HOSTS = {"127.0.0.1", "localhost"}

# Где лежат пределы, о которых говорят документы: имя в файле с заявленными цифрами → (файл, константа).
LIMIT_SOURCES = {
    "http_max_body_bytes": ("data/HttpJson.kt", "MAX_BODY_BYTES"),
    "http_total_timeout_ms": ("data/HttpJson.kt", "TOTAL_TIMEOUT_MS"),
    "http_connect_timeout_ms": ("data/HttpJson.kt", "CONNECT_TIMEOUT_MS"),
    "http_read_timeout_ms": ("data/HttpJson.kt", "READ_TIMEOUT_MS"),
    "json_max_depth": ("data/JsonGuard.kt", "MAX_DEPTH"),
    "bzip2_max_unpacked_bytes": ("data/Bzip2.kt", "MAX_UNPACKED_BYTES"),
    "bzip2_timeout_nanos": ("data/Bzip2.kt", "TIMEOUT_NANOS"),
    "grib2_max_points": ("map/Grib2.kt", "MAX_POINTS"),
    "png_max_side": ("ui/map/MapViewModel.kt", "MAX_SIDE"),
    "update_max_apk_bytes": ("update/UpdateRelease.kt", "MAX_APK_BYTES"),
    "update_min_apk_bytes": ("update/UpdateRelease.kt", "MIN_APK_BYTES"),
    "update_total_timeout_ms": ("update/ApkDownload.kt", "TOTAL_TIMEOUT_MS"),
}

LOG_CALL = re.compile(r"\bLog\.[a-z]\(")
SENSITIVE_NAME = re.compile(r"\$\{?\w*(?:lat|lon|coord|place|city|name|query|point)\w*", re.I)
CYRILLIC_STRING = re.compile(r'"[^"\n]*[А-Яа-яЁё][^"\n]*"')
TECHNICAL_CALL = re.compile(
    r"\bLog\.|\blog\(|\bguarded\(|\brequire\(|\bcheck\(|\berror\(|\bthrow\b|Exception\(|\bassert|\bfail\(|@Preview|\bprintln\("
)
SPEC = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([sdfxXeEgGcb])")


@dataclass
class Row:
    group: str
    what: str
    claimed: str
    measured: str
    status: str
    note: str = ""


class Report:
    def __init__(self) -> None:
        self.rows: list[Row] = []

    def add(self, group: str, what: str, claimed, measured, status: str, note: str = "") -> None:
        self.rows.append(Row(group, what, str(claimed), str(measured), status, note))

    def compare(self, group: str, what: str, claimed, measured, note: str = "") -> None:
        self.add(group, what, claimed, measured, OK if claimed == measured else FAIL, note)

    def count(self, status: str) -> int:
        return sum(1 for row in self.rows if row.status == status)

    def markdown(self) -> str:
        lines = ["# Сверка цифр", ""]
        summary = ", ".join(f"{MARK[s]} {s} {self.count(s)}" for s in (OK, FAIL, WARN, INFO, SKIP))
        lines += [summary, ""]
        group = None
        for row in self.rows:
            if row.group != group:
                group = row.group
                lines += ["", f"## {group}", "", "| Проверка | Заявлено | Измерено | Итог |", "|---|---|---|---|"]
            note = f" {row.note}" if row.note else ""
            cells = [cell.replace("|", "\\|").replace("\n", " ") for cell in (row.what, row.claimed, row.measured)]
            lines.append(f"| {cells[0]} | {cells[1]} | {cells[2]} | {MARK[row.status]} {row.status}{note} |")
        return "\n".join(lines) + "\n"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def code_lines(text: str) -> list[str]:
    """Строки без целых строк-комментариев (// , /* , * ): адреса и русские слова в пояснениях не считаются."""
    return [ln for ln in text.splitlines() if not ln.lstrip().startswith(("//", "/*", "*"))]


def kotlin_files(root: Path, parts: tuple[str, ...]) -> list[Path]:
    files: list[Path] = []
    for part in parts:
        base = root / "app" / "src" / part
        if base.exists():
            files += sorted(base.rglob("*.kt"))
    return files


def arithmetic(expression: str) -> int | None:
    """Значение выражения вроде `4 * 1024 * 1024` или `10 * 60_000L`; None, если там что-то кроме чисел и * +."""
    cleaned = re.sub(r"(?<=\d)[Lf]\b", "", expression.split("//")[0]).replace("_", "").strip()
    if not re.fullmatch(r"[0-9\s*+()]+", cleaned):
        return None
    try:
        node = ast.parse(cleaned, mode="eval")
        return int(eval(compile(node, "<const>", "eval"), {"__builtins__": {}}, {}))  # noqa: S307 (только числа)
    except (SyntaxError, ValueError, ZeroDivisionError):
        return None


def constant(path: Path, name: str) -> int | None:
    match = re.search(rf"\bconst val {name}\s*(?::\s*\w+)?\s*=\s*([^\n]+)", read(path))
    return arithmetic(match.group(1)) if match else None


# 1. Сборка ---------------------------------------------------------------------------------------------------------

def check_build(root: Path, claims: dict, report: Report) -> None:
    group = "1. Сборка"
    gradle = read(root / "app" / "build.gradle.kts")
    want = claims["sdk"]
    found = {
        "minSdk": int(m.group(1)) if (m := re.search(r"\bminSdk\s*=\s*(\d+)", gradle)) else None,
        "targetSdk": int(m.group(1)) if (m := re.search(r"\btargetSdk\s*=\s*(\d+)", gradle)) else None,
        "compileSdk": int(m.group(1)) if (m := re.search(r"release\((\d+)\)", gradle)) else None,
        "applicationId": m.group(1) if (m := re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)) else None,
    }
    for key, value in found.items():
        report.compare(group, key, want[key], value)
    for key, pattern in (("locales", r"localeFilters\s*\+=\s*listOf\(([^)]*)\)"), ("abis", r"abiFilters\s*\+=\s*listOf\(([^)]*)\)")):
        match = re.search(pattern, gradle)
        measured = re.findall(r'"([^"]+)"', match.group(1)) if match else []
        report.compare(group, key, want[key], measured)
    report.compare(group, "release без x86_64", True, "lib/x86_64/**" in gradle)
    report.compare(group, "предупреждения компилятора = ошибки", True, "allWarningsAsErrors = true" in gradle)

    api = want["minSdk"]
    for name in ("README.md", "README.ru.md", "README.de.md", "DEVELOPMENT.md"):
        text = read(root / name)
        for match in re.finditer(r"Android (\d{2})\b", text):
            if int(match.group(1)) + 20 != api:
                report.add(group, f"{name}: «{match.group(0)}»", f"Android {api - 20}", match.group(0), FAIL)
                break
        else:
            report.add(group, f"{name}: версия Android", f"Android {api - 20} (API {api})", "согласуется", OK)
    dev = read(root / "DEVELOPMENT.md")
    if (m := re.search(r"minSdk (\d+)", dev)):
        report.compare(group, "DEVELOPMENT.md: minSdk", api, int(m.group(1)))


# 2. Манифест -------------------------------------------------------------------------------------------------------

def manifest_components(manifest: ET.Element, namespace: str) -> list[tuple[str, str, bool, str | None]]:
    """(вид, полное имя, открыто наружу, право доступа) для каждой части приложения."""
    result = []
    application = manifest.find("application")
    for element in list(application) if application is not None else []:
        if element.tag not in {"activity", "activity-alias", "service", "receiver", "provider"}:
            continue
        name = element.get(f"{ANDROID_NS}name", "")
        full = namespace + name if name.startswith(".") else name
        exported = element.get(f"{ANDROID_NS}exported") == "true"
        result.append((element.tag, full, exported, element.get(f"{ANDROID_NS}permission")))
    return result


def allow_lists(text: str) -> list[set[str]]:
    """Списки --allow-permission у каждого вызова inspect_apk.py в файле сценария."""
    chunks = text.split("inspect_apk.py")[1:]
    return [set(re.findall(r"--allow-permission\s+(\S+)", chunk[:3000])) for chunk in chunks]


def check_manifest(root: Path, claims: dict, report: Report) -> None:
    group = "2. Манифест"
    manifest = ET.parse(root / "app" / "src" / "main" / "AndroidManifest.xml").getroot()
    namespace = claims["sdk"]["applicationId"]
    permissions = {
        el.get(f"{ANDROID_NS}name") for el in manifest.findall("uses-permission")
        if el.get(f"{TOOLS_NS}node") != "remove"
    }
    report.compare(group, "права приложения", sorted(claims["permissions"]), sorted(permissions),
                   "новое право решает Дима; поправить release/expected-numbers.json и security.md")
    report.compare(group, "число прав", len(claims["permissions"]), len(permissions))
    for name in ("ci.yml", "release.yml"):
        lists = allow_lists(read(root / ".github" / "workflows" / name))
        wanted = set(claims["permissions"])
        report.add(group, f"{name}: допустимые права в разборе APK ({len(lists)} вызова)", len(wanted),
                   ", ".join(str(len(s)) for s in lists) or "нет",
                   OK if lists and all(s == wanted for s in lists) else FAIL,
                   "должны совпадать с манифестом во всех вызовах")

    components = manifest_components(manifest, namespace)
    exported = sorted(full.removeprefix(namespace + ".") for _, full, exp, _ in components if exp)
    report.compare(group, "части, открытые наружу", sorted(claims["exported_components"]), exported,
                   "security.md говорит «только экран запуска»: сверить текст")
    services = [(full, perm) for kind, full, _, perm in components if kind == "service"]
    unguarded = [full for full, perm in services if perm != "android.permission.BIND_JOB_SERVICE"]
    report.add(group, f"службы ({len(services)}) защищены BIND_JOB_SERVICE", "все", "все" if not unguarded else unguarded,
               OK if not unguarded else FAIL)
    application = manifest.find("application")
    backup = application.get(f"{ANDROID_NS}allowBackup") if application is not None else None
    report.compare(group, "allowBackup", "false", backup)
    report.compare(group, "usesCleartextTraffic / debuggable в манифесте", "нет",
                   "есть" if any(application.get(f"{ANDROID_NS}{a}") for a in ("usesCleartextTraffic", "debuggable")) else "нет")
    network_config = (root / "app/src/main/res/xml/network_security_config.xml").exists()
    report.compare(group, "свой файл сетевых правил", "нет", "есть" if network_config else "нет")


# 3. Зависимости ----------------------------------------------------------------------------------------------------

def check_dependencies(root: Path, claims: dict, report: Report) -> None:
    group = "3. Зависимости"
    toml = read(root / "gradle" / "libs.versions.toml")
    versions = dict(re.findall(r'^(\w+)\s*=\s*"([^"]+)"', toml.split("[libraries]")[0], re.M))
    libraries: dict[str, str] = {}
    for alias, body in re.findall(r"^([\w-]+)\s*=\s*\{([^}]*)\}", toml.split("[libraries]")[1].split("[plugins]")[0], re.M):
        module = re.search(r'module\s*=\s*"([^"]+)"', body)
        ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', body)
        libraries[alias] = f"{module.group(1)}" if module else alias
        if ref and ref.group(1) not in versions:
            report.add(group, f"версия «{ref.group(1)}» у {alias}", "объявлена", "нет", FAIL)
    loose = {k: v for k, v in versions.items() if re.search(r"[+\[\]()]|latest|SNAPSHOT", v)}
    report.add(group, "версии закреплены (без «+», диапазонов, SNAPSHOT)", "все", "все" if not loose else loose,
               OK if not loose else FAIL)

    gradle = read(root / "app" / "build.gradle.kts")
    used = {"implementation": set(), "testImplementation": set()}
    for kind, alias in re.findall(r"\b(implementation|testImplementation)\((?:platform\()?libs\.([\w.]+)\)", gradle):
        used[kind].add(alias.replace(".", "-"))
    declared = set(libraries)
    unused = sorted(declared - used["implementation"] - used["testImplementation"])
    missing = sorted((used["implementation"] | used["testImplementation"]) - declared)
    report.add(group, "объявлены и не подключены / подключены и не объявлены", "нет / нет",
               f"{unused or 'нет'} / {missing or 'нет'}", OK if not unused and not missing else FAIL)

    modules = {libraries[a] for a in used["implementation"] if a in libraries}
    report.compare(group, "библиотеки в приложении", sorted(claims["app_libraries"]), sorted(modules),
                   "новую библиотеку решает Дима: источник, лицензия, версия (security.md, пункт 6)")
    test_modules = {libraries[a] for a in used["testImplementation"] if a in libraries}
    report.compare(group, "библиотеки только для тестов", sorted(claims["test_libraries"]), sorted(test_modules))
    for module in sorted(modules):
        license_name = claims["licenses"].get(module)
        report.add(group, f"лицензия: {module}", "известна", license_name or "не записана",
                   OK if license_name else FAIL)
    verification = (root / "gradle" / "verification-metadata.xml").exists()
    report.add(group, "подлинность библиотек проверяется при сборке (verification-metadata.xml)", "—",
               "есть" if verification else "нет", OK if verification else WARN)
    shown = ", ".join(f"{k} {v}" for k, v in sorted(versions.items()))
    report.add(group, "версии сейчас", "—", shown, INFO, "для обновлений ведёт dependabot")


# 4. Сеть -----------------------------------------------------------------------------------------------------------

def check_network(root: Path, claims: dict, report: Report) -> None:
    group = "4. Сеть и пределы"
    hosts: dict[str, int] = {}
    cleartext: list[str] = []
    for path in kotlin_files(root, ("main",)):
        for line in code_lines(read(path)):
            for scheme, host in re.findall(r"(https?)://([A-Za-z0-9.\-]+)", line):
                hosts[host] = hosts.get(host, 0) + 1
                if scheme == "http" and host not in LOOPBACK_HOSTS:
                    cleartext.append(f"{path.name}: {host}")
    known = sorted(set(claims["hosts"]) | set(claims["link_hosts"]))
    report.compare(group, "внешние адреса в коде (запросы и ссылки)", known, sorted(hosts),
                   "новый адрес: записать в таблицу «что видят сторонние сервисы» (security.md)")
    report.add(group, "адреса, которые приходят из стиля карты во время работы (в коде их нет)", "—",
               ", ".join(claims["runtime_hosts"]), INFO, "скрипт их не видит: сверять по таблице security.md")
    report.add(group, "http:// без шифрования (кроме 127.0.0.1)", "нет", cleartext or "нет", OK if not cleartext else FAIL)

    base = root / PACKAGE_DIR
    for key, (relative, name) in LIMIT_SOURCES.items():
        value = constant(base / relative, name)
        report.compare(group, f"{relative.split('/')[-1]}: {name}", claims["limits"][key], value)


# 5. Приватность ----------------------------------------------------------------------------------------------------

def check_privacy(root: Path, claims: dict, report: Report) -> None:
    group = "5. Приватность"
    base = root / PACKAGE_DIR
    coordinates = read(base / "data" / "Coordinates.kt")
    match = re.search(r'"%\.(\d)f"', coordinates)
    report.compare(group, "знаков после запятой у координат в запросах", claims["coordinate_decimals"],
                   int(match.group(1)) if match else None)
    bypass = []
    for path in kotlin_files(root, ("main",)):
        text = read(path)
        if re.search(r"\b(?:latitude|longitude|lat|lon)=\$", text) and "Coordinates.param(" not in text:
            bypass.append(path.name)
    report.add(group, "адреса с координатами строятся через Coordinates.param", "все", bypass or "все",
               OK if not bypass else FAIL)
    leaks = []
    for path in kotlin_files(root, ("main",)):
        for number, line in enumerate(read(path).splitlines(), 1):
            if LOG_CALL.search(line) and SENSITIVE_NAME.search(line):
                leaks.append(f"{path.name}:{number}")
    report.add(group, "в журнал не попадают координаты, названия мест, запросы", 0, len(leaks) if leaks else 0,
               OK if not leaks else FAIL, ", ".join(leaks[:5]))
    excluded = bool(re.search(r'allowBackup="false"', read(root / "app/src/main/AndroidManifest.xml")))
    report.compare(group, "резервные копии выключены", True, excluded)


# 6. Переводы -------------------------------------------------------------------------------------------------------

def load_resources(path: Path) -> dict[str, tuple[str, object]]:
    result: dict[str, tuple[str, object]] = {}
    for element in ET.parse(path).getroot():
        name = element.get("name")
        if not name or element.get("translatable") == "false":
            continue
        if element.tag == "string":
            result[name] = ("string", "".join(element.itertext()))
        elif element.tag == "plurals":
            result[name] = ("plurals", {i.get("quantity"): "".join(i.itertext()) for i in element.findall("item")})
        elif element.tag == "string-array":
            result[name] = ("array", ["".join(i.itertext()) for i in element.findall("item")])
    return result


def specs(value: object) -> list[str]:
    texts = value.values() if isinstance(value, dict) else value if isinstance(value, list) else [value]
    found = set()
    for text in texts:
        found |= {f"{m.group(1) or ''}{m.group(2)}" for m in SPEC.finditer(str(text).replace("%%", ""))}
    return sorted(found)


def hardcoded_russian(root: Path) -> dict[str, int]:
    counts: dict[str, int] = {}
    base = root / PACKAGE_DIR
    for path in kotlin_files(root, ("main",)):
        count = sum(1 for line in code_lines(read(path))
                    if CYRILLIC_STRING.search(line) and not TECHNICAL_CALL.search(line))
        if count:
            counts[path.relative_to(base).as_posix()] = count
    return counts


def check_translations(root: Path, claims: dict, report: Report) -> None:
    group = "6. Переводы"
    res = root / "app" / "src" / "main" / "res"
    locales = claims["sdk"]["locales"]
    tables = {loc: load_resources(res / ("values" if loc == "en" else f"values-{loc}") / "strings.xml") for loc in locales}
    base = tables["en"]
    report.add(group, "ключей в strings.xml (en)", "—", len(base), INFO)
    needed = {"en": {"one", "other"}, "de": {"one", "other"}, "ru": {"one", "few", "many", "other"}}
    for loc in locales:
        table = tables[loc]
        if loc != "en":
            report.add(group, f"{loc}: нет перевода", 0, len(set(base) - set(table)),
                       OK if set(base) <= set(table) else FAIL, ", ".join(sorted(set(base) - set(table))[:5]))
            report.add(group, f"{loc}: лишние ключи", 0, len(set(table) - set(base)),
                       OK if set(table) <= set(base) else WARN, ", ".join(sorted(set(table) - set(base))[:5]))
            diff = [k for k in base if k in table and specs(base[k][1]) != specs(table[k][1])]
            report.add(group, f"{loc}: подстановки (%s, %d) как в en", 0, len(diff),
                       OK if not diff else FAIL, ", ".join(diff[:5]))
        empty = [k for k, (kind, v) in table.items() if kind == "string" and not str(v).strip()]
        report.add(group, f"{loc}: пустые строки", 0, len(empty), OK if not empty else FAIL, ", ".join(empty[:5]))
        short = [k for k, (kind, v) in table.items() if kind == "plurals" and not needed.get(loc, set()) <= set(v)]
        report.add(group, f"{loc}: множественные числа полные ({'/'.join(sorted(needed.get(loc, [])))})", 0, len(short),
                   OK if not short else FAIL, ", ".join(short[:5]))
    update_russian = sum(
        1 for path in kotlin_files(root, ("main",)) for line in code_lines(read(path))
        if "UpdateException(" in line and re.search(r"[А-Яа-яЁё]", line)
    )
    limit_update = claims["max_update_exception_russian"]
    report.add(group, "русские тексты ошибок обновления (видит человек в настройках при любом языке)",
               f"≤ {limit_update}", update_russian,
               OK if update_russian == 0 else (WARN if update_russian <= limit_update else FAIL),
               "заменить кодами ошибок и строками ресурсов" if update_russian else "")
    counts = hardcoded_russian(root)
    total = sum(counts.values())
    top = ", ".join(f"{k} {v}" for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:4])
    limit = claims["max_hardcoded_russian_strings"]
    status = OK if total == 0 else (WARN if total <= limit else FAIL)
    report.add(group, "русские строки прямо в коде (не через ресурсы)", f"≤ {limit}", total, status,
               f"растить нельзя; больше всего: {top}" if total else "")


# 7. Размер кода ----------------------------------------------------------------------------------------------------

def check_size(root: Path, claims: dict, report: Report) -> None:
    group = "7. Код и тесты"
    main = kotlin_files(root, ("main", "debug", "release"))
    tests = kotlin_files(root, ("test", "testDebug"))
    test_classes = [p for p in tests if p.name.endswith("Test.kt")]
    main_lines = sum(len(read(p).splitlines()) for p in main)
    test_lines = sum(len(read(p).splitlines()) for p in tests)
    at_test = sum(len(re.findall(r"^\s*@Test\b", read(p), re.M)) for p in tests)
    snap = claims["snapshot"]
    for what, value, key in (("файлов кода", len(main), "main_files"), ("строк кода", main_lines, "main_lines"),
                             ("файлов тестов (*Test.kt)", len(test_classes), "test_files"), ("строк тестов", test_lines, "test_lines"),
                             ("методов @Test", at_test, "test_methods")):
        report.add(group, what, f"{snap[key]} (аудит {snap['date']})", value, OK if value == snap[key] else INFO,
                   "" if value == snap[key] else "изменилось после аудита")
    report.add(group, "методов @Test не меньше", f"≥ {claims['tests_min']}", at_test,
               OK if at_test >= claims["tests_min"] else FAIL, "тесты не удалены")
    long_files = sorted(((len(read(p).splitlines()), p.name) for p in main), reverse=True)
    over_limit = [f"{n} {name}" for n, name in long_files if n > 700]
    report.add(group, "файлов кода длиннее 700 строк", 0, len(over_limit), OK if not over_limit else FAIL, ", ".join(over_limit))
    report.add(group, "файлов кода длиннее 500 строк", "—", sum(1 for n, _ in long_files if n > 500), INFO,
               ", ".join(f"{n} {name}" for n, name in long_files[:3]))

    script = root / ".github" / "scripts" / "code_hygiene.py"
    proc = subprocess.run([sys.executable, str(script), str(root)], capture_output=True, text=True)
    warnings = proc.stdout.count("::warning")
    errors = proc.stdout.count("::error")
    report.add(group, "чистота кода: ошибок", 0, errors if proc.returncode else 0,
               OK if proc.returncode == 0 else FAIL, "" if proc.returncode == 0 else proc.stdout[-300:])
    report.add(group, "чистота кода: предупреждений (код живёт ради теста, длинные файлы)", "—", warnings, INFO)

    readability = read(root / "app/src/test/kotlin/io/github/dumahuch/pogodaapp/sky/CelestialReadabilityTest.kt")
    match = re.search(r'"цифры"\s+to\s+([\d.]+)', readability)
    report.compare(group, "предел читаемости цифр (контраст)", claims["readability_digits"], float(match.group(1)) if match else None)


# 8. CI -------------------------------------------------------------------------------------------------------------

def check_ci(root: Path, claims: dict, report: Report) -> None:
    group = "8. CI и выпуск"
    unpinned: list[str] = []
    no_permissions: list[str] = []
    for path in sorted((root / ".github" / "workflows").glob("*.yml")):
        text = read(path)
        for ref in re.findall(r"^\s*-?\s*uses:\s*(\S+)", text, re.M):
            if not ref.startswith("./") and not re.search(r"@[0-9a-f]{40}$", ref):
                unpinned.append(f"{path.name}: {ref}")
        if not re.search(r"^permissions:", text, re.M):
            no_permissions.append(path.name)
    report.add(group, "действия GitHub закреплены по хешу", "все", unpinned or "все", OK if not unpinned else FAIL)
    report.add(group, "у каждого сценария свои права (permissions)", "все", no_permissions or "все",
               OK if not no_permissions else FAIL)
    workflows = "\n".join(read(p) for p in (root / ".github" / "workflows").glob("*.yml"))
    report.compare(group, "pull_request_target не используется", True, "pull_request_target" not in workflows)
    release = read(root / ".github" / "workflows" / "release.yml")
    report.compare(group, "выпуск только через среду release с одобрением", True, "environment: release" in release)
    wrapper = read(root / "gradle" / "wrapper" / "gradle-wrapper.properties")
    report.compare(group, "у дистрибутива Gradle указана сумма", True, "distributionSha256Sum=" in wrapper)
    report.compare(group, "dependabot настроен", True, (root / ".github" / "dependabot.yml").exists())
    fingerprint = [ln for ln in read(root / "release" / "key-fingerprint.txt").splitlines() if re.fullmatch(r"[0-9a-f]{64}", ln)]
    report.compare(group, "отпечаток ключа записан (SHA-256)", 1, len(fingerprint))
    notes = sorted((root / "release-notes").glob("*.md"))
    report.add(group, "файлов заметок к выпускам", "—", len(notes), INFO)


# 9. Результаты тестов и APK ----------------------------------------------------------------------------------------

def check_junit(root: Path, directory: Path | None, report: Report) -> None:
    group = "9. Результаты тестов"
    if directory is None:
        report.add(group, "тесты приложения", "—", "не запускались", SKIP, "нужен --junit app/build/test-results")
        return
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    files = sorted(directory.rglob("TEST-*.xml"))
    for path in files:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
    tests = kotlin_files(root, ("test", "testDebug"))
    declared = sum(len(re.findall(r"^\s*@Test\b", read(p), re.M)) for p in tests)
    report.add(group, "файлов отчёта", "> 0", len(files), OK if files else FAIL)
    report.add(group, "выполнено тестов не меньше объявленных @Test", f"≥ {declared}", totals["tests"],
               OK if totals["tests"] >= declared else FAIL, "меньше: часть тестов не запущена")
    report.add(group, "упало / с ошибкой", "0 / 0", f"{totals['failures']} / {totals['errors']}",
               OK if not totals["failures"] and not totals["errors"] else FAIL)
    report.add(group, "пропущено", 0, totals["skipped"], OK if not totals["skipped"] else WARN)


def check_apk(claims: dict, apk: Path | None, kind: str, report: Report) -> None:
    group = "10. Собранный APK"
    if apk is None:
        report.add(group, "APK", "—", "не передан", SKIP, "нужен --apk файл --apk-kind debug|release")
        return
    want = claims["apk"][kind]
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        abis = sorted({n.split("/")[1] for n in names if n.startswith("lib/") and n.count("/") >= 2})
        dex = [n for n in names if re.fullmatch(r"classes\d*\.dex", n)]
        unpacked = sum(i.file_size for i in archive.infolist())
    size_mb = round(apk.stat().st_size / 1_000_000, 1)
    report.compare(group, f"{kind}: процессоры в APK", want["abis"], abis)
    report.add(group, f"{kind}: размер файла, МБ", want["size_mb"] or "не задан", size_mb,
               INFO if not want["size_mb"] else (OK if want["size_mb"]["min"] <= size_mb <= want["size_mb"]["max"] else FAIL),
               "первый замер: внести в expected-numbers.json" if not want["size_mb"] else "")
    report.add(group, f"{kind}: файлов dex", "—", len(dex), INFO)
    report.add(group, f"{kind}: в распакованном виде, МБ", "—", round(unpacked / 1_000_000, 1), INFO)


# Запуск ------------------------------------------------------------------------------------------------------------

def run(root: Path, claims_path: Path, junit: Path | None, apk: Path | None, kind: str, static: bool = True) -> Report:
    claims = json.loads(read(claims_path))
    report = Report()
    if static:
        for check in (check_build, check_manifest, check_dependencies, check_network, check_privacy,
                      check_translations, check_size, check_ci):
            check(root, claims, report)
    check_junit(root, junit, report)
    check_apk(claims, apk, kind, report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--claims", default="release/expected-numbers.json")
    parser.add_argument("--report", help="записать таблицу в файл Markdown")
    parser.add_argument("--junit", help="каталог с TEST-*.xml (app/build/test-results)")
    parser.add_argument("--apk", help="собранный APK для замера")
    parser.add_argument("--apk-kind", choices=["debug", "release"], default="debug")
    parser.add_argument("--only-built", action="store_true",
                        help="только результаты тестов и APK (исходники уже проверены другим вызовом)")
    args = parser.parse_args()
    root = Path(args.root)
    report = run(root, root / args.claims, Path(args.junit) if args.junit else None,
                 Path(args.apk) if args.apk else None, args.apk_kind, static=not args.only_built)
    text = report.markdown()
    print(text)
    if args.report:
        Path(args.report).write_text(text, encoding="utf-8")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(text)
    for row in report.rows:
        if row.status == FAIL:
            print(f"::error title=Сверка цифр::{row.group}: {row.what}: заявлено {row.claimed}, измерено {row.measured}")
    return 1 if report.count(FAIL) else 0


if __name__ == "__main__":
    sys.exit(main())
