/* ---------- Интерфейс макета ---------- */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const S = {
  h: 16.9, wk: 'partly', lang: 'ru', anim: !reduceMotion, page: 'home', run: false,
  inkK: null, inkT: 0, layer: 0, sheet: null, fc: 7,
  place: 'gps', pinned: [], q: '', hv: 'line', sTab: 'look', font: 'manrope',
  g: { cards: 80, btn: null, alert: null, blur: 10, guard: true },
  notif: { rain: 1, storm: 1, wind: 1, pollen: 1, snow: 1, hail: 1, ice: 1, fog: 0 }, lead: 60, quiet: true,
};

/* ----- числа ----- */
const fm = (n, d = 0) => { const r = +(+n).toFixed(d); return (r < 0 ? '−' : '') + Math.abs(r).toFixed(d).replace('.', ','); };
const deg = n => fm(n) + '°';
const hhmm = h => { h = ((h % 24) + 24) % 24; let m = Math.round((h % 1) * 60), hh = Math.floor(h); if (m === 60) { m = 0; hh = (hh + 1) % 24; } return String(hh).padStart(2, '0') + ':' + String(m).padStart(2, '0'); };

/* ----- иконки (линейные, 24×24) ----- */
const I = {
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/>',
  moon: '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>',
  cloud: '<path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/>',
  partly: '<path d="M12 2v2M4.93 4.93l1.41 1.41M20 12h2M19.07 4.93l-1.41 1.41M15.947 12.65a4 4 0 0 0-5.925-4.128M13 22H7a5 5 0 1 1 4.9-6H13a3 3 0 0 1 0 6Z"/>',
  partlyN: '<path d="M10.188 8.5A6 6 0 0 1 16 4a1 1 0 0 0 6 6 6 6 0 0 1-3 5.197M13 16a3 3 0 1 1 0 6H7a5 5 0 1 1 4.9-6Z"/>',
  rain: '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242M16 14v6M8 14v6M12 16v6"/>',
  storm: '<path d="M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973M13 12l-3 5h4l-3 5"/>',
  snow: '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242M8 15h.01M8 19h.01M12 17h.01M12 21h.01M16 15h.01M16 19h.01"/>',
  fog: '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242M16 17H7M17 21H9"/>',
  hail: '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242M16 14v2M8 14v2M16 20h.01M8 20h.01M12 16v2M12 22h.01"/>',
  wind: '<path d="M17.7 7.7a2.5 2.5 0 1 1 1.8 4.3H2M9.6 4.6A2 2 0 1 1 11 8H2M12.6 19.4A2 2 0 1 0 14 16H2"/>',
  flower: '<circle cx="12" cy="12" r="3"/><path d="M12 16.5A4.5 4.5 0 1 1 7.5 12 4.5 4.5 0 1 1 12 7.5a4.5 4.5 0 1 1 4.5 4.5 4.5 4.5 0 1 1-4.5 4.5M12 7.5V9M7.5 12H9M16.5 12H15M12 16.5V15M8 8l1.88 1.88M14.12 9.88 16 8M8 16l1.88-1.88M14.12 14.12 16 16"/>',
  ice: '<path d="M12 2v20M4.2 7l15.6 10M4.2 17 19.8 7M9 4l3 2 3-2M9 20l3-2 3 2"/>',
  drop: '<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/>',
  grid: '<rect width="7" height="7" x="3" y="3" rx="1.5"/><rect width="7" height="7" x="14" y="3" rx="1.5"/><rect width="7" height="7" x="14" y="14" rx="1.5"/><rect width="7" height="7" x="3" y="14" rx="1.5"/>',
  nav: '<path d="M3 11 22 2l-9 19-2-8z"/>',
  pin: '<path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/>',
  chev: '<path d="m6 9 6 6 6-6"/>',
  sliders: '<path d="M21 4h-7M10 4H3M21 12h-9M8 12H3M21 20h-5M12 20H3M14 2v4M8 10v4M16 18v4"/>',
  home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
  map: '<path d="M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0zM15 5.764v15M9 3.236v15"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
  warn: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3ZM12 9v4M12 17h.01"/>',
  sev: '<path d="M12 16h.01M12 8v4M15.312 2a2 2 0 0 1 1.414.586l4.688 4.688A2 2 0 0 1 22 8.688v6.624a2 2 0 0 1-.586 1.414l-4.688 4.688a2 2 0 0 1-1.414.586H8.688a2 2 0 0 1-1.414-.586l-4.688-4.688A2 2 0 0 1 2 15.312V8.688a2 2 0 0 1 .586-1.414l4.688-4.688A2 2 0 0 1 8.688 2z"/>',
  play: '<path d="M7 4.5v15l12-7.5z" fill="currentColor"/>',
  search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
  plus: '<path d="M5 12h14M12 5v14"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
};
const svg = (inner, cls = 'i') => `<svg class="${cls}" viewBox="0 0 24 24" aria-hidden="true">${inner}</svg>`;
const wxI = (wk, night) => {
  switch (wk) {
    case 'clear': return night ? I.moon : I.sun;
    case 'partly': return night ? I.partlyN : I.partly;
    case 'cloudy': return I.cloud;
    default: return I[wk];
  }
};
const wxSvg = (wk, night, cls) => svg(wxI(wk, night), cls);

