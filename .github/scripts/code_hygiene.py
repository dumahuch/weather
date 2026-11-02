#!/usr/bin/env python3
"""Проверка чистоты кода без сборки: размер файлов и объявления, которых нигде не используют.

Что делает:
  1. Файл Kotlin приложения длиннее 700 строк останавливает проверку (длиннее 500, а у тестов длиннее 500 тоже:
     предупреждение). Исключения только в списке LONG_FILES_ALLOWED с причиной.
  2. Имя объявления (fun, class, object, interface, val, var), которое встречается в коде приложения и тестов ровно
     один раз, то есть только в самом объявлении, считается неиспользуемым и останавливает проверку. Это грубая
     замена линтеру неиспользуемого кода: компилятор ловит только закрытое, а это ловит и открытое.
  3. Имя, которое используется только в тестах, даёт предупреждение (код живёт ради теста).
  4. Строка или список строк из res/values, на которые нет ссылок ни в коде, ни в разметке, останавливает проверку.

Запуск: python3 .github/scripts/code_hygiene.py [корень репозитория]
"""
import re
import sys
from collections import Counter
from pathlib import Path

LINES_WARN = 500
LINES_FAIL = 700
LONG_FILES_ALLOWED: dict[str, str] = {}

# Имена, которые вызывает платформа или библиотека, а не наш код.
PLATFORM_NAMES = {
    "main", "onCreate", "onResume", "onPause", "onStart", "onStop", "onDestroy", "invoke", "getValue", "setValue",
    "provideDelegate", "compareTo", "equals", "hashCode", "toString", "component1", "component2", "component3",
    "iterator", "next", "hasNext", "contains", "get", "set", "plus", "minus", "times", "unaryMinus", "rangeTo",
    "create", "factory", "onCleared", "values", "valueOf", "entries", "Companion",
}

DECLARATION = re.compile(
    r"^\s*(?:@[\w.]+(?:\([^)]*\))?\s+)*"
    r"(?:(?:internal|public|protected|data|sealed|enum|abstract|open|const|inline|value|suspend|operator|infix|tailrec|lateinit|fun interface)\s+)*"
    r"(fun|class|object|interface|val|var)\s+(?:<[^>]*>\s*)?(?:[\w<>?,. ]+\.)?(\w+)"
)


def kotlin_files(root: Path, parts: list[str]) -> list[Path]:
    files: list[Path] = []
    for part in parts:
        files += sorted((root / "app" / "src" / part).rglob("*.kt")) if (root / "app" / "src" / part).exists() else []
    return files


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    code = kotlin_files(root, ["main", "debug", "release"])
    tests = kotlin_files(root, ["test", "testDebug"])
    problems: list[str] = []
    warnings: list[str] = []

    for path in code + tests:
        count = len(path.read_text(encoding="utf-8").splitlines())
        rel = path.relative_to(root).as_posix()
        is_test = "/src/test" in rel
        if count > LINES_FAIL and rel not in LONG_FILES_ALLOWED and not is_test:
            problems.append(f"{rel}: {count} строк, предел {LINES_FAIL}")
        elif count > LINES_WARN:
            warnings.append(f"{rel}: {count} строк (предупреждение с {LINES_WARN})")

    word = re.compile(r"\w+")
    in_code: Counter[str] = Counter()
    in_tests: Counter[str] = Counter()
    for files, counter in ((code, in_code), (tests, in_tests)):
        for path in files:
            for match in word.finditer(path.read_text(encoding="utf-8")):
                counter[match.group(0)] += 1
    manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    resources = " ".join(p.read_text(encoding="utf-8", errors="ignore") for p in (root / "app/src/main/res").rglob("*.xml"))

    for path in code:
        rel = path.relative_to(root).as_posix()
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if line.lstrip().startswith(("//", "*", "/*")) or "override " in line or "@Preview" in line:
                continue
            found = DECLARATION.match(line)
            if not found:
                continue
            name = found.group(2)
            if name in PLATFORM_NAMES or name in manifest or name in resources:
                continue
            if in_code[name] + in_tests[name] <= 1:
                problems.append(f"{rel}:{number}: «{name}» нигде не используется")
            elif in_code[name] <= 1 and in_tests[name] >= 1:
                warnings.append(f"{rel}:{number}: «{name}» используется только в тестах")

    values = sorted((root / "app/src/main/res").glob("values*/strings.xml"))
    declared = {m.group(2) for f in values for m in re.finditer(r'<(string|string-array)\s+name="(\w+)"', f.read_text(encoding="utf-8"))}
    sources = "\n".join(p.read_text(encoding="utf-8") for p in code) + manifest + resources
    for name in sorted(declared):
        if not re.search(r"R\.(?:string|array)\." + name + r"\b|@(?:string|array)/" + name + r"\b", sources):
            problems.append(f"строка «{name}» нигде не используется")

    for text in warnings:
        print(f"::warning title=Чистота кода::{text}")
    if problems:
        for text in problems:
            print(f"::error title=Чистота кода::{text}")
        print(f"Чистота кода: {len(problems)} замечаний, {len(warnings)} предупреждений")
        return 1
    print(f"Чистота кода: замечаний нет, предупреждений {len(warnings)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
