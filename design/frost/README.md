# Иней на стекле: картинки

`app/src/main/assets/frost/`: двенадцать серых WebP (качество 80), белый иней на чёрном. Сгенерировал Дима 14.11.2026
в ChatGPT и Gemini по запросу «frost on a window pane, curved feather and fern-like plumes, pure white on black background,
flat front view, no needles or cracks». Права на использование в приложении у Димы.

- `large_01…06`: крупные перья, 1254×1254, для карточек.
- `dense_01…06`: мелкие узоры, уменьшены до 768×768, для кнопок, плашек и нижнего меню.

Как из них собирается иней: `sky/GlassFrost.kt` (`FrostComposer`), рисование: `ui/components/FrostLook.kt`.
Картинки в приложении читаются по красному каналу (серые, все каналы равны).
