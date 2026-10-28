#!/usr/bin/env bash
# Проверка на эмуляторе: установка, запуск, отсутствие падений и текст на экране на трёх языках.
# Использование: emulator_smoke.sh <apk> <пакет> <versionName> <versionCode>
set -euo pipefail

APK="${1:?путь к APK}"
PKG="${2:?имя пакета}"
VERSION_NAME="${3:?versionName}"
VERSION_CODE="${4:?versionCode}"
ACTIVITY="io.github.dumahuch.pogodaapp.MainActivity"

annotate_error() {
  local body
  body=$(printf '%s' "$2" | sed -e 's/%/%25/g' | sed ':a;N;$!ba;s/\n/%0A/g')
  echo "::error title=$1::$body"
}

fail() {
  annotate_error "Эмулятор" "$1
--- конец журнала Android ---
$(adb logcat -d 2>/dev/null | tr -d '\r' | tail -60)"
  exit 1
}

start_app() {
  adb shell am force-stop "$PKG"
  local out
  out=$(adb shell am start -W -n "$PKG/$ACTIVITY" | tr -d '\r')
  grep -q "Status: ok" <<<"$out" || fail "Приложение не запустилось: $out"
  sleep 4
  [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ] || fail "Процесс приложения не найден после запуска (приложение упало?)"
}

screen_texts() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml | python3 -c '
import re, sys
for text in re.findall(r"text=\"([^\"]*)\"", sys.stdin.read()):
    if text:
        print(text)
'
}

expect_text() {
  local texts="$1" expected="$2" locale="$3"
  grep -qxF "$expected" <<<"$texts" || fail "[$locale] на экране нет текста «$expected». Есть:
$texts"
  echo "[$locale] на экране есть: $expected"
}

check_locale() {
  local locale="$1" status="$2" version_label="$3"
  adb shell cmd locale set-app-locales "$PKG" --locales "$locale" >/dev/null
  start_app
  local texts
  texts=$(screen_texts)
  expect_text "$texts" "PogodaApp" "$locale"
  expect_text "$texts" "$status" "$locale"
  expect_text "$texts" "$version_label" "$locale"
}

adb install -r "$APK" >/dev/null || fail "APK не установился"
adb logcat -c

check_locale en "Empty app. Checking installation and updates." "Version $VERSION_NAME (build $VERSION_CODE)"
check_locale ru "Пустое приложение. Проверка установки и обновлений." "Версия $VERSION_NAME (сборка $VERSION_CODE)"
check_locale de "Leere App. Test von Installation und Updates." "Version $VERSION_NAME (Build $VERSION_CODE)"

crash=$(adb logcat -d -b crash | tr -d '\r')
[ -z "$crash" ] || fail "В журнале падений есть записи:
$crash"

echo "::notice title=Эмулятор::Приложение установилось, запустилось, не упало; тексты en/ru/de на экране верны."