/* ----- тексты ----- */
const L = {
  ru: {
    here: 'Текущее место', now: 'Сейчас', today: 'Сегодня', feels: 'Ощущается как', max: 'Макс', min: 'Мин',
    hourly: 'По часам', mm: 'осадки, мм', tabs: ['Главная', 'Осадки', 'Подробно', 'Карта'], settings: 'Настройки',
    wx: { clear: 'Ясно', partly: 'Переменная облачность', cloudy: 'Пасмурно', rain: 'Дождь', storm: 'Гроза', snow: 'Снег', fog: 'Туман' },
    kind: { clear: 'Осадки', partly: 'Осадки', cloudy: 'Осадки', rain: 'Дождь', storm: 'Гроза с дождём', snow: 'Снег', fog: 'Морось' },
    days: ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'], date: 'Пн, 26 октября',
    mo: 'окт', next24: 'Ближайшие 24 часа', mmU: 'мм', none24: 'Осадков не ожидается', going: 'идёт, до', starts: 'начнётся в', total: 'за сутки',
    fc: ['7 дней', '14 дней'], fcNote: 'После 10-го дня прогноз менее надёжен', precipT: 'Осадки',
    aqi: 'Качество воздуха', aqiL: ['Отлично', 'Хорошо', 'Умеренно', 'Плохо', 'Очень плохо'],
    pollen: 'Пыльца', pol: ['Берёза', 'Злаки', 'Полынь', 'Амброзия'],
    wind: 'Ветер', gust: 'порывы до', kmh: 'км/ч', dir: 'юго-запад', press: 'Давление', hpa: 'гПа',
    trend: { clear: 'растёт', partly: 'стабильно', cloudy: 'стабильно', rain: 'падает', storm: 'быстро падает', snow: 'падает', fog: 'растёт' },
    uv: 'УФ-индекс', uvL: ['Низкий', 'Умеренный', 'Высокий'], hum: 'Влажность', vis: 'Видимость', km: 'км',
    sun: 'Солнце и Луна', rise: 'Восход', set: 'Закат', dlen: 'День', hm: ['ч', 'мин'], moonS: 'Полнолуние, 100 %',
    map: 'Карта', mock: 'макет', layers: ['Радар', 'Облака', 'Ветер'], none: 'Осадков рядом нет', tl: ['−2 ч', 'сейчас', '+2 ч'], youHere: 'Вы здесь',
    sNotif: 'Уведомления', ntf: { rain: 'Дождь', storm: 'Гроза', wind: 'Ветер', pollen: 'Пыльца', snow: 'Снег', hail: 'Град', ice: 'Гололёд', fog: 'Туман' },
    sLead: 'Предупреждать заранее', lead: ['30 мин', '1 ч', '3 ч'], sQuiet: 'Тихие часы', sQuietS: '23:00–07:00. Опасные явления проходят всегда',
    sAnim: 'Анимация погоды', sAnimS: 'Облака и осадки в фоне',
    sTabs: ['Вид', 'Уведомления'], sHv: 'Почасовой прогноз', hvO: ['Линия времени', 'Карточки'], sFont: 'Шрифт',
    plT: 'Места', gps: 'Текущее место', gpsS: 'Определяется по GPS', pinned: 'Закреплённые', pinEmpty: 'Закреплённых мест пока нет',
    search: 'Найти населённый пункт', hint: 'Введите название города, посёлка или района', noRes: 'Ничего не нашлось', limit: 'Закрепить можно до 10 мест', pinDo: 'Закрепить', unpin: 'Убрать',
    al: {
      moon: ['info', 'Сегодня полнолуние', 'Луна взойдёт в 17:20', ''],
      frost: ['warn', 'Заморозки', 'до −1° к 06:00', 'ночью'],
      gust: ['info', 'Порывы до 38 км/ч', 'во второй половине дня', 'с 15:00'],
      rainNight: ['info', 'Ночью дождь', 'до 3 мм', 'с 02:00'],
      dwdRain: ['warn', 'DWD: затяжной дождь', 'до 22 мм за 12 часов', 'до 06:00'],
      rainUp: ['info', 'Дождь усилится', 'до 2,4 мм/ч', 'с 18:00'],
      dwdStorm: ['sev', 'DWD: гроза', 'порывы до 85 км/ч, возможен град', 'до 21:00'],
      stormEnd: ['info', 'Гроза уйдёт', 'дождь утихнет к 21:30', ''],
      dwdSnow: ['warn', 'DWD: снегопад', '3–5 см до утра', 'до 07:00'],
      ice: ['info', 'Возможен гололёд', 'на дорогах', 'с 22:00'],
      dwdFog: ['warn', 'DWD: туман', 'видимость меньше 150 м', 'до 10:00'],
    },
  },
  de: {
    here: 'Aktueller Standort', now: 'Jetzt', today: 'Heute', feels: 'Gefühlt', max: 'Max', min: 'Min',
    hourly: 'Stündlich', mm: 'Niederschlag, mm', tabs: ['Start', 'Regen', 'Details', 'Karte'], settings: 'Einstellungen',
    wx: { clear: 'Klar', partly: 'Wechselhaft', cloudy: 'Bedeckt', rain: 'Regen', storm: 'Gewitter', snow: 'Schnee', fog: 'Nebel' },
    kind: { clear: 'Niederschlag', partly: 'Niederschlag', cloudy: 'Niederschlag', rain: 'Regen', storm: 'Gewitterregen', snow: 'Schnee', fog: 'Nieselregen' },
    days: ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'], date: 'Mo, 26. Oktober',
    mo: 'Okt', next24: 'Nächste 24 Stunden', mmU: 'mm', none24: 'Kein Niederschlag erwartet', going: 'läuft, bis', starts: 'beginnt um', total: 'in 24 h',
    fc: ['7 Tage', '14 Tage'], fcNote: 'Ab Tag 10 weniger zuverlässig', precipT: 'Niederschlag',
    aqi: 'Luftqualität', aqiL: ['Sehr gut', 'Gut', 'Mäßig', 'Schlecht', 'Sehr schlecht'],
    pollen: 'Pollen', pol: ['Birke', 'Gräser', 'Beifuß', 'Ambrosia'],
    wind: 'Wind', gust: 'Böen bis', kmh: 'km/h', dir: 'Südwest', press: 'Luftdruck', hpa: 'hPa',
    trend: { clear: 'steigend', partly: 'stabil', cloudy: 'stabil', rain: 'fallend', storm: 'stark fallend', snow: 'fallend', fog: 'steigend' },
    uv: 'UV-Index', uvL: ['Niedrig', 'Mäßig', 'Hoch'], hum: 'Luftfeuchte', vis: 'Sicht', km: 'km',
    sun: 'Sonne & Mond', rise: 'Aufgang', set: 'Untergang', dlen: 'Tag', hm: ['Std', 'Min'], moonS: 'Vollmond, 100 %',
    map: 'Karte', mock: 'Entwurf', layers: ['Radar', 'Wolken', 'Wind'], none: 'Kein Niederschlag in der Nähe', tl: ['−2 Std', 'jetzt', '+2 Std'], youHere: 'Hier',
    sNotif: 'Benachrichtigungen', ntf: { rain: 'Regen', storm: 'Gewitter', wind: 'Wind', pollen: 'Pollen', snow: 'Schnee', hail: 'Hagel', ice: 'Glätte', fog: 'Nebel' },
    sLead: 'Vorwarnzeit', lead: ['30 Min', '1 Std', '3 Std'], sQuiet: 'Ruhezeiten', sQuietS: '23:00–07:00. Gefährliche Ereignisse kommen immer durch',
    sAnim: 'Wetteranimation', sAnimS: 'Wolken und Niederschlag im Hintergrund',
    sTabs: ['Ansicht', 'Mitteilungen'], sHv: 'Stündliche Vorhersage', hvO: ['Zeitleiste', 'Karten'], sFont: 'Schrift',
    plT: 'Orte', gps: 'Aktueller Standort', gpsS: 'Per GPS bestimmt', pinned: 'Angeheftet', pinEmpty: 'Noch keine angehefteten Orte',
    search: 'Ort suchen', hint: 'Name einer Stadt, eines Dorfes oder Bezirks eingeben', noRes: 'Nichts gefunden', limit: 'Bis zu 10 Orte können angeheftet werden', pinDo: 'Anheften', unpin: 'Entfernen',
    al: {
      moon: ['info', 'Heute Vollmond', 'Mondaufgang um 17:20', ''],
      frost: ['warn', 'Frost', 'bis −1° gegen 06:00', 'nachts'],
      gust: ['info', 'Böen bis 38 km/h', 'am Nachmittag', 'ab 15:00'],
      rainNight: ['info', 'Regen in der Nacht', 'bis 3 mm', 'ab 02:00'],
      dwdRain: ['warn', 'DWD: Dauerregen', 'bis 22 mm in 12 Stunden', 'bis 06:00'],
      rainUp: ['info', 'Regen verstärkt sich', 'bis 2,4 mm/h', 'ab 18:00'],
      dwdStorm: ['sev', 'DWD: Gewitter', 'Böen bis 85 km/h, Hagel möglich', 'bis 21:00'],
      stormEnd: ['info', 'Gewitter zieht ab', 'Regen lässt gegen 21:30 nach', ''],
      dwdSnow: ['warn', 'DWD: Schneefall', '3–5 cm bis zum Morgen', 'bis 07:00'],
      ice: ['info', 'Glättegefahr', 'auf den Straßen', 'ab 22:00'],
      dwdFog: ['warn', 'DWD: Nebel', 'Sicht unter 150 m', 'bis 10:00'],
    },
  },
};
const alertsFor = (wk, h, l) => {
  const A = l.al, out = [];
  switch (wk) {
    case 'clear': out.push(A.moon); if (h < 9 || h >= 21) out.push(A.frost); break;
    case 'partly': out.push(A.gust); break;
    case 'cloudy': out.push(A.rainNight); break;
    case 'rain': out.push(A.dwdRain, A.rainUp); break;
    case 'storm': out.push(A.dwdStorm, A.stormEnd); break;
    case 'snow': out.push(A.dwdSnow, A.ice); break;
    case 'fog': out.push(A.dwdFog); break;
  }
  return out.slice(0, 2);
};
const WX_RU = { clear: 'Ясно', partly: 'Переменно', cloudy: 'Пасмурно', rain: 'Дождь', storm: 'Гроза', snow: 'Снег', fog: 'Туман' };
const TOWNS = [['Berlin', 'Берлин'], ['Hamburg', 'Гамбург'], ['München', 'Мюнхен'], ['Köln', 'Кёльн'], ['Bremen', 'Бремен'], ['Leipzig', 'Лейпциг'],
  ['Dresden', 'Дрезден'], ['Nürnberg', 'Нюрнберг'], ['Bonn', 'Бонн'], ['Bielefeld', 'Билефельд'], ['Hannover', 'Ганновер'], ['Braunschweig', 'Брауншвейг']];
const townName = t => (S.lang === 'ru' ? t[1] : t[0]);
const placeName = () => { if (S.place === 'gps') return L[S.lang].here; const t = S.pinned.find(p => p[0] === S.place); return t ? townName(t) : L[S.lang].here; };

