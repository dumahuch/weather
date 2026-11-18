#!/usr/bin/env bash
# Готовит ключ подписи из секретов GitHub.
# Секреты среды release: KEYSTORE_BASE64 (файл ключа в base64) и KEYSTORE_PASSWORD.
# Из сценария выпуска приходят KEY_ALIAS (постоянное имя ключа) и KEY_PASSWORD (равен KEYSTORE_PASSWORD).
# Кладёт ключ в $RUNNER_TEMP/release.jks и сообщает путь следующим шагам через KEYSTORE_PATH.
set -euo pipefail

missing=""
for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD; do
  [ -n "${!name:-}" ] || missing="$missing $name"
done
if [ -n "$missing" ]; then
  echo "::error title=Нет секретов подписи::Не заданы секреты:$missing. Добавьте их в Settings → Environments → release → Environment secrets (см. README)."
  exit 1
fi
for name in KEY_ALIAS KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then
    echo "::error title=Ошибка сценария::В сценарии выпуска не задана переменная $name."
    exit 1
  fi
done

out="${RUNNER_TEMP:?RUNNER_TEMP не задан}/release.jks"
printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$out" 2>/dev/null || {
  echo "::error title=Ключ не читается::Секрет KEYSTORE_BASE64 не похож на base64. Вставьте значение целиком, без пробелов и переносов."
  rm -f "$out"
  exit 1
}

# Ранняя проверка: ключ открывается с этим паролем и именем, чтобы не искать причину в логах Gradle.
if ! check="$(keytool -list -keystore "$out" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" 2>&1)"; then
  # Без значений секретов: только то, что можно сверить с файлом на компьютере (размер, отпечаток файла, длина пароля).
  echo "::group::Диагностика ключа (секреты не печатаются)"
  echo "Размер файла: $(wc -c < "$out") байт"
  echo "SHA-256 файла: $(sha256sum "$out" | cut -d' ' -f1)"
  echo "Длина пароля: ${#KEYSTORE_PASSWORD} знаков; пробел или перенос по краям: $([[ "$KEYSTORE_PASSWORD" =~ ^[[:space:]]|[[:space:]]$ ]] && echo да || echo нет)"
  echo "Ответ keytool: ${check//$KEYSTORE_PASSWORD/***}"
  echo "::endgroup::"
  echo "::error title=Ключ не открывается::Файл ключа не открылся с паролем KEYSTORE_PASSWORD и именем ключа $KEY_ALIAS. Проверьте значения секретов."
  rm -f "$out"
  exit 1
fi

echo "KEYSTORE_PATH=$out" >> "$GITHUB_ENV"
echo "Ключ подписи готов."
