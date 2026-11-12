# Правила R8 для PogodaApp. Compose и AndroidX приносят свои правила сами.
# Сюда добавляем только то, что понадобится для классов, читаемых по имени.

# В выпускной сборке журнал уровней v, d и i не пишется (отладочные строки). Предупреждения и ошибки (w, e) остаются:
# в них нет ни координат, ни названий мест. Проверка в CI: в выпускном APK нет меток отладочного журнала.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Apache Commons Compress (распаковка bzip2 для карты): необязательные зависимости других форматов в приложении не нужны,
# их классов в APK нет. Нужен только BZip2CompressorInputStream.
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