/* ----- тема: применить переменные к элементу ----- */
function applyVars(el, tv) { for (const k in tv.css) el.style.setProperty(k, tv.css[k]); }
function mapVars(el, k) {
  el.style.setProperty('--land', rgb(mix([226, 232, 229], [34, 46, 56], k)));
  el.style.setProperty('--water', rgb(mix([184, 206, 220], [22, 34, 46], k)));
}

/* ---------- Главная ---------- */
let K = {};
const setHTML = (el, key, build) => { if (K[key.id] !== key.v) { K[key.id] = key.v; el.innerHTML = build(); } };

function renderHome() {
  const l = L[S.lang], t = temp(S.h, S.wk), night = sunElev(S.h) < -1, hl = hiLo(S.wk);
  const tr = Math.round(t);
  setHTML($('#num'), { id: 'num', v: tr }, () => `${tr < 0 ? '−' : ''}${Math.abs(tr)}<span class="deg">°</span>`);
  setHTML($('#hico'), { id: 'hico', v: S.wk + night }, () => wxSvg(S.wk, night));
  $('#cond').textContent = l.wx[S.wk];
  $('#meta').textContent = `${l.feels} ${deg(feels(t, S.wk))}  ·  ${l.max} ${deg(hl.hi)}, ${l.min} ${deg(hl.lo)}`;
  $('#city').textContent = placeName();
  setHTML($('#pinI'), { id: 'pinI', v: S.place === 'gps' }, () => svg(S.place === 'gps' ? I.nav : I.pin));
  $('#clock').textContent = hhmm(S.h);
  const al = alertsFor(S.wk, S.h, l);
  setHTML($('#alerts'), { id: 'al', v: S.wk + S.lang + al.length + (S.wk === 'clear' && al.length) }, () => al.map(a =>
    `<div class="alert ${a[0]}" role="status">${svg(I[a[0]])}<div class="tx"><b>${a[1]}</b><span>${a[2]}</span></div>${a[3] ? `<time>${a[3]}</time>` : ''}</div>`).join(''));
  $('#hrT').textContent = l.hourly; $('#hrU').textContent = l.mm;
  $('#hr').classList.toggle('cards', S.hv === 'cards');
  setHTML($('#hr'), { id: 'hr', v: S.wk + S.lang + Math.floor(S.h) + S.hv }, S.hv === 'cards' ? hourlyCardsHTML : hourlyHTML);
}

function hourlyCardsHTML() {
  const l = L[S.lang], data = hourly(S.h, S.wk, 12);
  return data.map((d, i) => `<div class="hcd${i ? '' : ' now'}"><span class="t">${i ? d.label : l.now}</span>${wxSvg(S.wk, d.night)}<b>${deg(Math.round(d.t))}</b><span class="p">${d.mm > 0 ? `${svg(I.drop)}${fm(d.mm, 1)}` : '–'}</span></div>`).join('');
}
function hourlyHTML() {
  const l = L[S.lang], data = hourly(S.h, S.wk, 12), COLW = 58, n = data.length;
  const ts = data.map(d => Math.round(d.t)), mn = Math.min(...ts), mx = Math.max(...ts);
  const yOf = t => (mx === mn ? 96 : 82 + (1 - (t - mn) / (mx - mn)) * 28);
  const maxMM = Math.max(1.5, ...data.map(d => d.mm));
  const xs = data.map((d, i) => i * COLW + COLW / 2), ys = ts.map(yOf);
  let path = `M${xs[0]} ${ys[0]}`;
  for (let i = 1; i < n; i++) { const mx2 = (xs[i - 1] + xs[i]) / 2; path += ` C${mx2} ${ys[i - 1]} ${mx2} ${ys[i]} ${xs[i]} ${ys[i]}`; }
  const dots = xs.map((x, i) => `<circle cx="${x}" cy="${ys[i]}" r="3.2"/>`).join('');
  let cols = '';
  data.forEach((d, i) => {
    const bh = d.mm > 0 ? Math.max(3, d.mm / maxMM * 26) : 0;
    cols += `<div class="hc" style="left:${i * COLW}px;width:${COLW}px">
      <div class="hl${i ? '' : ' now'}">${i ? d.label : `<span>${l.now}</span>`}</div>
      <div class="hi">${wxSvg(S.wk, d.night)}</div>
      <span class="tv" style="left:${COLW / 2}px;top:${ys[i] - 22}px">${deg(ts[i])}</span>
      <i class="bar${bh ? '' : ' z'}" style="${bh ? `height:${bh}px` : ''}"></i>
      <div class="mmv">${d.mm > 0 ? fm(d.mm, 1) : '–'}</div></div>`;
  });
  return `<svg class="curve" width="${n * COLW}" height="172" viewBox="0 0 ${n * COLW} 172" aria-hidden="true">
    <path class="ln" d="${path}"/>${dots}</svg>${cols}`;
}

/* ---------- Осадки: график на сутки и прогноз на 7 или 14 дней ---------- */
const FUT = [['cloudy', 10, 7, 25, .4], ['rain', 9, 6, 85, 6.2], ['rain', 8, 4, 80, 4.1], ['partly', 10, 3, 15, 0], ['clear', 11, 2, 5, 0], ['partly', 12, 5, 20, .2],
  ['cloudy', 11, 6, 30, .6], ['rain', 10, 7, 70, 3.4], ['rain', 9, 6, 65, 2.2], ['cloudy', 9, 5, 35, .5], ['partly', 10, 4, 20, 0], ['clear', 11, 3, 10, 0], ['cloudy', 10, 5, 30, .8]];
function precipHTML() {
  const l = L[S.lang], wk = S.wk, hl = hiLo(wk), nowH = Math.floor(S.h);
  const pr = Array.from({ length: 24 }, (_, i) => precipMM(nowH + i, wk)), prMax = Math.max(1, ...pr), prSum = pr.reduce((a, b) => a + b, 0);
  let verdict;
  const first = pr.findIndex(v => v > 0);
  if (first < 0) verdict = l.none24;
  else if (first === 0) { let e = 0; while (e < 24 && pr[e] > 0) e++; verdict = e >= 24 ? `${l.kind[wk]} ${S.lang === 'ru' ? 'весь день' : 'den ganzen Tag'}` : `${l.kind[wk]} ${l.going} ${hhmm(nowH + e)}`; }
  else verdict = `${l.kind[wk]} ${l.starts} ${hhmm(nowH + first)}`;
  const bars = pr.map((v, i) => `<i class="${v ? '' : 'z'}${i === 0 ? ' nw' : ''}" style="height:${v ? Math.max(6, v / prMax * 100) : 3}%"></i>`).join('');
  const ax = [0, 6, 12, 18, 24].map(i => `<span>${i === 24 ? '' : hhmm(nowH + i).slice(0, 2)}</span>`).join('');
  const n = S.fc, compact = n === 14;
  const rows = [[wk, hl.hi, hl.lo, POP[wk], prSum], ...FUT].slice(0, n);
  const mnT = Math.min(-2, ...rows.map(r => r[2])), mxT = Math.max(13, ...rows.map(r => r[1])), span = mxT - mnT;
  const list = rows.map((r, i) => {
    const wd = (i) % 7, dow = l.days[wd], date = 26 + i, dd = date > 31 ? date - 31 : date, mo = date > 31 ? (S.lang === 'ru' ? 'ноя' : 'Nov') : l.mo;
    const dn = i === 0 ? l.today : dow;
    const sub = `${dd} ${mo}`;
    const mmTx = r[4] > 0 ? fm(r[4], 1) : '';
    const far = i >= 10 ? ' far' : '';
    const pc = compact ? `<span class="pc one"><b>${r[3]}</b> %${mmTx ? ` <small>${mmTx}</small>` : ''}</span>` : `<span class="pc"><b>${r[3]} %</b><small>${mmTx ? mmTx + ' ' + l.mmU : '–'}</small></span>`;
    const dl = compact ? `<span class="d one">${dn === l.today ? dn : `${dn} <small>${dd}</small>`}</span>` : `<span class="d">${dn}<small>${sub}</small></span>`;
    return `<div class="frow${far}">${dl}${wxSvg(r[0], false)}${pc}<span class="lo">${deg(r[2])}</span>
      <div class="rg"><i style="left:${(r[2] - mnT) / span * 100}%;width:${Math.max(6, (r[1] - r[2]) / span * 100)}%"></i></div><span class="hi2">${deg(r[1])}</span></div>`;
  }).join('');
  return `<div class="flow ${compact ? 'c14' : 'c7'}">
    <div class="pg-title"><h3>${l.precipT}</h3><span>${l.date}</span></div>
    <div class="fcard glass">
      <div class="fh"><div class="nseg" role="radiogroup" aria-label="${l.precipT}">${[7, 14].map((d, i) => `<button type="button" role="radio" aria-checked="${S.fc === d}" data-fc="${d}">${l.fc[i]}</button>`).join('')}</div>${compact ? `<span class="fn">${l.fcNote}</span>` : ''}</div>
      ${list}
    </div>
    <div class="pcard glass">
      <div class="ph"><b>${l.next24}</b><span>${fm(prSum, 1)} ${l.mmU} ${l.total}</span></div>
      <div class="pv">${verdict}</div>
      <div class="pr24">${bars}</div><div class="pr-ax">${ax}</div>
    </div>
  </div>`;
}

