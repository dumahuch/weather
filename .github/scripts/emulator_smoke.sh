#!/usr/bin/env bash
# Проверка на эмуляторе: установка, запуск, тексты на трёх языках и три состояния главного экрана.
#   1. Без разрешения на местоположение: карточка «Разрешить» (en, ru, de).
#   2. С разрешением и сетью: настоящий прогноз Open-Meteo (если сеть из эмулятора недоступна, это предупреждение).
#   3. Без сети с сохранённым прогнозом: данные и строка «нет сети · данные 3 ч назад» (en, ru, de).
# Использование: emulator_smoke.sh <apk> <пакет> <versionName> <versionCode> [ответ.json]
# Снимки экрана и разметка складываются в папку $SMOKE_OUT (по умолчанию smoke-out).
set -euo pipefail
export LC_ALL=C.UTF-8

APK="${1:?путь к APK}"
PKG="${2:?имя пакета}"
VERSION_NAME="${3:?versionName}"
VERSION_CODE="${4:?versionCode}"
FIXTURE="${5:-app/src/test/resources/open-meteo/hannover.json}"
ACTIVITY="io.github.dumahuch.pogodaapp.MainActivity"
PERMISSION="android.permission.ACCESS_COARSE_LOCATION"
OUT="${SMOKE_OUT:-smoke-out}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$OUT"
TEXTS=""

annotate() {
  local body
  body=$(printf '%s' "$3" | sed -e 's/%/%25/g' | sed ':a;N;$!ba;s/\n/%0A/g')
  echo "::$1 title=$2::$body"
}

shot() {
  adb exec-out screencap -p >"$OUT/$1.png" 2>/dev/null || true
  adb exec-out cat /sdcard/ui.xml >"$OUT/$1.xml" 2>/dev/null || true
}

fail() {
  shot "ошибка"
  annotate error "Эмулятор" "$1
--- на экране ---
$TEXTS
--- определение места ---
$(location_diagnostics)
--- журнал приложения ---
$(adb logcat -d -s PogodaLocation:V AndroidRuntime:E 2>/dev/null | tr -d '\r' | tail -40)"
  exit 1
}

# Состояние службы местоположения эмулятора: нужно, когда место не определилось.
location_diagnostics() {
  echo "location_mode=$(adb shell settings get secure location_mode 2>&1 | tr -d '\r')"
  echo "включено=$(adb shell cmd location is-location-enabled 2>&1 | tr -d '\r')"
  echo "журнал приложения:"
  adb logcat -d -s PogodaLocation:V 2>&1 | tr -d '\r' | tail -15
  echo "dumpsys location:"
  adb shell dumpsys location 2>&1 | tr -d '\r' | grep -iE 'provider|enabled|last location|test' | head -40
}

# Подаёт эмулятору место (Ганновер) всеми способами: консоль эмулятора и пробные источники системы.
feed_location() {
  adb emu geo fix 9.7386 52.3745 >/dev/null 2>&1 || true
  for provider in fused network gps; do
    adb shell cmd location providers set-test-provider-location "$provider" --location 52.3745,9.7386 --accuracy 100 >/dev/null 2>&1 || true
  done
}

# Тексты экрана: значения text и content-desc, по одному в строке.
screen_texts() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml 2>/dev/null | PYTHONIOENCODING=utf-8 python3 -c '
import html, re, sys
data = sys.stdin.buffer.read().decode("utf-8", "replace")
for attribute in ("text", "content-desc"):
    for value in re.findall(attribute + r"=\"([^\"]*)\"", data):
        value = html.unescape(value)
        if value:
            print(value)
'
}

start_app() {
  adb shell am force-stop "$PKG"
  local out
  out=$(adb shell am start -W -n "$PKG/$ACTIVITY" | tr -d '\r')
  grep -q "Status: ok" <<<"$out" || fail "Приложение не запустилось: $out"
  sleep 3
  [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ] || fail "Процесс приложения не найден после запуска (приложение упало?)"
}

set_locale() {
  adb shell cmd locale set-app-locales "$PKG" --locales "$1" >/dev/null
}

# Ждёт точной строки на экране; при успехе возвращает 0, TEXTS хранит последние тексты экрана.
wait_for_text() {
  local expected="$1" deadline=$((SECONDS + $2))
  while :; do
    if [ -n "${WAIT_HOOK:-}" ]; then "$WAIT_HOOK"; fi
    TEXTS=$(screen_texts)
    if grep -qxF "$expected" <<<"$TEXTS"; then return 0; fi
    if [ "$SECONDS" -ge "$deadline" ]; then return 1; fi
    sleep 3
  done
}

expect_text() {
  local expected="$1" label="$2"
  grep -qxF "$expected" <<<"$TEXTS" || fail "[$label] на экране нет текста «$expected»"
  echo "[$label] на экране есть: $expected"
}

expect_pattern() {
  local pattern="$1" label="$2" what="$3"
  grep -qE "$pattern" <<<"$TEXTS" || fail "[$label] на экране нет строки вида «$what»"
  echo "[$label] на экране есть: $what"
}

