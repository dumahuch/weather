#!/usr/bin/env bash
# Проверка на эмуляторе: установка, запуск, тексты на трёх языках и три состояния главного экрана.
#   1. Без разрешения на местоположение: карточка «Разрешить» (en, ru, de).
#   2. С разрешением и сетью: настоящий прогноз Open-Meteo (если сеть из эмулятора недоступна, это предупреждение).
#   3. Без сети с сохранённым прогнозом: данные и строка «нет сети · данные 3 ч назад» (en, ru, de).
#   3б. Обновление протягиванием вниз: короткое движение не запускает обновление, длинное запускает (проверочная сборка замедляет место на 12 с); верх экрана сдвигается на 56 dp, у индикатора подпись, потом всё возвращается.
#   3в. Небо: скрытый экран проверки (семь нажатий на номер версии), шесть времён суток и семь видов погоды со снимками,
#       частота кадров; при выключенных анимациях (масштаб 0) небо стоит на месте.
#   4. Рабочая сборка (release, сжатая R8): запускается, карточка без разрешения на трёх языках, после разрешения не падает.
# Использование: emulator_smoke.sh <apk> <пакет> <versionName> <versionCode> [ответ.json] [release.apk пакет versionName versionCode]
# Снимки экрана и разметка складываются в папку $SMOKE_OUT (по умолчанию smoke-out).
set -euo pipefail
export LC_ALL=C.UTF-8

APK="${1:?путь к APK}"
PKG="${2:?имя пакета}"
VERSION_NAME="${3:?versionName}"
VERSION_CODE="${4:?versionCode}"
FIXTURE="${5:-app/src/test/resources/open-meteo/hannover.json}"
# Необязательно: рабочая сборка (release, с одноразовым ключом) проверяется отдельно, так как R8 сжимает код.
RELEASE_APK="${6:-}"
RELEASE_PKG="${7:-}"
RELEASE_VERSION_NAME="${8:-}"
RELEASE_VERSION_CODE="${9:-}"
ACTIVITY="io.github.dumahuch.pogodaapp.MainActivity"
PERMISSION="android.permission.ACCESS_COARSE_LOCATION"
OUT="${SMOKE_OUT:-smoke-out}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$OUT"
# Весь вывод сценария дублируется в файл, а при любом выходе (в том числе при сбое) сохраняется журнал телефона:
# CI выкладывает их в ветку ci-screenshots, потому что журналы самого GitHub из сессии Claude недоступны.
exec > >(tee -a "$OUT/smoke.log") 2>&1
save_logcat() {
  adb logcat -d -v time -t 8000 >"$OUT/logcat.txt" 2>/dev/null || true
  sleep 1
}
trap save_logcat EXIT
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

# Сведения для разбора зависания и тормозов: стек главного потока из записи о «не отвечает» (ANR), кадры и память.
# Кладутся в файлы рядом со снимками (в ветку ci-screenshots).
save_hang_info() {
  adb shell dumpsys dropbox --print data_app_anr 2>/dev/null | tr -d '\r' | head -400 >"$OUT/anr.txt" || true
  adb shell dumpsys gfxinfo "$PKG" 2>/dev/null | tr -d '\r' | head -80 >"$OUT/gfxinfo.txt" || true
  adb shell dumpsys meminfo "$PKG" 2>/dev/null | tr -d '\r' | head -60 >"$OUT/meminfo.txt" || true
  adb logcat -d -b all -v threadtime 2>/dev/null | tr -d '\r' | grep -iE "pogodaapp|ANR in|Choreographer|Davey|haze|RenderThread" | tail -200 >"$OUT/app-logcat.txt" || true
}

fail() {
  shot "ошибка"
  save_hang_info
  annotate error "Эмулятор" "$1
--- на экране (последняя проверка) ---
$TEXTS
--- на экране сейчас ---
$(screen_texts)
--- определение места ---
$(location_diagnostics)
--- журнал приложения ---
$(adb logcat -d -s PogodaLocation:V AndroidRuntime:E 2>/dev/null | tr -d '\r' | tail -40)
--- журнал падений ---
$(adb logcat -d -b crash 2>/dev/null | tr -d '\r' | tail -40)
--- система о приложении ---
$(adb logcat -d -t 400 2>/dev/null | tr -d '\r' | grep -iE "pogodaapp|FATAL|am_kill|am_proc_died|Process .* died" | tail -25)"
  exit 1
}