/* ---------- Подробно: всё остальное, без прокрутки ---------- */
function tile(title, body, cls = '') { return `<div class="tile glass ${cls}"><div class="tt">${title}</div>${body}</div>`; }
const EAQI = { clear: 2, partly: 2, cloudy: 2, rain: 1, storm: 1, snow: 2, fog: 3 };
const HUM = { clear: 62, partly: 66, cloudy: 78, rain: 90, storm: 88, snow: 92, fog: 99 };
const VIS = { clear: 25, partly: 22, cloudy: 15, rain: 8, storm: 6, snow: 3, fog: .2 };

function detailsHTML() {
  const l = L[S.lang], wk = S.wk, e = sunElev(S.h);
  const aq = EAQI[wk];
  const pol = [0, 0, wk === 'clear' || wk === 'partly' ? 1 : 0, 0];
  const wind = WIND[wk], gust = Math.round(wind * 1.6), uv = uvAt(e, wk);
  const press = PRESS[wk];
  const sp = Array.from({ length: 12 }, (_, i) => { const d = wk === 'storm' ? -1.1 : wk === 'rain' ? -.5 : wk === 'clear' || wk === 'fog' ? .35 : 0; return press - d * (11 - i) + Math.sin(i * .9) * .6; });
  const sMin = Math.min(...sp), sMax = Math.max(...sp);
  const spath = sp.map((v, i) => `${i ? 'L' : 'M'}${(i / 11 * 150).toFixed(1)} ${(30 - (v - sMin) / (sMax - sMin || 1) * 26).toFixed(1)}`).join(' ');
  const day = clamp((S.h - SR) / DAYLEN, 0, 1), nightF = clamp((celestial(S.h).moonX - .1) / .8);
  const mf = e >= 0 ? day : nightF;
  const ax = 24 + mf * 272, ay = 66 - Math.sin(Math.PI * mf) * 48;
  const dl = DAYLEN, dh = Math.floor(dl), dm = Math.round((dl - dh) * 60);
  return `<div class="flow">
    <div class="pg-title"><h3>${l.tabs[2]}</h3><span>${l.date}</span></div>
    <div class="tiles">
      ${tile(l.aqi, `<div class="big">${aq}<small>/ 5</small></div><div class="sub">${l.aqiL[aq - 1]}</div><div class="scale"><i style="left:calc(${(aq - 1) / 4 * 100}% - 2px)"></i></div>`)}
      ${tile(l.pollen, `<div class="pol">${l.pol.map((n, i) => `<div><span>${n}</span><span class="dots">${[1, 2, 3].map(k => `<i class="${pol[i] >= k ? 'f' : ''}"></i>`).join('')}</span></div>`).join('')}</div>`)}
      ${tile(l.wind, `<div class="big">${wind}<small>${l.kmh}</small></div><div class="sub">${l.gust} ${gust}<br>${l.dir}</div>
        <div class="compass"><svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="29" fill="none" stroke="var(--glass-line)" stroke-width="2"/>${[0, 90, 180, 270].map(a => `<line x1="32" y1="3" x2="32" y2="8" transform="rotate(${a} 32 32)" stroke="var(--ink3)" stroke-width="2"/>`).join('')}
        <g transform="rotate(45 32 32)"><path d="M32 12v34M32 12l-6 8M32 12l6 8" fill="none" stroke="var(--acc)" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></g></svg></div>`)}
      ${tile(l.press, `<div class="big">${press}<small>${l.hpa}</small></div><div class="sub">${l.trend[wk]}</div><svg class="spark" viewBox="0 0 150 34" preserveAspectRatio="none"><path d="${spath}"/></svg>`)}
      ${tile(l.uv, `<div class="big">${uv}</div><div class="sub">${l.uvL[uv >= 6 ? 2 : uv >= 3 ? 1 : 0]}</div><div class="scale"><i style="left:calc(${Math.min(uv, 11) / 11 * 100}% - 2px)"></i></div>`)}
      ${tile(l.hum, `<div class="big">${HUM[wk]}<small>%</small></div><div class="sub">${l.vis}: ${fm(VIS[wk], VIS[wk] < 1 ? 1 : 0)} ${l.km}</div>`)}
      ${tile(l.sun, `<svg class="arc" viewBox="0 0 320 84" aria-hidden="true"><line x1="8" y1="70" x2="312" y2="70"/><path class="b" d="M24 70 Q160 -26 296 70"/>
        <path class="f" d="M24 70 Q160 -26 296 70" pathLength="1" stroke-dasharray="${e >= 0 ? mf : 0} 1"/>
        <g transform="translate(${ax.toFixed(1)} ${ay.toFixed(1)})" style="color:var(--warn)"><circle r="9" fill="var(--glass)" stroke="currentColor" stroke-width="1.5"/>${e >= 0 ? '<circle r="3.2" fill="currentColor"/>' : '<path d="M-2 -4.5a4.6 4.6 0 0 0 5.2 5.2A4.6 4.6 0 1 1 -2 -4.5z" fill="var(--ink)" transform="scale(.9)"/>'}</g></svg>
        <div class="sm3"><div>${l.rise}<b>07:10</b></div><div>${l.dlen}<b>${dh} ${l.hm[0]} ${dm} ${l.hm[1]}</b></div><div>${l.set}<b>17:02</b></div></div>`, 'wide')}
    </div></div>`;
}

