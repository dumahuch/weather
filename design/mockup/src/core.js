'use strict';
/* ---------- Ядро: чистые функции, без DOM. Проверяются в Node. ---------- */
const TAU = Math.PI * 2;
const clamp = (x, a = 0, b = 1) => (x < a ? a : x > b ? b : x);
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
const mix = (p, q, t) => [lerp(p[0], q[0], t), lerp(p[1], q[1], t), lerp(p[2], q[2], t)];
const hx = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const rgb = (c, a) => (a === undefined || a >= 1)
  ? `rgb(${Math.round(c[0])},${Math.round(c[1])},${Math.round(c[2])})`
  : `rgba(${Math.round(c[0])},${Math.round(c[1])},${Math.round(c[2])},${+a.toFixed(3)})`;
const toHex = c => '#' + c.map(v => Math.round(clamp(v, 0, 255)).toString(16).padStart(2, '0')).join('');
const lum = c => {
  const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
  return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]);
};
const contrast = (a, b) => { const la = lum(a), lb = lum(b); return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05); };
const mulberry32 = a => () => {
  a |= 0; a = a + 0x6D2B79F5 | 0;
  let t = Math.imul(a ^ a >>> 15, 1 | a);
  t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
  return ((t ^ t >>> 14) >>> 0) / 4294967296;
};

/* ---------- Солнце: демо-день, 26 октября 2026, север Германии (в приложении считается по вашей точке) ---------- */
const SR = 7.17, SS = 17.03;               // восход 07:10, закат 17:02
const DAYLEN = SS - SR;
function sunElev(h) {
  h = ((h % 24) + 24) % 24;
  if (h >= SR && h <= SS) return 26 * Math.sin(Math.PI * (h - SR) / DAYLEN);
  const night = 24 - DAYLEN;
  const hh = h > SS ? h - SS : h + 24 - SS;
  return -38 * Math.sin(Math.PI * hh / night);
}

/* ---------- Погодные состояния ---------- */
const WX_KEYS = ['clear', 'partly', 'cloudy', 'rain', 'storm', 'snow', 'fog'];
const WX = {
  clear:  { cloud: .14, rain: 0,   snow: 0,   fog: 0,   storm: 0, gray: 0,   dark: 0 },
  partly: { cloud: .45, rain: 0,   snow: 0,   fog: 0,   storm: 0, gray: .18, dark: .04 },
  cloudy: { cloud: .95, rain: 0,   snow: 0,   fog: 0,   storm: 0, gray: .62, dark: .12 },
  rain:   { cloud: 1,   rain: .62, snow: 0,   fog: .08, storm: 0, gray: .72, dark: .22 },
  storm:  { cloud: 1,   rain: 1,   snow: 0,   fog: 0,   storm: 1, gray: .80, dark: .46 },
  snow:   { cloud: .95, rain: 0,   snow: .7,  fog: .15, storm: 0, gray: .70, dark: .06 },
  fog:    { cloud: .3,  rain: 0,   snow: 0,   fog: .9,  storm: 0, gray: .55, dark: .0 },
};
const BASE = { clear: 7, partly: 9, cloudy: 8, rain: 8, storm: 11, snow: -1, fog: 4 };
const AMP  = { clear: 5, partly: 4, cloudy: 2.5, rain: 2, storm: 3, snow: 2.5, fog: 2.5 };
const WIND = { clear: 8, partly: 14, cloudy: 16, rain: 22, storm: 55, snow: 14, fog: 4 };
const PRESS = { clear: 1024, partly: 1019, cloudy: 1014, rain: 1007, storm: 998, snow: 1010, fog: 1022 };
const temp = (h, wk) => BASE[wk] + AMP[wk] * Math.sin(TAU * (h - 9) / 24);
const hiLo = wk => ({ hi: Math.round(BASE[wk] + AMP[wk]), lo: Math.round(BASE[wk] - AMP[wk]) });
const feels = (t, wk) => t - Math.max(0, (WIND[wk] - 6) * 0.075) - (WX[wk].rain ? 0.8 : 0);

/* Осадки: детерминированная псевдо-случайность, чтобы кривая не прыгала */
function precipMM(hour, wk) {
  const r = mulberry32(31 + Math.floor(hour) * 13 + wk.length)();
  switch (wk) {
    case 'rain':  return +(0.1 + r * 1.7).toFixed(1);
    case 'storm': return +(0.4 + r * 3.6).toFixed(1);
    case 'snow':  return +(0.1 + r * 0.9).toFixed(1);
    case 'cloudy': return r < .22 ? +(0.1 + r).toFixed(1) : 0;
    case 'partly': return r < .1 ? 0.2 : 0;
    default: return 0;
  }
}
const POP = { clear: 2, partly: 10, cloudy: 22, rain: 86, storm: 92, snow: 78, fog: 6 };

