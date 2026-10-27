# Промпты для ChatGPT: небо по времени суток (картины для фона)

Зачем: фон приложения это картины неба в стиле референса Димы («небо по часам суток», живописное аниме-небо, свет со стороны солнца). Картины делает ChatGPT, Claude сводит их в слои и анимирует (плавный переход между временами суток, медленный сдвиг, оттенки под погоду, дождь, снег, молнии и туман кодом). Claude картинки не генерирует.

Порядок: сначала 6 панорам (этап 1). Слои с прозрачностью (этап 2) только если панорам мало для глубины.

Направление света принято по ходу солнца: утром слева, вечером справа. Если в референсе иначе, поменять слова left/right.

## Как делать
Всё в одном чате, новый чат на каждую картину не нужен.
1. Сообщение 1: прикрепить референс (лист «небо по часам суток») и вставить блок STYLE. ChatGPT ответит «ready».
2. Сообщение 2: вставить текст картины MORNING (мастер). Пересоздавать, пока не понравится. Готовый файл сохранить как sky_morning.png.
3. Остальные пять картин, по одной. В каждое сообщение заново прикрепить файл MORNING и вставить готовый текст из раздела «Готовые тексты» ниже. Не просить «теперь поправь предыдущую картинку»: при правке по цепочке композиция уплывает. Каждый раз основа это MORNING.
4. Если ChatGPT перестал слушаться или стал рисовать другое, открыть новый чат, снова прикрепить MORNING и вставить нужный текст. STYLE в новом чате не обязателен: стиль держит сама картина.
5. Прислать Claude в чат вложением (скрепка), не в проект: оригиналы PNG, не скриншоты и не пересохранённые в JPEG или WebP. По три файла на время суток (панорама, облака, небо без облаков), в сообщении написать, какое это время суток. Названия файлов можно оставить как есть, Claude сопоставит. Шов для зацикливания, увеличение и цвет Claude сделает сам.

## Сообщение 1: STYLE
```
STYLE (applies to every image in this chat)
Hand-painted digital concept art of a sky, in the tradition of painterly anime background art: visible but soft brush strokes, large volumetric cumulus clouds built from layered painted shapes, bright rim light on the sunward edges, cool coloured shadows (blue-violet), atmospheric perspective (distant clouds smaller, flatter, hazier and lower in contrast; near clouds larger and more contrasted), cinematic lighting with one clear key light direction.
Not photographic, not a 3D render, not flat vector art, not a smooth airbrushed gradient.
Sky only: no ground, no horizon objects, no buildings, no people, no birds, no aircraft, no text, no watermark, no logo, and no visible sun or moon disc (the light source stays just outside the frame).
The attached reference sheet shows the lighting logic and the colour progression through the day (7-8 AM, 9-12, 13-16, 16-18, 18-19, 20+). Use it only as a guide for colour and light direction. Do not copy its clouds or composition.
Reply "ready" and wait for the next message.
```

## Сообщение 2: MORNING (мастер)
```
Image 1 of 6: MORNING (9-12 AM).
Format: wide panorama, 3:2 landscape (1536x1024).
Composition: one large cumulus cluster in the upper-right third, two or three medium puffy clouds in the middle-left, stretched thin cloud streaks and small low clouds in the bottom third, soft pale haze at the very bottom (the horizon is just below the frame). The far left and far right edges contain only low, calm clouds so the image can be looped horizontally.
Light: the sun is outside the frame at the upper left. Every cloud has warm-white lit tops and upper-left edges, a thin bright rim light along them, and cool blue-lavender shadows on the right side and underneath.
Palette: top of the frame saturated azure #3d84d7, middle #5a9fe9, bottom pale blue #8ec2f8 fading almost to white in the haze.
```

## Готовые тексты для остальных пяти
К каждому сообщению прикрепить файл MORNING. Вставлять целиком, ничего не подставлять.