/* ---------- Карта ---------- */
function mapHTML() {
  const l = L[S.lang], w = WX[S.wk], k = S.inkK || 0;
  let ov = '', note = '';
  if (S.layer === 0) {
    if (w.rain || w.snow) {
      const snow = w.snow > 0, c1 = snow ? 'rgba(196,216,238,.8)' : 'rgba(93,160,204,.62)', c2 = snow ? 'rgba(236,244,252,.9)' : 'rgba(70,176,143,.66)';
      const B = [[96, 232, 84, 46, 0], [150, 330, 112, 58, 1], [244, 290, 80, 40, 0], [62, 400, 72, 42, 1], [276, 470, 92, 46, 0], [196, 170, 62, 32, 0], [120, 560, 80, 36, 0]];
      ov = `<g class="drift" filter="url(#bl)">${B.map(b => `<ellipse cx="${b[0]}" cy="${b[1]}" rx="${b[2] * (.6 + w.rain * .5 + w.snow * .4)}" ry="${b[3] * (.6 + w.rain * .5 + w.snow * .4)}" fill="${b[4] ? c2 : c1}"/>`).join('')}
        ${w.storm ? '<ellipse cx="150" cy="330" rx="44" ry="26" fill="rgba(218,166,70,.8)"/><ellipse cx="156" cy="334" rx="20" ry="12" fill="rgba(196,84,74,.85)"/><ellipse cx="244" cy="290" rx="26" ry="16" fill="rgba(218,166,70,.75)"/>' : ''}</g>`;
    } else note = l.none;
  } else if (S.layer === 1) {
    const a = .15 + w.cloud * .5, c = k ? '216,226,242' : '255,255,255';
    ov = `<g class="drift" filter="url(#bl2)" style="opacity:${a}">${[[80, 210, 90, 50], [240, 240, 100, 56], [150, 400, 120, 64], [300, 480, 80, 50], [90, 560, 100, 50], [220, 620, 90, 44]].map(b => `<ellipse cx="${b[0]}" cy="${b[1]}" rx="${b[2]}" ry="${b[3]}" fill="rgb(${c})"/>`).join('')}</g>`;
  } else {
    const len = clamp(WIND[S.wk] / 3.2, 7, 24); let g = '';
    for (let y = 130; y < 700; y += 74) for (let x = 40; x < 384; x += 76) g += `<g transform="translate(${x + (y / 74 % 2 ? 18 : 0)} ${y}) rotate(45)"><path d="M${-len} 0H${len}M${len} 0l-5 -4M${len} 0l-5 4" fill="none" stroke="var(--acc)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></g>`;
    ov = g;
  }
  const rivers = [['M148 428C128 410 112 392 108 360S96 300 70 265C62 240 52 215 46 180'], ['M174 535C182 480 196 440 190 380S200 340 206 318'], ['M304 372C270 350 245 335 216 321S150 300 114 285']];
  const grid = [96, 192, 288].map(x => `<line x1="${x}" y1="0" x2="${x}" y2="734"/>`).join('') + [150, 300, 450, 600].map(y => `<line x1="0" y1="${y}" x2="384" y2="${y}"/>`).join('');
  const label = S.place === 'gps' ? l.youHere : placeName();
  const mark = `<circle cx="190" cy="380" r="15" fill="var(--acc)" opacity=".2"/><circle cx="190" cy="380" r="6" fill="var(--acc)" stroke="var(--land)" stroke-width="2"/><text x="202" y="372" class="mt hn">${label}</text>`;
  return `<div class="mapcard"><svg class="map" viewBox="0 0 384 734" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
    <defs><filter id="bl" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="9"/></filter><filter id="bl2" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="16"/></filter></defs>
    <rect width="384" height="734" fill="var(--land)"/><g stroke="var(--glass-line)" stroke-width="1">${grid}</g>
    ${rivers.map(r => `<path d="${r[0]}" fill="none" stroke="var(--water)" stroke-width="3.5" stroke-linecap="round"/>`).join('')}
    ${ov}<style>.mt{font:600 13px var(--font-ui);fill:var(--ink);paint-order:stroke;stroke:var(--land);stroke-width:3px;stroke-linejoin:round}</style>${mark}</svg>
    <div class="mtop"><div class="nseg glass" role="radiogroup">${l.layers.map((n, i) => `<button type="button" role="radio" aria-checked="${S.layer === i}" data-layer="${i}">${n}</button>`).join('')}</div><span class="tagm glass">${l.mock}</span></div>
    ${note ? `<div class="mnote glass">${note}</div>` : ''}
    <div class="mbot glass"><button class="rbtn glass" type="button" aria-label="Play">${svg(I.play)}</button><div class="tl"><div class="trk"><i></i><b></b></div><div class="tk"><span>${l.tl[0]}</span><span>${l.tl[1]}</span><span>${l.tl[2]}</span></div></div></div></div>`;
}

/* ---------- Шторки: настройки и места ---------- */
const NTF = ['rain', 'storm', 'wind', 'pollen', 'snow', 'hail', 'ice', 'fog'];
const FONTS = [
  ['manrope', 'Manrope', "'Manrope'", 200, 'sans-serif'], ['inter', 'Inter', "'Inter'", 200, 'sans-serif'], ['onest', 'Onest', "'Onest'", 200, 'sans-serif'],
  ['golos', 'Golos', "'Golos Text'", 400, 'sans-serif'], ['jost', 'Jost', "'Jost'", 200, 'sans-serif'], ['nunito', 'Nunito', "'Nunito'", 200, 'sans-serif'],
  ['raleway', 'Raleway', "'Raleway'", 200, 'sans-serif'], ['source', 'Source Serif', "'Source Serif 4'", 200, 'serif'],
];
function setFont(id) {
  const f = FONTS.find(x => x[0] === id) || FONTS[0]; S.font = f[0];
  const st = document.documentElement.style, stack = `${f[2]},system-ui,-apple-system,'Segoe UI',Roboto,${f[4]}`;
  st.setProperty('--font-ui', stack); st.setProperty('--font-num', stack); st.setProperty('--num-w', f[3]);
  kick();
}
function settingsHTML() {
  const l = L[S.lang], row = (t, s, k, on) => `<div class="srow"><div>${t}<small>${s}</small></div><button class="tg" type="button" role="switch" data-t="${k}" aria-checked="${on}" aria-label="${t}"></button></div>`;
  const tabs = ['look', 'notif'], sample = S.lang === 'ru' ? 'Аа' : 'Aa';
  const head = `<h4>${l.settings}</h4><div class="nseg wide" role="tablist">${tabs.map((k, i) => `<button type="button" role="tab" aria-selected="${S.sTab === k}" data-st="${k}">${l.sTabs[i]}</button>`).join('')}</div>`;
  if (S.sTab === 'notif') return `${head}
    <div class="nchips" role="group" aria-label="${l.sNotif}">${NTF.map(k => `<button type="button" class="nc" data-n="${k}" aria-pressed="${!!S.notif[k]}">${svg(I[k])}<span>${l.ntf[k]}</span></button>`).join('')}</div>
    <div class="srow"><div>${l.sLead}</div><div class="nseg" role="radiogroup">${[30, 60, 180].map((n, i) => `<button type="button" role="radio" aria-checked="${S.lead === n}" data-lead="${n}">${l.lead[i]}</button>`).join('')}</div></div>
    ${row(l.sQuiet, l.sQuietS, 'quiet', S.quiet)}`;
  return `${head}
    <div class="sl">${l.sHv}</div>
    <div class="nseg wide" role="radiogroup" aria-label="${l.sHv}">${['line', 'cards'].map((k, i) => `<button type="button" role="radio" aria-checked="${S.hv === k}" data-hv="${k}">${l.hvO[i]}</button>`).join('')}</div>
    <div class="sl">${l.sFont}</div>
    <div class="nchips" role="radiogroup" aria-label="${l.sFont}">${FONTS.map(f => `<button type="button" class="nc fo" role="radio" data-font="${f[0]}" aria-checked="${S.font === f[0]}" aria-pressed="${S.font === f[0]}" style="font-family:${f[2]},${f[4]}"><span class="fs">${sample}</span><span>${f[1]}</span></button>`).join('')}</div>
    ${row(l.sAnim, l.sAnimS, 'anim', S.anim)}`;
}
function placeResHTML() {
  const l = L[S.lang], q = S.q.trim().toLowerCase();
  if (!q) return `<p class="hint">${l.hint}</p>`;
  const res = TOWNS.filter(t => !S.pinned.some(p => p[0] === t[0]) && (t[0].toLowerCase().startsWith(q) || t[1].toLowerCase().startsWith(q))).slice(0, 4);
  if (!res.length) return `<p class="hint">${l.noRes}</p>`;
  return res.map(t => `<div class="prow"><div class="pn">${svg(I.pin)}<span>${townName(t)}</span></div><button type="button" class="pbtn" data-pin="${t[0]}" aria-label="${l.pinDo}: ${townName(t)}">${svg(I.plus)}<span>${l.pinDo}</span></button></div>`).join('');
}
function placesHTML() {
  const l = L[S.lang];
  const sel = S.place === 'gps';
  return `<h4>${l.plT}</h4>
    <button type="button" class="prow sel-row${sel ? ' on' : ''}" data-sel="gps"><div class="pn">${svg(I.nav)}<span>${l.gps}<small>${l.gpsS}</small></span></div>${sel ? svg(I.check, 'i ck') : ''}</button>
    <div class="sl">${l.pinned}</div>
    ${S.pinned.length ? S.pinned.map(t => `<div class="prow${S.place === t[0] ? ' on' : ''}"><button type="button" class="pn sel" data-sel="${t[0]}">${svg(I.pin)}<span>${townName(t)}</span>${S.place === t[0] ? svg(I.check, 'i ck') : ''}</button>
      <button type="button" class="pbtn ghost" data-unpin="${t[0]}" aria-label="${l.unpin}: ${townName(t)}">${svg(I.x)}</button></div>`).join('') : `<p class="hint">${l.pinEmpty}</p>`}
    <label class="srch">${svg(I.search)}<input id="plq" type="search" value="${S.q.replace(/"/g, '')}" placeholder="${l.search}" autocomplete="off" spellcheck="false" aria-label="${l.search}"></label>
    <div id="plres" class="pres">${placeResHTML()}</div>
    <p class="hint foot">${l.limit}</p>`;
}
function renderSheet() {
  const sh = $('#sheet');
  sh.dataset.m = S.sheet || '';
  if (S.sheet === 'settings') sh.innerHTML = settingsHTML();
  else if (S.sheet === 'places') sh.innerHTML = placesHTML();
  sh.classList.toggle('on', !!S.sheet); $('#scrim').classList.toggle('on', !!S.sheet);
}
function toggleSheet(m) {
  S.sheet = (m === undefined || m === S.sheet) ? null : m;
  if (m === null) S.sheet = null;
  if (S.sheet) renderSheet(); else { $('#sheet').classList.remove('on'); $('#scrim').classList.remove('on'); }
}