/* ---------- Ключевые цвета неба по высоте солнца ----------
   Откалиброваны по референсу «небо по часам суток»: насыщенная лазурь днём,
   золото у горизонта на рассвете, персик и сиреневый на закате, тёмно-синее ночью.
   Строка: [высота солнца, верх, середина, горизонт]. */
const mk = a => a.map(k => [k[0], hx(k[1]), hx(k[2]), hx(k[3])]);
const K_MORN = mk([
  [-38, '#0e1226', '#161c38', '#262a4c'], [-14, '#161b34', '#232b52', '#3a3158'], [-8, '#2c3568', '#44558f', '#8c6c86'],
  [-3, '#34488a', '#5878bd', '#d69f78'], [1, '#4a74c4', '#7fa8e0', '#f2cf8c'], [5, '#5c93e0', '#92bdf2', '#cfe2f0'],
  [12, '#4a8fe4', '#7db5f2', '#a9d4f7'], [26, '#3d84d7', '#5a9fe9', '#8ec2f8'],
]);
const K_EVE = mk([
  [-38, '#0e1226', '#161c38', '#262a4c'], [-14, '#161b34', '#2a2548', '#523047'], [-8, '#1d2142', '#4a3560', '#a2503f'],
  [-3, '#2d3470', '#7a4f86', '#e07a56'], [1, '#5f78cf', '#b394b8', '#f0b078'], [5, '#5f86dc', '#9aa4d8', '#f0c8a0'],
  [12, '#4f8ae0', '#80afe8', '#b3d2ee'], [26, '#3d84d7', '#5a9fe9', '#8ec2f8'],
]);
function skyBase(e, ev) {
  const K = ev ? K_EVE : K_MORN;
  if (e <= K[0][0]) return { top: K[0][1], mid: K[0][2], hor: K[0][3] };
  for (let i = 1; i < K.length; i++) {
    if (e <= K[i][0]) {
      const t = smooth(K[i - 1][0], K[i][0], e);
      return { top: mix(K[i - 1][1], K[i][1], t), mid: mix(K[i - 1][2], K[i][2], t), hor: mix(K[i - 1][3], K[i][3], t) };
    }
  }
  const L = K[K.length - 1];
  return { top: L[1], mid: L[2], hor: L[3] };
}
function wxTint(c, g, d) {
  const l = c[0] * .3 + c[1] * .59 + c[2] * .11;
  return mix(c, [l, l, l], g).map(v => v * (1 - d));
}
function skyState(h, wk) {
  const w = WX[wk];
  const e = sunElev(h), ev = ((h % 24) + 24) % 24 >= 12;
  const b = skyBase(e, ev);
  const day = smooth(-7, 6, e);
  let top = wxTint(b.top, w.gray, w.dark);
  let mid = wxTint(b.mid, w.gray * .9, w.dark * .9);
  let hor = wxTint(b.hor, w.gray * .85, w.dark * .75);
  top = mix(top, mid, w.gray * .3);                         // пасмурное небо ровнее
  mid = mix(mid, hor, w.gray * .25);
  const fogC = mix(mix([46, 54, 68], [208, 214, 220], day), hor, .22);
  if (w.fog > 0) { top = mix(top, fogC, w.fog * .55); mid = mix(mid, fogC, w.fog * .7); hor = mix(hor, fogC, w.fog * .8); }
  return { h, e, ev, wk, w, top, mid, hor, fogC, day, sunVis: clamp(1 - w.cloud * .9 - w.fog * .6) };
}