### DAWN (7-8 AM)
```
Use the attached image (MORNING) as the exact base. Keep the framing, the composition, the cloud shapes and their positions, and the brush style identical. Change only the time of day to DAWN (7-8 AM). The sun is just below the horizon at the lower left, outside the frame. Clouds are dark blue-violet with a thin golden rim light on their lower-left edges. A warm golden glow runs along the bottom of the frame. Palette: top #34488a, middle #5878bd, bottom #f2cf8c with a peach band #d69f78. Do not add or remove clouds. Same format and size.
```

### AFTERNOON (13-16)
```
Use the attached image (MORNING) as the exact base. Keep the framing, the composition, the cloud shapes and their positions, and the brush style identical. Change only the time of day to AFTERNOON (13-16). The sun is high at the upper right, outside the frame. Lit tops and upper-right edges, shadows on the left side and underneath. Palette: top #4a8fe4, middle #7db5f2, bottom #a9d4f7. Do not add or remove clouds. Same format and size.
```

### SUNSET (16-18)
```
Use the attached image (MORNING) as the exact base. Keep the framing, the composition, the cloud shapes and their positions, and the brush style identical. Change only the time of day to SUNSET (16-18). The sun is low at the right, just above the horizon, outside the frame. Cloud sides facing right glow warm peach-orange, tops catch pink-gold, shadow sides are violet. Palette: top #5f78cf, middle #b394b8, bottom #f0b078, shadows #4c4c80. Do not add or remove clouds. Same format and size.
```

### DUSK (18-19)
```
Use the attached image (MORNING) as the exact base. Keep the framing, the composition, the cloud shapes and their positions, and the brush style identical. Change only the time of day to DUSK (18-19). The sun is just below the horizon at the right. Clouds are dark navy-purple with intense red-orange light on their undersides and right edges, as if lit from below the horizon. The bottom of the frame glows red-orange. Palette: top #1d2142, middle #4a3560, bottom #e07a56 with a darker band #a2503f. Do not add or remove clouds. Same format and size.
```

### NIGHT (20+)
```
Use the attached image (MORNING) as the exact base. Keep the framing, the composition, the cloud shapes and their positions, and the brush style identical. Change only the time of day to NIGHT (20+). The moon is outside the frame at the upper left. No sun glow. Clouds are deep navy with a cold blue-silver rim light on their upper-left edges. A few faint stars are allowed in the top third. Palette: top #0e1226, middle #161c38, bottom #262a4c. Do not add or remove clouds. Same format and size.
```

## Что проверить в каждой картине
- Нет диска солнца или луны, текста, подписи, птиц.
- Свет на всех облаках идёт с одной стороны, тени с другой.
- Края слева и справа спокойные (низкие облака), иначе зацикливание будет заметно.
- Композиция совпадает с мастером: те же облака на тех же местах.
- Цвета близки к указанным.

## Этап 2 (по желанию): слои для глубины
Облака отдельно от неба. Делать из готовой картины, по одной.

Облака:
```
Use the attached image as the exact base. Output only the clouds from it, on a perfectly flat, uniform pure green (#00FF00) background: no gradient, no shadow, no glow on the background, no green tint on the cloud edges. Keep the cloud shapes, positions, colours and lighting exactly as in the base image. If you can produce a PNG with real transparency, do that instead of the green background. Same format and size.
```
Небо без облаков:
```
Use the attached image as the exact base. Remove all clouds. Keep only the smooth painted sky: the same colours and brush texture, subtle painted haze streaks near the bottom, and the same light glow position. Same format and size.
```

## Ограничения
- ChatGPT отдаёт примерно 1536x1024 (иногда 1024x1536 или квадрат). На весь экран Pixel 6 Pro (1440x3120) картина будет увеличена, живописный стиль это прощает. Если модель умеет другие пропорции, просить 3:1 для панорамы.
- Совпадение композиции между временами суток приблизительное: модель перерисовывает, а не копирует. Расхождения прячутся плавным переходом.
- Прозрачность ChatGPT делает не всегда. Запасной путь: зелёный фон, Claude вырежет.
- Права на картины определяются условиями сервиса, которым Дима пользуется. Для личного приложения и друзей это не проблема, при публикации в магазине перепроверить.