/* ---------- Сцена и тема ---------- */
const sky = $('#sky'), phone = $('#phone'), screen = $('#screen');
const scene = new Scene(sky, { seed: 11 });
scene.onTick = () => kick();                 // раз в секунду: облака сдвинулись, цвет под интерфейсом мог измениться
const fx = new CardFx($('#fx'), screen); fx.src = sky;      // капли, сугробы и запотевание на карточках
scene.onFrame = dt => fx.frame(dt);
const FX_TXT = { rain: 'Дождь: верх и левая кромка карточек мокнут, капли набухают и стекают.', storm: 'Гроза: дождь сильнее, капель на карточках больше.', snow: 'Снег: на карточках нарастает сугроб, пока идёт снег, потом он тает. Искры на снегу вспыхивают при наклоне: в приложении это наклон телефона, здесь подвигайте мышью над экраном.', fog: 'Туман: карточки запотевают. Проведите по ним пальцем или мышью, туман сотрётся и понемногу вернётся.' };
let lastPal = 0, visible = true;

/* ---------- Панель «Прозрачность стекла» ----------
   Ползунок показывает прозрачность в процентах: 0 почти матовое, 100 почти невидимое. */
const pToA = p => .94 - .009 * p, aToP = a => clamp(Math.round((.94 - a) / .009), 0, 100);
const gAlpha = () => ({ cards: S.g.cards == null ? null : pToA(S.g.cards), btn: S.g.btn == null ? null : pToA(S.g.btn), alert: S.g.alert == null ? null : pToA(S.g.alert), blur: S.g.blur, guard: S.g.guard });
const G_NAMES = { cards: 'Карточки', btn: 'Кнопки и панель', alert: 'Оповещения' };
let lastGl = 0;
function glassSync(tv, force) {
  const now = performance.now(); if (!force && now - lastGl < 140) return; lastGl = now;
  const parts = [];
  for (const k of ['cards', 'btn', 'alert']) {
    const man = S.g[k] != null, inp = $('#g_' + k), eff = aToP(tv.aEff[k]), c = tv.cc[k];
    if (!man && document.activeElement !== inp) inp.value = aToP(tv.aAuto[k]);
    $('#gv_' + k).textContent = man ? S.g[k] + ' %' : 'авто, сейчас ' + eff + ' %';
    $(`.gauto[data-g=${k}]`).setAttribute('aria-pressed', !man);
    const raised = man && eff < S.g[k], verdict = c >= 4.5 ? 'мелкий текст читается' : c >= 3 ? 'мелкий текст на грани' : 'мелкий текст плохо читается';
    const n = $('#gn_' + k); n.textContent = `${raised ? `поднято до ${eff} % ради читаемости · ` : ''}контраст ${fm(c, 1)} : 1, ${verdict}`; n.classList.toggle('warn', c < 4.5);
    parts.push(`${G_NAMES[k].toLowerCase()} ${man ? S.g[k] + ' %' : 'авто (' + eff + ' %)'}`);
  }
  $('#gv_blur').textContent = S.g.blur + ' px';
  const sc = `${hhmm(S.h)}, ${L.ru.wx[S.wk].toLowerCase()}`;
  $('#gOut').textContent = `Прозрачность: ${parts.join(', ')}, размытие ${S.g.blur} px, защита читаемости ${S.g.guard ? 'включена' : 'выключена'}. Сцена: ${sc}.`;
}
function glassBind() {
  const hit = () => { lastGl = 0; kick(); };
  $$('#glassCtl input[data-g]').forEach(i => i.addEventListener('input', () => { S.g[i.dataset.g] = +i.value; hit(); }));
  $$('#glassCtl .gauto').forEach(b => b.addEventListener('click', () => { S.g[b.dataset.g] = null; hit(); }));
  $('#g_blur').addEventListener('input', e => { S.g.blur = +e.target.value; hit(); });
  $('#gGuard').addEventListener('click', e => { S.g.guard = !S.g.guard; e.currentTarget.setAttribute('aria-checked', S.g.guard); hit(); });
  $('#gReset').addEventListener('click', () => { S.g = { cards: 80, btn: null, alert: null, blur: 10, guard: true }; $('#g_cards').value = 80; $('#g_blur').value = 10; $('#gGuard').setAttribute('aria-checked', 'true'); hit(); });
  $('#gCopy').addEventListener('click', async e => {
    const t = $('#gOut').textContent, b = e.currentTarget;
    try { await navigator.clipboard.writeText(t); b.textContent = 'Скопировано'; }
    catch (_) { const r = document.createRange(); r.selectNodeContents($('#gOut')); const sl = getSelection(); sl.removeAllRanges(); sl.addRange(r); b.textContent = 'Выделено, скопируй вручную'; }
    setTimeout(() => { b.textContent = 'Скопировать значения'; }, 1800);
  });
  const q = $('#quick'), setQ = () => { document.documentElement.style.setProperty('--qh', Math.round(q.offsetHeight) + 'px'); };
  new ResizeObserver(setQ).observe(q); setQ();
  if (matchMedia('(min-width:900px)').matches) $('#glassCtl').open = true;
}
function theme(dt) {
  const s = scene.st, q = inkScores(s);
  if (S.inkK === null) { S.inkT = q.cL > q.cD ? 1 : 0; S.inkK = S.inkT; }
  else if (S.inkT === 0 && q.cL - q.cD > .6) S.inkT = 1;
  else if (S.inkT === 1 && q.cD - q.cL > .6) S.inkT = 0;
  if (S.inkK !== S.inkT) {
    const d = S.inkT - S.inkK, st = dt * 3.2;
    S.inkK = Math.abs(d) <= st ? S.inkT : S.inkK + Math.sign(d) * st;
  }
  const tv = themeVars(s, S.inkK, gAlpha());
  applyVars(screen, tv); mapVars(screen, S.inkK);
  scene.scrim = tv.scrim;
  S.tv = tv;
  return tv;
}

function palette(tv) {
  const now = performance.now();
  if (now - lastPal < 140) return; lastPal = now;
  const s = scene.st, pal = $('#pal');
  pal.style.setProperty('--p-top', rgb(s.top)); pal.style.setProperty('--p-hor', rgb(s.hor));
  pal.style.setProperty('--p-glass', rgb(tv.glass)); pal.style.setProperty('--p-ink', rgb(tv.ink));
  const lab = ['небо сверху', 'у горизонта', 'стекло', 'текст'], hexes = [s.top, s.hor, tv.glass, tv.ink];
  $('#palSw').innerHTML = hexes.map((c, i) => `<div><i style="background:${rgb(c)}"></i>${lab[i]}<br>${toHex(c)}</div>`).join('');
  const dark = tv.k < .5;
  $('#palNote').innerHTML = `Текст ${dark ? 'тёмный' : 'светлый'}: на стекле контраст <b>${fm(tv.inkOnGlass, 1)} : 1</b>, на самом небе ${fm(tv.inkOnSky, 1)} : 1. Для мелкого текста нужно от 4,5. Цвет выбирается по контрасту, а не по времени суток.`;
}