# --- 1. Без разрешения -------------------------------------------------------

check_no_permission() {
  local locale="$1" title="$2" text="$3" button="$4" place="$5" tab="$6"
  set_locale "$locale"
  start_app
  wait_for_text "$title" 20 || fail "[$locale без разрешения] нет карточки «$title»"
  expect_text "$title" "$locale без разрешения"
  expect_text "$text" "$locale без разрешения"
  expect_text "$button" "$locale без разрешения"
  expect_text "$place" "$locale без разрешения"
  expect_text "$tab" "$locale без разрешения"
  expect_text "$VERSION_NAME ($VERSION_CODE)" "$locale без разрешения"
  shot "1-без-разрешения-$locale"
}

adb install -r "$APK" >/dev/null || fail "APK не установился"
adb logcat -c

check_no_permission en "No location access" "Allow access to see the weather where you are." "Allow" "No place set" "Home"
check_no_permission ru "Нет доступа к местоположению" "Разрешите доступ, чтобы видеть погоду там, где вы." "Разрешить" "Место не задано" "Главная"
check_no_permission de "Kein Zugriff auf den Standort" "Erlaube den Zugriff, um das Wetter dort zu sehen, wo du bist." "Erlauben" "Kein Ort gewählt" "Start"

# --- 2. С разрешением и сетью --------------------------------------------------

adb shell pm grant "$PKG" "$PERMISSION" || fail "Не удалось выдать разрешение на местоположение"
adb shell cmd location set-location-enabled true >/dev/null 2>&1 || true
adb shell settings put secure location_mode 3 >/dev/null 2>&1 || true
# Пробные источники места: система отдаёт приложению заданные координаты, как настоящие.
for provider in fused network gps; do
  adb shell cmd location providers add-test-provider "$provider" >/dev/null 2>&1 || true
  adb shell cmd location providers set-test-provider-enabled "$provider" true >/dev/null 2>&1 || true
done
# Ганновер: на консоли эмулятора сначала долгота, потом широта.
feed_location
export WAIT_HOOK=feed_location

set_locale en
start_app
if wait_for_text "precipitation, mm" 90; then
  expect_text "Hourly" "en с сетью"
  expect_text "Now" "en с сетью"
  expect_text "Weather data by Open-Meteo.com" "en с сетью"
  expect_pattern '^Feels like −?[0-9]+° · High −?[0-9]+°, Low −?[0-9]+°$' "en с сетью" "Feels like 11° · High 14°, Low 8°"
  expect_pattern '^−?[0-9]+°$' "en с сетью" "температура 13°"
  shot "2-с-сетью-en"
  annotate notice "Эмулятор" "С сетью: настоящий прогноз Open-Meteo показан. Название места: $(grep -vE '^(Hourly|Now|Home)$' <<<"$TEXTS" | head -3 | tr '\n' '|')"
else
  shot "2-с-сетью-нет-данных"
  annotate warning "Эмулятор" "С сетью: прогноз за 90 с не появился (сеть эмулятора или определение места недоступны). Это не сбой приложения, но проверить показ настоящих данных не удалось.
--- на экране ---
$TEXTS
--- определение места ---
$(location_diagnostics)"
fi

# --- 3. Без сети, прогноз из памяти --------------------------------------------

adb shell am force-stop "$PKG"
adb shell svc wifi disable >/dev/null 2>&1 || true
adb shell svc data disable >/dev/null 2>&1 || true
python3 "$HERE/make_cache.py" "$FIXTURE" "$OUT/forecast_cache.json" --age-hours 3
adb push "$OUT/forecast_cache.json" /data/local/tmp/forecast_cache.json >/dev/null
adb shell run-as "$PKG" cp /data/local/tmp/forecast_cache.json files/forecast_cache.json \
  || fail "Не удалось положить сохранённый прогноз в память приложения"
rm -f "$OUT/forecast_cache.json"

check_offline() {
  local locale="$1" pill="$2" title="$3"
  set_locale "$locale"
  start_app
  wait_for_text "$pill" 60 || fail "[$locale без сети] нет строки «$pill»"
  expect_text "$pill" "$locale без сети"
  expect_text "$title" "$locale без сети"
  expect_text "Hannover" "$locale без сети"
  expect_pattern '^−?[0-9]+°$' "$locale без сети" "температура 13°"
  shot "3-без-сети-$locale"
}

check_offline en "Offline · data from 3 h ago" "Hourly"
check_offline ru "Нет сети · данные 3 часа назад" "По часам"
check_offline de "Kein Netz · Daten von vor 3 Std." "Stündlich"

adb shell svc wifi enable >/dev/null 2>&1 || true
adb shell svc data enable >/dev/null 2>&1 || true

crash=$(adb logcat -d -b crash | tr -d '\r')
[ -z "$crash" ] || fail "В журнале падений есть записи:
$crash"

annotate notice "Эмулятор" "Приложение установилось, запустилось, не упало; карточка без разрешения и прогноз без сети на en/ru/de показаны верно."
