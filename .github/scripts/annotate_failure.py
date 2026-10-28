#!/usr/bin/env python3
"""Выводит причину падения сборки в виде аннотаций GitHub.

Аннотации видны на странице проверки коммита, без скачивания журналов.
Использование: annotate_failure.py build-*.log
"""
import re
import sys
from pathlib import Path

LIMIT = 30000


def esc(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def emit(title: str, body: str) -> None:
    print(f"::error title={title}::{esc(body[-LIMIT:])}")


def main() -> None:
    for name in sys.argv[1:]:
        path = Path(name)
        if not path.is_file():
            continue
        text = path.read_text(errors="replace")
        compiler = re.findall(r"^[ew]: .*$", text, re.M)
        if compiler:
            emit(f"Компилятор Kotlin ({path.name})", "\n".join(compiler[:60]))
        went_wrong = re.search(r"\* What went wrong:\n(.*?)\n\* Try:", text, re.S)
        if went_wrong:
            emit(f"Gradle: что пошло не так ({path.name})", went_wrong.group(1))
        emit(f"Конец журнала ({path.name})", "\n".join(text.splitlines()[-70:]))

    for report in sorted(Path("app/build/reports").glob("lint-results*.txt")):
        emit(f"Линт ({report.name})", report.read_text(errors="replace"))
    for xml in sorted(Path("app/build/test-results").rglob("*.xml")):
        body = xml.read_text(errors="replace")
        if "<failure" in body:
            emit(f"Тест не прошёл ({xml.name})", body)


if __name__ == "__main__":
    main()
