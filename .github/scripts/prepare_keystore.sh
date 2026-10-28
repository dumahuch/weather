#!/usr/bin/env bash
# Готовит ключ подписи из секретов GitHub.
# Секреты репозитория: KEYSTORE_BASE64 (файл ключа в base64) и KEYSTORE_PASSWORD.
# Из сценария выпуска приходят KEY_ALIAS (постоянное имя ключа) и KEY_PASSWORD (равен KEYSTORE_PASSWORD).
# Кладёт ключ в $RUNNER_TEMP/release.jks и сообщает путь следующим шагам через KEYSTORE_PATH.
set -euo pipefail

missing=""
for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD; do
  [ -n "${!name:-}" ] || missing="$missing $name"
done
if [ -n "$missing" ]; then
  echo "::error title=Нет секретов подписи::Не заданы секреты:$missing. Добавьте их в Settings → Secrets and variables → Actions (см. README)."
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
if ! keytool -list -keystore "$out" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" >/dev/null 2>&1; then
  echo "::error title=Ключ не открывается::Файл ключа не открылся с паролем KEYSTORE_PASSWORD и именем ключа $KEY_ALIAS. Проверьте значения секретов."
  rm -f "$out"
  exit 1
fi

echo "KEYSTORE_PATH=$out" >> "$GITHUB_ENV"
echo "Ключ подписи готов."
