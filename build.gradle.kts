// Общий файл сборки. Плагины подключаются в модуле app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