# Состояние службы местоположения эмулятора: нужно, когда место не определилось.
location_diagnostics() {
  echo "location_mode=$(adb shell settings get secure location_mode 2>&1 | tr -d '\r')"
  echo "включено=$(adb shell cmd location is-location-enabled 2>&1 | tr -d '\r')"
  echo "журнал приложения:"
  adb logcat -d -s PogodaLocation:V 2>&1 | tr -d '\r' | tail -15
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
  local waited=0
  while [ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]; do
    [ "$waited" -lt 12 ] || fail "Процесс приложения не найден после запуска (приложение упало?)"
    sleep 2
    waited=$((waited + 2))
  done
}

set_locale() {
  adb shell cmd locale set-app-locales "$PKG" --locales "$1" >/dev/null
}

# Ждёт точной строки на экране; при успехе возвращает 0, TEXTS хранит последние тексты экрана.
wait_for_text() {
  local expected="$1" deadline=$((SECONDS + $2))
  while :; do
    TEXTS=$(screen_texts)
    if grep -qxF "$expected" <<<"$TEXTS"; then return 0; fi
    if [ "$SECONDS" -ge "$deadline" ]; then return 1; fi
    sleep 3
  done
}

# Нажимает на элемент экрана, текст которого подходит под регулярное выражение (по его середине).
tap_text() {
  local coords
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  coords=$(adb exec-out cat /sdcard/ui.xml 2>/dev/null | PATTERN="$1" PYTHONIOENCODING=utf-8 python3 -c '
import html, os, re, sys
data = sys.stdin.buffer.read().decode("utf-8", "replace")
pattern = re.compile(os.environ["PATTERN"])
for node in re.findall(r"<node [^>]*>", data):
    # Подпись элемента: текст, а у вкладок и кнопок без текста описание (content-desc).
    values = [html.unescape(m) for m in re.findall(r" (?:text|content-desc)=\"([^\"]*)\"", node)]
    bounds = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
    if bounds and any(v and pattern.search(v) for v in values):
        x1, y1, x2, y2 = map(int, bounds.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
')
  [ -n "$coords" ] || return 1
  # shellcheck disable=SC2086
  adb shell input tap $coords
}

# Центр (x y) элемента с точным текстом; пусто, если такого нет.
exact_center() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml 2>/dev/null | TEXT="$1" PYTHONIOENCODING=utf-8 python3 -c '
import html, os, re, sys
data = sys.stdin.buffer.read().decode("utf-8", "replace")
for node in re.findall(r"<node [^>]*>", data):
    text = re.search(r" text=\"([^\"]*)\"", node)
    bounds = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
    if text and bounds and html.unescape(text.group(1)) == os.environ["TEXT"]:
        x1, y1, x2, y2 = map(int, bounds.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
'
}

# Ждёт, пока на экране появится элемент с подходящим текстом, и нажимает на него.
wait_tap() {
  local deadline=$((SECONDS + $2))
  while :; do
    if tap_text "$1"; then return 0; fi
    if [ "$SECONDS" -ge "$deadline" ]; then return 1; fi
    sleep 2
  done
}

# Нажимает кнопку системного окна: ждёт, пока окно встанет на место (оно выезжает с анимацией, касание в этот
# момент может промахнуться), нажимает и ждёт до 10 с, пока кнопка исчезнет (окно закрывается с анимацией,
# на нагруженной машине дольше двух секунд); при неудаче пробует ещё до трёх раз. Если после нажатия кнопки
# уже нет, окно закрылось, это успех.
tap_dialog() {
  local pattern="$1" attempt tapped=0
  for attempt in 1 2 3 4; do
    local deadline=$((SECONDS + 20))
    until grep -qE "$pattern" <<<"$(screen_texts)"; do
      [ "$tapped" -eq 0 ] || return 0
      [ "$SECONDS" -lt "$deadline" ] || return 1
      sleep 2
    done
    sleep 2
    if ! tap_text "$pattern"; then
      [ "$tapped" -eq 1 ] && return 0
      return 1
    fi
    tapped=1
    local gone=$((SECONDS + 10))
    while grep -qE "$pattern" <<<"$(screen_texts)"; do
      [ "$SECONDS" -lt "$gone" ] || break
      sleep 1
    done
    if ! grep -qE "$pattern" <<<"$(screen_texts)"; then return 0; fi
  done
  return 1
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

# --- 1б. Системное окно разрешения: два отказа, затем кнопка «Открыть настройки» -----

set_locale en
start_app
wait_for_text "Allow" 20 || fail "[en окно разрешения] нет кнопки «Allow»"
tap_text '^Allow$' || fail "[en окно разрешения] не удалось нажать «Allow»"
tap_dialog "^Don.t allow$" || fail "[en окно разрешения] системное окно с кнопкой «Don't allow» не появилось или не закрылось"
wait_for_text "Allow" 20 || fail "[en окно разрешения] после первого отказа карточка пропала"
! grep -qxF "Open settings" <<<"$TEXTS" || fail "[en окно разрешения] после первого отказа уже показана «Open settings»"
tap_text '^Allow$' || fail "[en окно разрешения] не удалось нажать «Allow» второй раз"
tap_dialog "^Don.t allow$" || fail "[en окно разрешения] второе системное окно не появилось или не закрылось"
wait_for_text "Open settings" 20 || fail "[en окно разрешения] после второго отказа нет кнопки «Open settings»"
shot "1б-после-отказа-en"
tap_text '^Open settings$' || fail "[en окно разрешения] не удалось нажать «Open settings»"
sleep 3
focus=$(adb shell dumpsys window | tr -d '\r' | grep -E 'mCurrentFocus' | head -1)
grep -qi 'settings' <<<"$focus" || fail "[en окно разрешения] после «Open settings» открылось не системное приложение настроек: $focus"
echo "[en окно разрешения] открылись настройки: $focus"
adb shell input keyevent KEYCODE_BACK
sleep 2
wait_for_text "Open settings" 20 || fail "[en окно разрешения] после возврата из настроек карточка пропала"

# --- 2. Разрешение через системное окно, затем настоящий прогноз ------------------

# Сброс данных и разрешений приложения: окно запроса снова покажется.
adb shell pm clear "$PKG" >/dev/null || fail "Не удалось сбросить данные приложения"
start_app
adb shell am force-stop "$PKG"
# Место эмулятору не подаётся через его службу (ей нужны сеть и спутники): проверочная сборка читает
# широту и долготу из файла в своей папке (Ганновер), см. Locations.kt в src/debug.
put_file() {
  adb push "$1" /data/local/tmp/"$2" >/dev/null
  adb shell run-as "$PKG" cp /data/local/tmp/"$2" files/"$2" || fail "Не удалось положить $2 в память приложения"
}
echo "52.3745,9.7386" >"$OUT/location_override.txt"
put_file "$OUT/location_override.txt" location_override.txt
rm -f "$OUT/location_override.txt"

set_locale en
start_app
wait_for_text "Allow" 20 || fail "[en с сетью] после сброса нет кнопки «Allow»"
tap_text '^Allow$' || fail "[en с сетью] не удалось нажать «Allow»"
tap_dialog "^While using" || fail "[en с сетью] системное окно с кнопкой «While using the app» не появилось или не закрылось"
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
put_file "$OUT/forecast_cache.json" forecast_cache.json
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

# --- 3б. Обновление протягиванием вниз ---------------------------------------------

# Верхняя граница (y) первого элемента с точным текстом: по ней видно, сдвинулся ли верх экрана.
text_top() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml 2>/dev/null | TEXT="$1" PYTHONIOENCODING=utf-8 python3 -c '
import html, os, re, sys
data = sys.stdin.buffer.read().decode("utf-8", "replace")
for node in re.findall(r"<node [^>]*>", data):
    text = re.search(r" text=\"([^\"]*)\"", node)
    bounds = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
    if text and bounds and html.unescape(text.group(1)) == os.environ["TEXT"]:
        print(bounds.group(2))
        break
'
}

# Сколько раз приложение записало в журнал «обновление протягиванием».
refresh_count() {
  adb logcat -d -s PogodaRefresh:I 2>/dev/null | tr -d '\r' | grep -c "PogodaRefresh" || true
}

size=$(adb shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1)
width=${size%x*}
height=${size#*x}
x=$((width / 2))
y_rest=$(text_top "Hannover")
[ -n "$y_rest" ] || fail "[протягивание] на экране не найдена плашка «Hannover»"
before=$(refresh_count)

# Короткое движение (около 45 dp пальцем, сдвиг экрана вдвое меньше): порог не взят, обновления нет.
adb shell input swipe "$x" $((height * 45 / 100)) "$x" $((height * 50 / 100)) 400
sleep 3
[ "$(refresh_count)" = "$before" ] || fail "[протягивание] короткое движение запустило обновление"
echo "[протягивание] короткое движение обновление не запустило"

# Длинное движение медленно, в фоне. Чтобы обновление не кончилось раньше, чем его успеют увидеть, проверочная сборка
# по файлу замедляет определение свежего места на 12 с (без сети остальное отвечает мгновенно).
echo "12000" >"$OUT/location_delay_ms.txt"
put_file "$OUT/location_delay_ms.txt" location_delay_ms.txt
rm -f "$OUT/location_delay_ms.txt"
density=$(adb shell wm density | tr -d '\r' | grep -oE '[0-9]+' | tail -1)
adb shell input swipe "$x" $((height * 30 / 100)) "$x" $((height * 62 / 100)) 4000 &
swipe_pid=$!
sleep 1.8
adb exec-out screencap -p >"$OUT/3б-протягивание-1.png" 2>/dev/null || true
adb exec-out screencap -p >"$OUT/3б-протягивание-2.png" 2>/dev/null || true
wait "$swipe_pid" || true
# Палец отпущен, обновление идёт (ещё не меньше 8 с): экран должен стоять сдвинутым на 56 dp, у индикатора подпись «Refreshing…».
y_hold=$(text_top "Hannover")
TEXTS=$(screen_texts)
adb exec-out screencap -p >"$OUT/3б-обновление-1.png" 2>/dev/null || true
adb exec-out screencap -p >"$OUT/3б-обновление-2.png" 2>/dev/null || true
[ "$(refresh_count)" -gt "$before" ] || fail "[протягивание] длинное движение не запустило обновление"
echo "[протягивание] длинное движение запустило обновление"
expect_text "Refreshing…" "протягивание"
expected_hold=$((56 * density / 160))
[ -n "$y_hold" ] || fail "[протягивание] во время обновления плашка «Hannover» пропала"
delta_hold=$((y_hold - y_rest))
[ "${delta_hold#-}" -ge $((expected_hold - 14)) ] && [ "${delta_hold#-}" -le $((expected_hold + 14)) ] \
  || fail "[протягивание] во время обновления верх экрана сдвинут на $delta_hold px, ожидалось около $expected_hold px (56 dp)"
echo "[протягивание] во время обновления верх экрана сдвинут на $delta_hold px (56 dp = $expected_hold px), индикатор подписан"

# Когда пауза кончится, экран возвращается на место и индикатор исчезает (ждём до 40 с).
deadline=$((SECONDS + 40))
while :; do
  y_now=$(text_top "Hannover")
  if [ -n "$y_now" ]; then
    delta_now=$((y_now - y_rest))
    [ "${delta_now#-}" -le 8 ] && break
  fi
  [ "$SECONDS" -lt "$deadline" ] || fail "[протягивание] после обновления экран не вернулся на место (было y=$y_rest, стало y=${y_now:-нет})"
done
adb shell run-as "$PKG" rm -f files/location_delay_ms.txt || true
echo "[протягивание] экран вернулся на место"

# Данные те же, индикатор исчез, приложение живо.
[ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ] || fail "[протягивание] приложение упало"
TEXTS=$(screen_texts)
expect_text "Offline · data from 3 h ago" "после протягивания"
! grep -qxF "Refreshing…" <<<"$TEXTS" || fail "[протягивание] после обновления индикатор «Refreshing…» не исчез"
shot "3б-после-протягивания"
check_offline ru "Нет сети · данные 3 часа назад" "По часам"
check_offline de "Kein Netz · Daten von vor 3 Std." "Stündlich"

# --- 3г. Вкладка «Прогноз» (0.3.0): прогноз на 15 суток из памяти ---------------------------

python3 "$HERE/make_cache.py" "$(dirname "$FIXTURE")/hannover-15d.json" "$OUT/forecast_cache.json" --age-hours 3
put_file "$OUT/forecast_cache.json" forecast_cache.json
rm -f "$OUT/forecast_cache.json"

check_forecast() {
  local locale="$1" pill="$2" tab="$3" title="$4" seven="$5" fourteen="$6" label="$7"
  set_locale "$locale"
  start_app
  wait_for_text "$pill" 60 || fail "[$label] нет строки «$pill»"
  tap_text "^$tab\$" || fail "[$label] не удалось нажать на вкладку «$tab»"
  wait_for_text "$title" 20 || fail "[$label] после нажатия на «$tab» нет карточки «$title»"
  expect_text "$seven" "$label"
  expect_text "$fourteen" "$label"
  expect_text "Weather data by Open-Meteo.com" "$label"
  shot "3г-прогноз-$locale"
}

check_forecast en "Offline · data from 3 h ago" "Forecast" "Next 24 hours" "7 days" "14 days" "en прогноз"

# Все 14 дней и подробности дня; кнопка «назад» возвращает к списку. Подробности проверяются мягко: если строку
# дня не удалось нажать через разметку, это предупреждение, а не сбой.
tap_text '^14 days$' || fail "[en прогноз] не удалось нажать «14 days»"
sleep 2
shot "3г-прогноз-14-дней-en"
if tap_text '^Today'; then
  if wait_for_text "Daylight" 15; then
    expect_text "UV index" "en подробности дня"
    expect_text "Wind, max." "en подробности дня"
    shot "3г-подробности-дня-en"
    adb shell input keyevent KEYCODE_BACK
    wait_for_text "Next 24 hours" 15 || fail "[en подробности дня] после «назад» нет списка прогноза"
  else
    shot "3г-подробности-дня-нет"
    annotate warning "Эмулятор" "Подробности дня не открылись после нажатия на строку «Today».
--- на экране ---
$(screen_texts)"
    adb shell input keyevent KEYCODE_BACK
  fi
else
  annotate warning "Эмулятор" "Строку дня «Today» не удалось нажать через разметку экрана: подробности дня не проверены.
--- на экране ---
$(screen_texts)"
fi
check_forecast ru "Нет сети · данные 3 часа назад" "Прогноз" "Ближайшие 24 часа" "7 дней" "14 дней" "ru прогноз"
check_forecast de "Kein Netz · Daten von vor 3 Std." "Vorhersage" "Nächste 24 Stunden" "7 Tage" "14 Tage" "de прогноз"

# --- 3д. Вкладка «Подробно» (0.4.0): плитки из прогноза в памяти; воздух без сети («нет данных») ----------

check_details() {
  local locale="$1" pill="$2" tab="$3" air="$4" nodata="$5" wind="$6" pressure="$7" uv="$8" humidity="$9" sunmoon="${10}" sunrise="${11}" label="${12}"
  set_locale "$locale"
  start_app
  wait_for_text "$pill" 60 || fail "[$label] нет строки «$pill»"
  tap_text "^$tab\$" || fail "[$label] не удалось нажать на вкладку «$tab»"
  wait_for_text "$air" 20 || fail "[$label] после нажатия на «$tab» нет плитки «$air»"
  expect_text "$nodata" "$label"
  expect_text "$wind" "$label"
  expect_text "$pressure" "$label"
  expect_text "Weather data by Open-Meteo.com" "$label"
  shot "3д-подробно-$locale"
  # Нижние плитки видны после прокрутки; сбой здесь предупреждение, если разметка экрана не отдала строки.
  local height x
  height=$(adb shell wm size | tr -d '\r' | sed -E 's/.* ([0-9]+)x([0-9]+).*/\2/' | tail -1)
  x=$(adb shell wm size | tr -d '\r' | sed -E 's/.* ([0-9]+)x([0-9]+).*/\1/' | tail -1)
  adb shell input swipe $((x / 2)) $((height * 75 / 100)) $((x / 2)) $((height * 20 / 100)) 400
  sleep 2
  TEXTS=$(screen_texts)
  if grep -qxF "$sunmoon" <<<"$TEXTS" && grep -qxF "$sunrise" <<<"$TEXTS"; then
    expect_text "$uv" "$label"
    expect_text "$humidity" "$label"
    shot "3д-подробно-внизу-$locale"
  else
    shot "3д-подробно-внизу-нет-$locale"
    annotate warning "Эмулятор" "[$label] после прокрутки нет плитки «$sunmoon» или строки «$sunrise».
--- на экране ---
$TEXTS"
  fi
}

check_details en "Offline · data from 3 h ago" "Details" "Air quality" "No data" "Wind" "Pressure" "UV index" "Humidity" "Sun & Moon" "Sunrise" "en подробно"
check_details ru "Нет сети · данные 3 часа назад" "Подробно" "Качество воздуха" "Нет данных" "Ветер" "Давление" "УФ-индекс" "Влажность" "Солнце и Луна" "Восход" "ru подробно"
check_details de "Kein Netz · Daten von vor 3 Std." "Details" "Luftqualität" "Keine Daten" "Wind" "Luftdruck" "UV-Index" "Luftfeuchte" "Sonne & Mond" "Aufgang" "de подробно"

# Прежний прогноз без суточных данных (из памяти версии до 0.3.0) возвращается для следующих проверок.
python3 "$HERE/make_cache.py" "$FIXTURE" "$OUT/forecast_cache.json" --age-hours 3
put_file "$OUT/forecast_cache.json" forecast_cache.json
rm -f "$OUT/forecast_cache.json"

# --- 3в. Небо: экран проверки --------------------------------------------------------

# Экран проверки открывается семью нажатиями подряд на номер версии (между нажатиями не больше 1,5 с).
open_sky_check() {
  local attempt coords
  for attempt in 1 2 3; do
    coords=$(exact_center "$VERSION_NAME ($VERSION_CODE)")
    [ -n "$coords" ] || fail "[небо] на экране не найден номер версии «$VERSION_NAME ($VERSION_CODE)»"
    # shellcheck disable=SC2086
    set -- $coords
    adb shell "for i in 1 2 3 4 5 6 7; do input tap $1 $2; done"
    if wait_for_text "Проверка неба" 8; then return 0; fi
  done
  fail "[небо] семь нажатий на номер версии не открыли экран проверки"
}

# Строка экрана, начинающаяся с заданного начала (например «Картины:»).
sky_line() {
  screen_texts | grep -E "^$1" | head -1 || true
}

# Частота кадров из строки «Кадров в секунду: 18,4 (экран 60 Гц)» с точкой вместо запятой.
frame_rate() {
  sky_line "Кадров в секунду:" | sed -E 's/^Кадров в секунду: ([0-9]+),([0-9]).*/\1.\2/'
}

# Снимок со свёрнутой панелью проверки (панель закрывает полэкрана), затем панель снова разворачивается.
shot_collapsed() {
  tap_text '^Свернуть$' || fail "[небо] на экране проверки нет кнопки «Свернуть»"
  sleep 2
  shot "$1"
  tap_text '^Развернуть$' || fail "[небо] нет кнопки «Развернуть» у свёрнутой панели"
  sleep 1
}

# Нажимает кнопку времени суток и ждёт, пока картины загрузятся и проявятся; в строке «Картины:» должно быть имя.
sky_scene() {
  local button="$1" name="$2" shot_name="$3"
  tap_text "^$button\$" || fail "[небо] на экране проверки нет кнопки «$button»"
  sleep 4
  TEXTS=$(screen_texts)
  grep -qE "^Картины: .*$name" <<<"$TEXTS" || fail "[небо] после «$button» в строке «Картины» нет «$name»: $(grep -E '^Картины' <<<"$TEXTS")"
  echo "[небо] $button: $(grep -E '^(Солнце|Картины|Облака|Текст)' <<<"$TEXTS" | tr '\n' '|')"
  shot_collapsed "$shot_name"
}

sky_weather() {
  local button="$1" shot_name="$2"
  tap_text "^$button\$" || fail "[небо] на экране проверки нет кнопки «$button»"
  sleep 3
  TEXTS=$(screen_texts)
  echo "[небо] $button: $(grep -E '^(Картины|Облака|Текст)' <<<"$TEXTS" | tr '\n' '|')"
  shot_collapsed "$shot_name"
}

set_locale en
start_app
wait_for_text "Offline · data from 3 h ago" 60 || fail "[небо] нет прогноза из памяти"
open_sky_check
echo "[небо] экран проверки открыт"
shot "5-проверка-неба-открыта"

sky_scene "Рассвет" "Рассвет" "5-небо-1-рассвет"
sky_scene "Утро" "Утро" "5-небо-2-утро"
sky_scene "День" "День" "5-небо-3-день"
sky_scene "Закат" "Закат" "5-небо-4-закат"
sky_scene "Сумерки" "Сумерки" "5-небо-5-сумерки"
sky_scene "Ночь" "Ночь" "5-небо-6-ночь"

# Погода при дневном небе и ночью: ночью облака и тёмные накладки видны иначе.
sky_scene "День" "День" "5-небо-3-день-повтор"
sky_weather "Ясно" "5-погода-1-ясно"
sky_weather "Малооблачно" "5-погода-2-малооблачно"
sky_weather "Облачно" "5-погода-3-облачно"
sky_weather "Дождь" "5-погода-4-дождь"
sky_weather "Гроза" "5-погода-5-гроза"
sky_weather "Снег" "5-погода-6-снег"
sky_weather "Туман" "5-погода-7-туман"
sky_scene "Ночь" "Ночь" "5-небо-6-ночь-туман"
sky_weather "Гроза" "5-погода-5-гроза-ночью"

tap_text '^Сейчас$' || fail "[небо] на экране проверки нет кнопки «Сейчас»"
sleep 3
shot "5-небо-сейчас"

# Пока снимки делались, анимации на эмуляторе были выключены (так их ставит запуск эмулятора), небо стояло.
# Включаем: облака плывут, частота кадров заметно выше нуля (на эмуляторе без видеокарты она ниже, чем на телефоне).
adb shell settings put global animator_duration_scale 1
sleep 4
tap_text '^Сбросить$' || fail "[небо] нет кнопки «Сбросить»"
# Эмулятор без видеокарты рисует кадр с размытием стекла за секунду и дольше (29.10.2026: 1–3 кадра в секунду, иногда
# строка на экране не обновлялась по полминуты), поэтому движение облаков проверяем при выключенном размытии:
# так заодно проверяется сам выключатель на экране проверки (0.2.3). С размытием цифры кадров снимаются ниже, только в журнал.
tap_text '^Размытие: вкл$' || fail "[небо] на экране проверки нет кнопки «Размытие: вкл»"
sleep 3
shot "5-небо-размытие-выключено"
# Облака должны плыть сразу (0.2.1: раньше они начинали у края покачивания и первые секунд десять почти стояли;
# 29.10.2026: «анимация неба начинается не сразу»). Строгая проверка начала дрейфа это `SkyDriftTest` на JVM.
# Здесь проверяется, что облака вообще плывут: положение на экране проверки (две цифры после запятой) должно
# уйти от первого значения не меньше чем на 0,03. Эмулятор без видеокарты рисует кадр за секунду и дольше, строка
# на экране обновляется редко (в запусках 29.10.2026 за 5 секунд она не менялась), поэтому ждём до 24 секунд.
cloud_position() {
  sky_line "Облака:" | sed -E 's/^Облака: положение ([0-9]+),([0-9]+).*/\1.\2/'
}
# Пять секунд на то, чтобы после «Сбросить» строка на экране догнала скачок положения; отсчёт идёт от неё.
sleep 5
clouds_start=$(cloud_position)
[ -n "$clouds_start" ] || fail "[небо] на экране проверки нет строки «Облака»"
clouds_moved=""
clouds_later=$clouds_start
for waited in 3 6 9 12 15 18 21 24; do
  sleep 3
  clouds_later=$(cloud_position)
  [ -n "$clouds_later" ] || continue
  if awk -v a="$clouds_start" -v b="$clouds_later" 'BEGIN { d = a - b; if (d < 0) d = -d; exit !(d >= 0.03) }'; then
    clouds_moved=$waited
    break
  fi
done
echo "[небо] облака после включения анимаций: $clouds_start -> $clouds_later (сдвиг заметен через ${clouds_moved:-нет} с)"
[ -n "$clouds_moved" ] || fail "[небо] за 24 секунды после включения анимаций облака не сдвинулись: $clouds_start -> $clouds_later"
sleep 4
rate_moving=$(frame_rate)
echo "[небо] анимации включены, размытие выключено, кадров в секунду: ${rate_moving:-нет}"
echo "[небо] $(sky_line 'Загрузка картин:')"
# Память с размытием стекла (0.2.2): только в журнал, чтобы видеть рост относительно прошлых запусков.
echo "[память] $(adb shell dumpsys meminfo "$PKG" 2>/dev/null | grep -E '^ +(Graphics|TOTAL PSS|Native Heap):?' | tr -s ' ' | tr '\n' ';')"
[ -n "$rate_moving" ] || fail "[небо] на экране проверки нет строки «Кадров в секунду»"
# Включаем размытие обратно и снимаем частоту кадров с ним (только в журнал: на эмуляторе без видеокарты она низкая).
tap_text '^Размытие: выкл$' || fail "[небо] на экране проверки нет кнопки «Размытие: выкл»"
sleep 8
rate_blur=$(frame_rate)
echo "[небо] кадров в секунду с размытием: ${rate_blur:-нет}, без размытия: ${rate_moving:-нет}"
[ -n "$rate_blur" ] || fail "[небо] после включения размытия на экране проверки нет строки «Кадров в секунду»"
if ! awk -v r="$rate_moving" 'BEGIN { exit !(r >= 5) }'; then
  annotate warning "Эмулятор" "Небо: при включённых анимациях кадров в секунду только $rate_moving (ждали не меньше 5). На эмуляторе без видеокарты это возможно, на телефоне проверить частоту кадров через экран проверки."
fi

# Анимации выключены (масштаб 0, как «Убрать анимацию»): небо должно стоять, кадры идут только от самого счётчика (раз в секунду).
adb shell settings put global animator_duration_scale 0
sleep 4
tap_text '^Сбросить$' || fail "[небо] нет кнопки «Сбросить»"
clouds_before=$(sky_line "Облака:")
sleep 9
rate_still=$(frame_rate)
clouds_after=$(sky_line "Облака:")
echo "[небо] анимации выключены, кадров в секунду: ${rate_still:-нет}; $clouds_before -> $clouds_after"
shot "5-небо-анимации-выключены"
[ -n "$rate_still" ] || fail "[небо] без анимаций на экране проверки нет строки «Кадров в секунду»"
awk -v r="$rate_still" 'BEGIN { exit !(r <= 3) }' || fail "[небо] при выключенных анимациях кадров в секунду $rate_still (ждали не больше 3): облака не остановились"
[ "$clouds_before" = "$clouds_after" ] || fail "[небо] при выключенных анимациях положение облаков изменилось: $clouds_before -> $clouds_after"
echo "[небо] при выключенных анимациях небо стоит на месте"

# Анимации снова включены: облака поплыли дальше.
adb shell settings put global animator_duration_scale 1
sleep 4
tap_text '^Сбросить$' || fail "[небо] нет кнопки «Сбросить»"
sleep 8
echo "[небо] анимации снова включены, кадров в секунду: $(frame_rate)"
adb shell settings put global animator_duration_scale 0

tap_text '^Закрыть$' || fail "[небо] на экране проверки нет кнопки «Закрыть»"
sleep 2
TEXTS=$(screen_texts)
! grep -qxF "Проверка неба" <<<"$TEXTS" || fail "[небо] экран проверки не закрылся"
[ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ] || fail "[небо] приложение упало"
echo "[небо] экран проверки закрыт"

adb shell svc wifi enable >/dev/null 2>&1 || true
adb shell svc data enable >/dev/null 2>&1 || true

crash=$(adb logcat -d -b crash | tr -d '\r')
[ -z "$crash" ] || fail "В журнале падений есть записи:
$crash"

# --- 4. Рабочая сборка ------------------------------------------------------------

if [ -n "$RELEASE_APK" ]; then
  PKG="$RELEASE_PKG"
  VERSION_NAME="$RELEASE_VERSION_NAME"
  VERSION_CODE="$RELEASE_VERSION_CODE"
  adb install -r "$RELEASE_APK" >/dev/null || fail "Рабочий APK не установился"
  adb logcat -c
  check_no_permission en "No location access" "Allow access to see the weather where you are." "Allow" "No place set" "Home"
  check_no_permission ru "Нет доступа к местоположению" "Разрешите доступ, чтобы видеть погоду там, где вы." "Разрешить" "Место не задано" "Главная"
  check_no_permission de "Kein Zugriff auf den Standort" "Erlaube den Zugriff, um das Wetter dort zu sehen, wo du bist." "Erlauben" "Kein Ort gewählt" "Start"
  # После разрешения приложение идёт определять место. На эмуляторе оно не определится (или определится),
  # важно, что приложение не падает и остаётся на экране с нижней панелью.
  adb shell pm grant "$PKG" "$PERMISSION" || fail "Не удалось выдать разрешение рабочей сборке"
  set_locale en
  start_app
  sleep 20
  TEXTS=$(screen_texts)
  expect_text "Home" "release после разрешения"
  shot "4-release-после-разрешения"
fi

crash=$(adb logcat -d -b crash | tr -d '\r')
[ -z "$crash" ] || fail "В журнале падений есть записи:
$crash"

annotate notice "Эмулятор" "Приложение установилось, запустилось, не упало; карточка без разрешения и прогноз без сети на en/ru/de показаны верно; небо: шесть времён суток и семь видов погоды показаны, при выключенных анимациях облака стоят."