/* ---------- Земля: цвета дальних гряд (нужны и сцене, и теме) ---------- */
function groundColors(s) {
  const w = s.w, day = s.day, snow = clamp(w.snow * 1.2);
  const wd = smooth(4, 16, s.e), wn = 1 - smooth(-14, -6, s.e);
  const dF = mix(s.hor, [150, 184, 214], .5), dN = [96, 136, 142], dB = [74, 110, 116];
  const tF = mix(s.hor, [40, 44, 78], .4), tN = mix(s.hor, [34, 32, 62], .42), tB = mix(s.hor, [34, 30, 58], .55);
  let far = mix(mix(tF, dF, wd), [30, 36, 66], wn);
  let near = mix(mix(tN, dN, wd), [12, 16, 30], wn);
  let base = mix(mix(tB, dB, wd), [9, 12, 24], wn);
  far = wxTint(far, w.gray * .6, w.dark * .5); near = wxTint(near, w.gray * .5, w.dark * .5); base = wxTint(base, w.gray * .5, w.dark * .5);
  far = mix(far, s.fogC, w.fog * .75); near = mix(near, s.fogC, w.fog * .55); base = mix(base, s.fogC, w.fog * .45);
  if (snow > 0) {
    const sn = mix([112, 126, 154], [240, 245, 251], day);
    far = mix(far, mix(sn, s.hor, .45), snow * .85); near = mix(near, sn, snow * .92); base = mix(base, mix(sn, [150, 164, 190], .35), snow * .92);
  }
  return { far, near, base };
}

/* ---------- Динамическая тема ----------
   Цвет текста выбирается по тому, что реально под ним: небо за цифрами, небо за предупреждениями,
   дальние гряды за карточкой «по часам», земля за нижней панелью.
   Стекло прозрачное, пока читаемо, и становится плотнее ровно настолько, чтобы мелкий текст держал контраст 4,5.
   Под цифрами стекла нет, поэтому там добавляется едва заметная вуаль. */