/* ---------- Обновление ---------- */
let raf = 0, lastT = 0, dirty = true;
function update(dt) {
  scene.set(S.h, S.wk);
  const tv = theme(dt);
  renderHome();
  if (S.page === 'precip') { const key = S.wk + S.lang + Math.floor(S.h) + S.fc; if (K.pr !== key) { K.pr = key; $('#pgPrecip').innerHTML = precipHTML(); } }
  if (S.page === 'details') { const key = S.wk + S.lang + Math.floor(S.h); if (K.det !== key) { K.det = key; $('#pgDetails').innerHTML = detailsHTML(); } }
  if (S.page === 'map') { const key = S.wk + S.lang + S.layer + (S.inkK > .5) + S.place; if (K.map !== key) { K.map = key; $('#pgMap').innerHTML = mapHTML(); } }
  $('#hrOut').textContent = hhmm(S.h);
  if (document.activeElement !== $('#hr_in')) $('#hr_in').value = S.h;
  if (!scene.running) scene.draw();
  fx.sync(scene.st);
  if (!scene.running) { const k = S.wk + fx.on + S.page + S.lang + Math.floor(S.h) + S.hv; if (k !== fx.key) { fx.key = k; fx.warm(); } else fx.frame(0); }
  const fxt = fx.on ? FX_TXT[S.wk] : '', fn = $('#fxNote'); fn.hidden = !fxt; if (fxt && fn.textContent !== fxt) fn.textContent = fxt;
  palette(tv); glassSync(tv);
  gallerySync();
  widgetsSync();
}
function tick(now) {
  raf = 0;
  const dt = Math.min(.1, (now - lastT) / 1000); lastT = now;
  if (S.run) { S.h = (S.h + dt * (24 / 30)) % 24; dirty = true; }
  if (S.inkK !== S.inkT) dirty = true;
  if (dirty) { dirty = false; update(dt); }
  if (S.run || S.inkK !== S.inkT || dirty) raf = requestAnimationFrame(tick);
}
function kick() { dirty = true; if (!raf) { lastT = performance.now(); raf = requestAnimationFrame(tick); } }

/* ---------- Масштаб и видимость ---------- */
const fit = $('#fit');
function resize() {
  const w = fit.clientWidth, h = fit.clientHeight;
  const s = clamp(Math.min((w - 26) / 412, (h - 26) / 892), .3, 1.15);
  phone.style.setProperty('--s', s.toFixed(4));
  scene.setSize(clamp(s * (devicePixelRatio || 1), 1, 2.5));
  scene.draw();
  fx.setSize(clamp(s * (devicePixelRatio || 1), 1, 2.5)); fx.draw();
}
new ResizeObserver(resize).observe(fit);
function syncRun() { const on = S.anim && visible && !document.hidden; if (on) scene.start(); else scene.stop(); if (!on) scene.draw(); }
new IntersectionObserver(es => { visible = es[0].isIntersecting; syncRun(); }, { threshold: 0 }).observe(fit);
document.addEventListener('visibilitychange', syncRun);
function setAnim(v) {
  S.anim = v; scene.motion = v; screen.classList.toggle('anim-on', v);
  $('#swAnim').setAttribute('aria-checked', v);
  $('#animNote').textContent = v ? 'Облака плывут, идёт дождь' : 'Кадр замер';
  $$('.tg[data-t=anim]').forEach(b => b.setAttribute('aria-checked', v));
  syncRun(); kick();
}