const INK_D = [16, 23, 34], INK_L = [244, 247, 251];
const GL_L = [238, 243, 249], GL_D = [12, 19, 31];
const tints = s => { const c = mix(s.mid, s.hor, .45); return { L: mix([255, 255, 255], c, .36), D: mix(GL_D, c, .26) }; };   // стекло с цветом неба, а не серое
const BT_L = .45;                              // насколько кнопки светлее карточек
const GA_L = .26, GA_D = .26, GB_L = .52, GB_D = .46;                  // минимальная непрозрачность стекла: светлого и тёмного
const INK2A = .84, TARGET = 4.5, SC_MAX = .36;
const effInk = (bg, ink) => mix(bg, ink, INK2A);
function needAlpha(bg, tint, ink, a0, a1) {
  for (let a = a0; a < a1; a += .02) { const g = mix(bg, tint, a); if (contrast(g, effInk(g, ink)) >= TARGET) return a; }
  return a1;
}
function backdrops(s) {
  const cc = cloudColors(s), w = s.w, g = groundColors(s);
  const hero = mix(mix(s.top, s.mid, .8), cc.lit, clamp(w.cloud * 1.1) * (1 - w.dark) * .3);
  return {
    hero, alert: mix(s.mid, s.hor, .6),
    card: mix(g.near, g.base, .55),
    dock: mix(g.near, g.base, .9),
  };
}
function inkScores(s) {
  const B = backdrops(s), mid = mix(mix(s.top, s.hor, .5), s.mid, .5);
  const T = tints(s);
  const one = (ink, tint, ga, bt, gb) => {
    const sc = needAlpha(B.hero, tint, ink, 0, SC_MAX), hb = mix(B.hero, tint, sc);
    const hero = contrast(hb, effInk(hb, ink));
    const a = Math.max(needAlpha(B.alert, tint, ink, ga, .6), needAlpha(B.card, tint, ink, ga, .6));
    const aB = Math.max(needAlpha(B.dock, bt, ink, gb, .8), needAlpha(B.hero, bt, ink, gb, .8));
    // сколько нужно для читаемости без нижней границы: на случай ручной прозрачности
    const aNc = needAlpha(B.card, tint, ink, 0, .95), aNa = needAlpha(B.alert, tint, ink, 0, .95), aNb = Math.max(needAlpha(B.dock, bt, ink, 0, .95), needAlpha(B.hero, bt, ink, 0, .95));
    return { sc, hero, a, aB, aNc, aNa, aNb, score: Math.min(hero, 6) - 6 * (a - ga) - 5 * sc };
  };
  const D = one(INK_D, T.L, GA_L, mix(T.L, [255, 255, 255], BT_L), GB_L), Lw = one(INK_L, T.D, GA_D, T.D, GB_D);
  return { mid, D, L: Lw, cD: D.score, cL: Lw.score };
}
const inkTarget = s => { const q = inkScores(s); return q.cL > q.cD ? 1 : 0; };
/* k: 0 = тёмный текст на светлом стекле, 1 = светлый текст на тёмном стекле */
/* G: ручные уровни стекла в долях непрозрачности или null (авто): { cards, btn, alert, blur, guard } */
function themeVars(s, k, G) {
  const q = inkScores(s), mid = q.mid;
  const ink = mix(INK_D, INK_L, k);
  const T = tints(s), tint = mix(T.L, T.D, k);
  const bt = mix(mix(T.L, [255, 255, 255], BT_L), T.D, k);      // заливка кнопок: светлее карточек
  const aAuto = lerp(q.D.a, q.L.a, k) + .06 * Math.sin(Math.PI * k);
  const aBAuto = lerp(q.D.aB, q.L.aB, k) + .05 * Math.sin(Math.PI * k);
  const need = { cards: lerp(q.D.aNc, q.L.aNc, k), alert: lerp(q.D.aNa, q.L.aNa, k), btn: lerp(q.D.aNb, q.L.aNb, k) };
  const g = G || {}, pick = (man, auto, nd) => (man == null ? auto : (g.guard !== false ? Math.max(man, nd) : man));
  const a = pick(g.cards, aAuto, need.cards), aB = pick(g.btn, aBAuto, need.btn), aAl = g.alert == null ? aAuto : pick(g.alert, aAuto, need.alert);
  const Bd = backdrops(s), cx = (bg, t, al) => { const gg = mix(bg, t, al); return contrast(gg, effInk(gg, ink)); };
  const sc = lerp(q.D.sc, q.L.sc, k);
  const eff = mix(mid, tint, a);
  const dk = 1 - k;
  const css = {
    '--ink': rgb(ink),
    '--ink2': rgb(ink, INK2A),
    '--ink3': rgb(ink, .56),
    '--glass': rgb(tint, a),
    '--glass-alert': rgb(tint, aAl),
    '--blur': (g.blur == null ? 22 : g.blur) + 'px',
    '--btn-bg': rgb(bt, aB),
    '--btn-on': rgb([255, 255, 255], lerp(.8, .22, k)),
    '--btn-hi': rgb([255, 255, 255], lerp(.8, .2, k)),
    '--sheen': rgb([255, 255, 255], lerp(.2, .07, k)),
    '--glass-hi': rgb(mix([255, 255, 255], tint, .1), lerp(.30, .10, k)),
    '--glass-line': rgb(ink, .12),
    '--shadow': rgb(mix([20, 34, 56], [0, 0, 0], k), lerp(.16, .34, k)),
    '--btn': rgb(ink, .11),
    '--btn2': rgb(ink, .2),
    '--acc': rgb(mix([28, 86, 160], [170, 208, 250], k)),
    '--acc-soft': rgb(mix([28, 86, 160], [170, 208, 250], k), .2),
    '--info': rgb(mix([40, 100, 166], [146, 190, 238], k)),
    '--warn': rgb(mix([170, 104, 14], [242, 186, 98], k)),
    '--sev': rgb(mix([174, 48, 40], [246, 128, 114], k)),
    '--bar': rgb(mix([34, 98, 172], [152, 202, 250], k), .92),
  };
  return {
    css, k, a, aB, bt, scrim: { tint, a: sc },
    aAuto: { cards: aAuto, btn: aBAuto, alert: aAuto }, aEff: { cards: a, btn: aB, alert: aAl }, need,
    cc: { cards: cx(Bd.card, tint, a), alert: cx(Bd.alert, tint, aAl), btn: Math.min(cx(Bd.dock, bt, aB), cx(Bd.hero, bt, aB)) },
    ink, tint, mid, glass: eff,
    inkOnSky: contrast(mid, ink),
    inkOnGlass: contrast(eff, ink),
    dark: dk < .5 ? 'dark-ink' : 'light-ink',
  };
}

/* ---------- Цвета облаков: три тона и свет с одной стороны ----------
   hi — блик на стороне солнца, lit — освещённая часть, sh — тень, rim — тёплая кромка. */
const mc = a => a.map(k => [k[0], hx(k[1]), hx(k[2]), hx(k[3])]);
const CK_MORN = mc([
  [-38, '#7080b8', '#2a3260', '#121732'], [-14, '#8c8ec0', '#3a3d70', '#1a1f3e'], [-8, '#e8b890', '#8a6c9a', '#35396a'],
  [-3, '#ffe29a', '#d89aa0', '#4c4c80'], [1, '#fff0c0', '#f4d2b6', '#7a86bc'], [5, '#fff8e8', '#f2ecee', '#86a6d8'],
  [12, '#ffffff', '#f0f5fb', '#86aee0'], [26, '#ffffff', '#eef4fb', '#7ea8dc'],
]);
const CK_EVE = mc([
  [-38, '#7080b8', '#2a3260', '#121732'], [-14, '#a05a60', '#46345e', '#1c2040'], [-8, '#ff8a62', '#b04a4a', '#2c2848'],
  [-3, '#ffb27a', '#d9786a', '#4a3a64'], [1, '#ffdcb0', '#f0b494', '#5a4e72'], [5, '#fff0dc', '#f4d6c4', '#8a8cc4'],
  [12, '#ffffff', '#f0f4fa', '#84acde'], [26, '#ffffff', '#eef4fb', '#7ea8dc'],
]);
function cloudPal(e, ev) {
  const K = ev ? CK_EVE : CK_MORN;
  if (e <= K[0][0]) return { hi: K[0][1], lit: K[0][2], sh: K[0][3] };
  for (let i = 1; i < K.length; i++) {
    if (e <= K[i][0]) {
      const t = smooth(K[i - 1][0], K[i][0], e);
      return { hi: mix(K[i - 1][1], K[i][1], t), lit: mix(K[i - 1][2], K[i][2], t), sh: mix(K[i - 1][3], K[i][3], t) };
    }
  }
  const L = K[K.length - 1];
  return { hi: L[1], lit: L[2], sh: L[3] };
}
function cloudColors(s) {
  const w = s.w, day = smooth(-7, 6, s.e);
  const p = cloudPal(s.e, s.ev);
  // пасмурно: свет плоский, цвета серые; гроза: почти чёрные с синевой
  const gl = mix([70, 76, 90], [206, 212, 220], day), gh = mix([92, 98, 114], [226, 231, 238], day), gs = mix([26, 30, 42], [112, 122, 140], day);
  let hi = mix(p.hi, gh, w.gray * .78), lit = mix(p.lit, gl, w.gray * .86), sh = mix(p.sh, gs, w.gray * .8);
  const k1 = 1 - w.dark * .95, k2 = 1 - w.dark * 1.3;
  hi = hi.map(v => v * k1); lit = lit.map(v => v * k1); sh = sh.map(v => v * k2);
  return { hi, lit, sh, flat: w.gray };
}

/* ---------- Небесные тела ---------- */
const HZ = .60;                         // линия горизонта, доля высоты
const SUNH = .33;                       // подъём солнца в полдень, доля высоты
function celestial(h) {
  h = ((h % 24) + 24) % 24;
  const e = sunElev(h);
  const f = clamp((h - SR) / DAYLEN);
  const night = 24 - DAYLEN;
  const hh = h > SS ? h - SS : h + 24 - SS;
  const nf = clamp(hh / night);
  return {
    e,
    sunX: lerp(.1, .9, f),
    sunY: HZ - (Math.max(e, -4) / 26) * SUNH,
    sunA: smooth(-4, -.5, e),
    moonX: lerp(.1, .9, nf),
    moonY: HZ - Math.sin(Math.PI * nf) * .36,
    moonA: (1 - smooth(-3, 1, e)) * Math.pow(Math.sin(Math.PI * nf), .35),
  };
}

/* ---------- Данные для интерфейса ---------- */
const uvAt = (e, wk) => Math.max(0, Math.round(e / 26 * 3 * (1 - WX[wk].cloud * .55)));
function hourly(h0, wk, n = 12) {
  const out = [];
  const base = Math.floor(h0);
  for (let i = 0; i < n; i++) {
    const hh = base + i, hm = ((hh % 24) + 24) % 24;
    const e = sunElev(hm + .5);
    out.push({
      label: i === 0 ? null : String(hm).padStart(2, '0'),
      h: hm, t: temp(hm, wk), night: e < -1,
      mm: precipMM(hh, wk), pop: POP[wk],
    });
  }
  return out;
}

if (typeof module !== 'undefined') {
  module.exports = {
    TAU, clamp, lerp, smooth, mix, hx, rgb, toHex, lum, contrast, mulberry32,
    SR, SS, DAYLEN, sunElev, WX_KEYS, WX, BASE, AMP, WIND, PRESS, POP, temp, hiLo, feels, precipMM,
    skyState, groundColors, backdrops, inkScores, inkTarget, themeVars, cloudColors, celestial, uvAt, hourly, HZ, SUNH,
  };
}