/* ---------- Нижняя панель и страницы ---------- */
const PAGES = ['home', 'precip', 'details', 'map'], PG_ID = { home: '#pgHome', precip: '#pgPrecip', details: '#pgDetails', map: '#pgMap' };
function buildDock() {
  const l = L[S.lang], ic = [I.home, I.drop, I.grid, I.map];
  $('#dock').innerHTML = PAGES.map((p, i) => `<button type="button" class="${S.page === p ? 'on' : ''}" data-p="${p}" aria-label="${l.tabs[i]}" ${S.page === p ? 'aria-current="page"' : ''}>${svg(ic[i])}<span class="lb">${l.tabs[i]}</span></button>`).join('');
}
function go(p) {
  S.page = p; K.pr = K.det = K.map = null;
  $$('#dock button').forEach(b => { const on = b.dataset.p === p; b.classList.toggle('on', on); if (on) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current'); });
  for (const k of PAGES) $(PG_ID[k]).classList.toggle('on', k === p);
  toggleSheet(null); kick();
}
$('#dock').addEventListener('click', e => { const b = e.target.closest('button[data-p]'); if (b) go(b.dataset.p); });
$('#gear').addEventListener('click', () => toggleSheet('settings'));
$('#cityBtn').addEventListener('click', () => toggleSheet('places'));
$('#scrim').addEventListener('click', () => toggleSheet(null));
$('#sheet').addEventListener('click', e => {
  const t = e.target.closest('[data-t]'), n = e.target.closest('[data-n]'), ld = e.target.closest('[data-lead]');
  const st = e.target.closest('[data-st]'), hv = e.target.closest('[data-hv]'), fo = e.target.closest('[data-font]');
  const sel = e.target.closest('[data-sel]'), pin = e.target.closest('[data-pin]'), un = e.target.closest('[data-unpin]');
  if (t) { const on = t.getAttribute('aria-checked') !== 'true'; if (t.dataset.t === 'anim') setAnim(on); else { S[t.dataset.t] = on; t.setAttribute('aria-checked', on); } }
  if (n) { const k = n.dataset.n; S.notif[k] = S.notif[k] ? 0 : 1; n.setAttribute('aria-pressed', !!S.notif[k]); }
  if (ld) { S.lead = +ld.dataset.lead; $$('[data-lead]').forEach(b => b.setAttribute('aria-checked', b === ld)); }
  if (st) { S.sTab = st.dataset.st; renderSheet(); }
  if (hv) { S.hv = hv.dataset.hv; renderSheet(); kick(); }
  if (fo) { setFont(fo.dataset.font); renderSheet(); }
  if (sel) { S.place = sel.dataset.sel; K.map = null; renderSheet(); kick(); }
  if (pin) { const t2 = TOWNS.find(x => x[0] === pin.dataset.pin); if (t2 && S.pinned.length < 10) { S.pinned.push(t2); S.place = t2[0]; S.q = ''; K.map = null; renderSheet(); kick(); } }
  if (un) { S.pinned = S.pinned.filter(x => x[0] !== un.dataset.unpin); if (S.place === un.dataset.unpin) S.place = 'gps'; K.map = null; renderSheet(); kick(); }
});
$('#sheet').addEventListener('input', e => { if (e.target.id === 'plq') { S.q = e.target.value; $('#plres').innerHTML = placeResHTML(); } });
$('#pgMap').addEventListener('click', e => { const b = e.target.closest('[data-layer]'); if (b) { S.layer = +b.dataset.layer; K.map = null; kick(); } });
$('#pgPrecip').addEventListener('click', e => { const b = e.target.closest('[data-fc]'); if (b) { S.fc = +b.dataset.fc; K.pr = null; kick(); } });
document.addEventListener('keydown', e => { if (e.key === 'Escape' && S.sheet) toggleSheet(null); });
$('#gear').innerHTML = svg(I.sliders); $('#chevI').innerHTML = svg(I.chev);

/* ---------- Пульт ---------- */
function bindSeg(el, cb) {
  el.addEventListener('click', e => {
    const b = e.target.closest('button[data-v]'); if (!b) return;
    $$('button', el).forEach(x => x.setAttribute('aria-checked', x === b));
    cb(b.dataset.v);
  });
}
$('#hr_in').addEventListener('input', e => { S.h = +e.target.value; kick(); });
$('#quickT').addEventListener('click', e => { const b = e.target.closest('[data-h]'); if (!b) return; S.h = +b.dataset.h; kick(); });
$('#run').addEventListener('click', e => { S.run = !S.run; e.currentTarget.setAttribute('aria-pressed', S.run); e.currentTarget.textContent = S.run ? 'Остановить' : 'Сутки за 30 секунд'; kick(); });
$('#wxChips').innerHTML = WX_KEYS.map(k => `<button class="chip" type="button" role="radio" aria-checked="${k === S.wk}" data-wk="${k}">${WX_RU[k]}</button>`).join('');
$('#wxChips').addEventListener('click', e => { const b = e.target.closest('[data-wk]'); if (!b) return; S.wk = b.dataset.wk; $$('#wxChips .chip').forEach(x => x.setAttribute('aria-checked', x === b)); kick(); });
bindSeg($('#segLang'), v => { S.lang = v; K = {}; buildDock(); if (S.sheet) renderSheet(); galleryBuild(); widgetsBuild(); kick(); });
$('#swAnim').addEventListener('click', () => setAnim(!S.anim));
$('#swFx').addEventListener('click', e => { fx.on = !fx.on; e.currentTarget.setAttribute('aria-checked', fx.on); kick(); });

/* ---------- Галерея состояний ---------- */
const GAL = [
  [8.2, 'clear', 'Ясное утро'], [13.5, 'rain', 'Дождь днём'], [16.9, 'partly', 'Закат с облаками'], [22.5, 'clear', 'Ясная ночь, полнолуние'],
  [17.5, 'storm', 'Вечерняя гроза'], [7.6, 'fog', 'Утренний туман'], [11, 'snow', 'Снегопад'], [14, 'cloudy', 'Пасмурный день'],
];
const galScenes = [];
function galleryBuild() {
  const g = $('#gal'), l = L[S.lang];
  if (!galScenes.length) {
    g.innerHTML = GAL.map(([h, wk, nm], i) => `<figure class="mini"><div class="mini-screen" data-i="${i}"><canvas></canvas><div class="mini-ui"></div></div><figcaption>${nm}<span>${hhmm(h)}, ${deg(temp(h, wk))}</span></figcaption></figure>`).join('');
    $$('.mini-screen', g).forEach((el, i) => {
      const sc = new Scene($('canvas', el), { seed: 21 + i * 13, staticBolt: true, t0: 40 + i * 9 });
      sc.motion = false; sc.setSize(clamp((devicePixelRatio || 1), 1, 2) * .6); galScenes.push(sc);
    });
  }
  $$('.mini-screen', g).forEach((el, i) => {
    const [h, wk] = GAL[i], hr = hourly(h, wk, 9);
    const al = alertsFor(wk, h, l)[0];
    el.querySelector('.mini-ui').innerHTML = `<div class="ml">${svg(I.nav).replace('class="i"', '')}${l.here}</div>
      <div class="mn">${deg(temp(h, wk)).replace('°', '')}<span style="font-size:.5em;vertical-align:top;margin-left:2px">°</span></div><div class="mc">${l.wx[wk]}</div>
      ${al ? `<div class="ma" style="--c:var(--${al[0]})">${svg(I[al[0]]).replace('class="i"', '')}${al[1]}</div>` : ''}
      <div class="mg">${[0, 2, 4, 6, 8].map(k => `<div><span>${String(hr[k].h).padStart(2, '0')}</span>${wxSvg(wk, hr[k].night, '')}${deg(hr[k].t)}</div>`).join('')}</div>`;
  });
  galleryDraw();
}
function galleryDraw() {
  galScenes.forEach((sc, i) => {
    const [h, wk] = GAL[i], el = $$('#gal .mini-screen')[i];
    sc.set(h, wk);
    const tv = themeVars(sc.st, inkTarget(sc.st));
    applyVars(el, tv); sc.scrim = tv.scrim;
    sc.draw();
  });
}
let galDone = false;
function gallerySync() { if (!galDone) { galDone = true; galleryDraw(); } }

/* ---------- Виджеты ---------- */
const W = { wall: 'light', bg: 'glass', tr: 40, ink: 'auto' };
function widgetsBuild() {
  const l = L[S.lang], t = temp(S.h, S.wk), night = sunElev(S.h) < -1, hl = hiLo(S.wk), hr = hourly(S.h, S.wk, 7), al = alertsFor(S.wk, S.h, l)[0];
  const hrs = (n, step = 1) => `<div class="whrs">${Array.from({ length: n }, (_, i) => hr[i * step]).map(d => `<div><span>${String(d.h).padStart(2, '0')}</span>${wxSvg(S.wk, d.night)}${deg(d.t)}</div>`).join('')}</div>`;
  const items = [
    ['1×1', `<div class="wdg w11">${wxSvg(S.wk, night)}<div class="wn">${deg(t)}</div></div>`],
    ['2×1', `<div class="wdg w21"><div style="min-width:0"><div class="wn">${deg(t)}</div><div class="wsm">${l.wx[S.wk]}</div></div>${wxSvg(S.wk, night)}</div>`],
    ['2×2', `<div class="wdg w22"><div class="wcity">${l.here}</div><div class="wn" style="font-size:42px;margin-top:2px">${deg(t)}</div><div class="wsm">${l.wx[S.wk]}</div><div style="margin-top:auto">${hrs(3, 2)}</div></div>`],
    ['4×1', `<div class="wdg w41"><div><div class="wcity">${l.here}</div><div class="wn" style="font-size:34px">${deg(t)}</div></div>${hrs(5)}</div>`],
    ['4×2', `<div class="wdg w42"><div class="wrow2"><div style="display:flex;gap:12px;align-items:center;min-width:0"><div class="wn" style="font-size:40px">${deg(t)}</div><div style="min-width:0"><div class="wcity">${l.here}</div><div class="wsm">${deg(hl.hi)} / ${deg(hl.lo)}</div></div></div>${al ? `<div class="wal"><i></i>${al[1]}</div>` : ''}</div><div style="margin-top:auto">${hrs(7)}</div></div>`],
  ];
  $('#wall').innerHTML = items.map(([c, h]) => `<div class="wcol">${h}<small>${c}</small></div>`).join('');
  widgetsStyle();
}
function widgetsStyle() {
  const wall = $('#wall');
  wall.className = 'wall ' + W.wall;
  const ink = W.ink === 'auto' ? (W.wall === 'light' ? 'dark' : 'light') : W.ink;
  const dInk = ink === 'dark', glass = W.bg === 'glass';
  const tint = dInk ? GL_L : GL_D, alpha = (1 - W.tr / 100) * .66;
  wall.style.setProperty('--wink', dInk ? '#101722' : '#f4f7fb');
  wall.style.setProperty('--wbg', glass ? rgb(tint, alpha) : 'transparent');
  wall.style.setProperty('--wblur', glass ? '14px' : '0px');
  wall.style.setProperty('--wsh', glass ? `inset 0 0 0 1px ${dInk ? 'rgba(16,23,34,.08)' : 'rgba(255,255,255,.1)'},0 10px 24px -12px rgba(0,0,0,.35)` : 'none');
  $('#wTrWrap').style.display = glass ? '' : 'none';
  const wallMid = W.wall === 'light' ? [196, 208, 212] : [34, 46, 64];
  const bg = glass ? mix(wallMid, tint, alpha) : wallMid;
  const c = contrast(bg, dInk ? INK_D : INK_L);
  $('#wNote').innerHTML = `Контраст текста с фоном: <b>${fm(c, 1)} : 1</b>. ${c >= 4.5 ? 'Читается хорошо.' : c >= 3 ? 'Для крупных цифр хватает, мелкий текст читается хуже.' : 'Плохо читается. Поэтому по умолчанию цвет текста подбирается сам.'}`;
}
let wSig = '';
function widgetsSync() { const sig = S.wk + S.lang + Math.round(temp(S.h, S.wk)) + Math.floor(S.h); if (sig !== wSig) { wSig = sig; widgetsBuild(); } }
bindSeg($('#wWall'), v => { W.wall = v; widgetsStyle(); });
bindSeg($('#wBg'), v => { W.bg = v; widgetsStyle(); });
bindSeg($('#wInk'), v => { W.ink = v; widgetsStyle(); });
$('#wTr').addEventListener('input', e => { W.tr = +e.target.value; $('#wTrV').textContent = W.tr + ' %'; widgetsStyle(); });

/* ---------- Запуск ---------- */
screen.classList.toggle('anim-on', S.anim);
if (reduceMotion) { $('#swAnim').setAttribute('aria-checked', 'false'); $('#animNote').textContent = 'Выключено: в системе включено «убрать анимацию»'; }
if (matchMedia('(min-width:900px)').matches) $('#more').open = true;
buildDock(); galleryBuild(); widgetsBuild(); glassBind();
resize(); update(.016); syncRun();
SkyArt.load(typeof SKY_DATA !== 'undefined' ? SKY_DATA : null);
SkyArt.onReady(() => { scene.sampKey = ''; galleryDraw(); kick(); });
