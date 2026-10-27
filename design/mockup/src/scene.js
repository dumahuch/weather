/* ---------- Сцена неба: Canvas 2D. Всё рисуется кодом из данных. ----------
   Небо: градиент из трёх цветов по высоте солнца. Облака: шары в порядке «крупные → мелкие»,
   у каждого своя тень, освещённая часть и блик со стороны солнца, поэтому свет везде идёт в одну сторону. */
const SCENE_W = 412, SCENE_H = 892;

/* ---------- Картины неба ----------
   Для каждого из шести времён суток две картинки: небо без облаков и облака с прозрачностью.
   Чтобы быстро оценивать цвет под интерфейсом, с каждой снимается маленькая копия. */
const SkyArt = {
  ready: false, img: {}, th: {}, cbs: [],
  load(data) {
    if (!data) return;
    const names = SKY_TIMES, total = names.length * 2; let n = 0;
    for (const t of names) {
      this.img[t] = []; this.th[t] = [];
      data[t].forEach((uri, i) => {
        const im = new Image();
        im.onload = () => {
          const cv = document.createElement('canvas'); cv.width = 192; cv.height = 128;
          cv.getContext('2d').drawImage(im, 0, 0, 192, 128);
          this.img[t][i] = im; this.th[t][i] = cv;
          if (++n === total) { this.ready = true; this.cbs.forEach(f => f()); }
        };
        im.src = uri;
      });
    }
  },
  onReady(f) { if (this.ready) f(); else this.cbs.push(f); },
};

function softBlob(g, x, y, r, c, a) {
  const gr = g.createRadialGradient(x, y, 0, x, y, r);
  gr.addColorStop(0, rgb(c, a));
  gr.addColorStop(.55, rgb(c, a * .78));
  gr.addColorStop(1, rgb(c, 0));
  g.fillStyle = gr;
  g.beginPath(); g.arc(x, y, r, 0, TAU); g.fill();
}

/* Формы облаков в долях ширины; y = 0 — основание, вверх отрицательные. [x, y, радиус, сплюснутость] */
function genCumulus(r, tall) {
  const out = [];
  const bump = (x, y, rad, depth) => {                            // шар и «завитки» на его верхней половине
    out.push([x, y, rad, 1]);
    if (depth >= 2 || rad < .045) return;
    const n = 3 + ((r() * 2) | 0);
    for (let i = 0; i < n; i++) {
      const ang = -Math.PI * (.04 + .92 * (i + r() * .6) / n), cr = rad * (.46 + r() * .24);
      bump(x + Math.cos(ang) * rad * .82, y + Math.sin(ang) * rad * .82, cr, depth + 1);
    }
  };
  const nb = 6 + ((r() * 3) | 0);
  for (let i = 0; i < nb; i++) {                                  // плоское основание
    const u = (i + .5) / nb, bell = Math.sin(Math.PI * (.05 + .9 * u));
    const rad = (.06 + .07 * bell) * (.8 + r() * .5);
    bump(.05 + .9 * u + (r() - .5) * .025, -rad * .7, rad, 1);
  }
  const nt = (tall ? 3 : 2) + ((r() * 2) | 0);
  for (let t = 0; t < nt; t++) {                                  // башни: шары уменьшаются вверх
    const u = .2 + .6 * (t + r() * .8) / nt, bell = Math.sin(Math.PI * u);
    const hmax = (tall ? .5 : .3) * (.45 + .55 * bell) * (.75 + r() * .4);
    let rad = (tall ? .14 : .11) * (.7 + .5 * bell), x = .05 + .9 * u, y = -rad * .9;
    while (rad > .05) {
      bump(x + (r() - .5) * .02, y, rad, 0);
      y -= rad * (.7 + r() * .2);
      if (-y > hmax) break;
      rad *= .82 + r() * .1; x += (r() - .5) * .06;
    }
  }
  return out.sort((a, b) => b[2] - a[2]);
}
function genStrip(r) {
  const out = [], n = 9 + ((r() * 4) | 0);
  for (let i = 0; i < n; i++) {
    const u = (i + .5) / n, bell = Math.sin(Math.PI * (.04 + .92 * u));
    const rad = (.05 + .075 * bell) * (.7 + r() * .6);
    out.push([.04 + .92 * u + (r() - .5) * .03, -rad * .38 + (r() - .5) * .02, rad, .42 + r() * .12]);
  }
  return out.sort((a, b) => b[2] - a[2]);
}
function genDeck(r) {
  const out = [], n = 16;
  for (let i = 0; i < n; i++) {
    const u = (i + .5) / n, rad = .1 + r() * .12;
    out.push([u * 1.0, -rad * .35 + (r() - .5) * .05, rad, .5 + r() * .12]);
  }
  for (let i = 0; i < 12; i++) {
    const u = (i + .5) / 12, rad = .08 + r() * .08;
    out.push([u, -.1 - r() * .12, rad, .55]);
  }
  return out.sort((a, b) => b[2] - a[2]);
}

class Scene {
  constructor(cv, opt = {}) {
    this.cv = cv; this.ctx = cv.getContext('2d');
    this.off = document.createElement('canvas'); this.octx = this.off.getContext('2d');
    this.W = SCENE_W; this.H = SCENE_H; this.q = 1;
    this.seed = opt.seed || 11;
    this.staticBolt = !!opt.staticBolt;
    this.h = 12; this.wk = 'clear';
    this.t = opt.t0 || 0; this.motion = true;
    this.flash = 0; this.again = 0; this.nextFlash = 2.5; this.bolt = null;
    this.pkey = ''; this.rain = []; this.snow = []; this.splash = []; this.drops = []; this.spAcc = 0;
    this.fogTile = null; this.fogSig = '';
    this.running = false; this.raf = 0; this.last = 0; this.fps = 30;
    this.dph = 0; this.tickAcc = 0; this.onTick = null;                 // фаза дрейфа облаков, таймер пересчёта темы
    this.an = null; this.samp = null; this.sampKey = ''; this.sw = null; this.pan = .5;
    const r = mulberry32(this.seed + 99);
    this.stars = [];
    for (let i = 0; i < 160; i++) this.stars.push({ x: r(), y: r() * .62, s: r() * 1.1 + .35, p: r() * TAU, b: .4 + r() * .6 });
    this.mkClouds();
  }

  mkClouds() {
    const r = mulberry32(this.seed + 5);
    const L = [
      { n: 6, d: 0, gen: () => genStrip(r), y: [.53, .61], w: [150, 270], sp: .35, a: .92, th0: 0 },
      { n: 6, d: 1, gen: () => genCumulus(r, 0), y: [.43, .55], w: [150, 240], sp: .7, a: .96, th0: .06 },
      { n: 5, d: 2, gen: () => genCumulus(r, 1), y: [.30, .47], w: [300, 460], sp: 1.1, a: 1, th0: .12 },
      { n: 4, d: 3, gen: () => genCumulus(r, 1), y: [.16, .34], w: [380, 560], sp: 1.7, a: 1, th0: .3 },
      { n: 3, d: 4, gen: () => genDeck(r), y: [.07, .2], w: [760, 940], sp: .5, a: 1, th0: .55, deck: true },
    ];
    this.clouds = [];
    for (const l of L) for (let i = 0; i < l.n; i++) {
      const f = (i + r() * .7) / l.n;
      this.clouds.push({
        d: l.d, deck: !!l.deck, x: r(), y: lerp(l.y[0], l.y[1], r()), w: lerp(l.w[0], l.w[1], r()),
        sp: l.sp, a: l.a, th: l.deck ? l.th0 + f * .25 : l.th0 + f * (.88 - l.th0), c: l.gen(), ph: r() * TAU,
      });
    }
    this.clouds.sort((a, b) => a.d - b.d);
  }

  setSize(q) {
    q = Math.max(.5, q);
    this.q = q;
    for (const cv of [this.cv, this.off]) { cv.width = Math.round(this.W * q); cv.height = Math.round(this.H * q); }
    this.ctx.setTransform(q, 0, 0, q, 0, 0);
    this.octx.setTransform(q, 0, 0, q, 0, 0);
  }

  set(h, wk) {
    this.h = h; this.wk = wk;
    this.st = skyState(h, wk);
    this.an = { top: this.st.top, mid: this.st.mid, hor: this.st.hor };    // небо по формулам: нужно для погодной вуали
    this.sw = skyWeights(h); this.pan = skyPan(this.sw);
    if (SkyArt.ready) {
      const key = h.toFixed(3) + wk + Math.round(this.t / .8);
      if (key !== this.sampKey) { this.sampKey = key; this.sample(); }
      if (this.samp) { this.st.top = this.samp.top; this.st.mid = this.samp.mid; this.st.hor = this.samp.hor; this.st.bk = this.samp.bk; }
    }
    this.cel = celestial(h);
    this.cc = cloudColors(this.st);
    this.ensureParticles();
    if (this.st.w.fog > 0) this.ensureFog();
  }

  /* --- осадки --- */
  ensureParticles() {
    const w = this.st.w;
    const rainN = w.rain > 0 ? Math.round(120 + w.rain * 560) : 0;
    const snowN = w.snow > 0 ? Math.round(120 + w.snow * 420) : 0;
    const dropN = w.rain > .3 ? 12 : 0;
    const key = rainN + '/' + snowN + '/' + dropN;
    if (key === this.pkey) return;
    this.pkey = key;
    const r = mulberry32(this.seed + 400), W = this.W, H = this.H;
    this.rain = []; this.snow = []; this.splash = []; this.drops = [];
    for (let i = 0; i < rainN; i++) {
      const z = r();
      this.rain.push({ x: r() * W * 1.5 - W * .25, y: r() * H, z, l: 7 + z * 22 + r() * 6, v: 520 + z * 780 + r() * 160 });
    }
    for (let i = 0; i < snowN; i++) {
      const z = r();
      this.snow.push({ x: r() * W * 1.3 - W * .15, y: r() * H, z, ph: r() * TAU, s: (.7 + z * 3.6) * (.7 + r() * .5), v: 18 + z * 62 + r() * 12 });
    }
    for (let i = 0; i < dropN; i++) {
      this.drops.push({ x: 14 + r() * (W - 28), y: r() * H * .92, r: 2 + r() * 4.2, slide: 0, vy: 0, trail: [], ph: r() });
    }
  }
  step(dt) {
    const W = this.W, H = this.H, wind = WIND[this.wk], hy = H * HZ, w = this.st.w;
    const gust = .5 + .5 * Math.sin(this.t * .33);
    this.dph += dt * TAU / (150 - wind * 1.5);                                // ветер ускоряет дрейф облаков
    if (this.rain.length) {
      const sl = clamp((wind * (.8 + .4 * gust)) / 90, 0, .55);
      for (const p of this.rain) {
        p.y += p.v * dt; p.x += p.v * sl * dt;
        if (p.y > H + 30) { p.y -= H + 60; p.x = Math.random() * W * 1.5 - W * .25; }
        if (p.x > W * 1.3) p.x -= W * 1.6;
      }
      this.spAcc += SkyArt.ready ? 0 : dt * w.rain * 46;                       // брызги на земле только у нарисованной земли
      while (this.spAcc > 1) {
        this.spAcc -= 1;
        if (this.splash.length < 70) this.splash.push({ x: Math.random() * W, y: hy + H * .03 + Math.random() * (H * .94 - hy - H * .03), t: 0, m: 4 + Math.random() * 6 });
      }
      for (const s of this.splash) s.t += dt;
      this.splash = this.splash.filter(s => s.t < .42);
      for (const d of this.drops) {
        if (d.slide > 0) {
          d.y += d.vy * dt; d.slide -= dt; d.x += (Math.random() - .5) * .25;
          if (!d.trail.length || d.y - d.trail[d.trail.length - 1][1] > 5) { d.trail.push([d.x, d.y]); if (d.trail.length > 16) d.trail.shift(); }
        } else {
          if (d.trail.length && Math.random() < dt * 1.4) d.trail.shift();
          if (Math.random() < dt * .16) { d.slide = .5 + Math.random() * 1.1; d.vy = 36 + Math.random() * 54; }
        }
        if (d.y > H + 10) { d.y = -8; d.x = 14 + Math.random() * (W - 28); d.trail = []; }
      }
    }
    if (this.snow.length) {
      for (const p of this.snow) {
        p.y += p.v * dt;
        p.x += (Math.sin(this.t * .9 + p.ph) * (9 + p.z * 12) + wind * .5 * (.4 + gust)) * dt;
        if (p.y > H + 8) { p.y -= H + 16; p.x = Math.random() * W * 1.3 - W * .15; }
        if (p.x > W + 12) p.x -= W + 24;
      }
    }
    if (w.storm && this.motion) {
      this.nextFlash -= dt;
      if (this.nextFlash <= 0) { this.flash = 1; this.again = .17; this.nextFlash = 6 + Math.random() * 8; this.bolt = this.mkBolt(); }
      if (this.again > 0) { this.again -= dt; if (this.again <= 0) this.flash = Math.max(this.flash, .7); }
    }
    this.flash = Math.max(0, this.flash - dt * 3.2);
  }
  mkBolt() {
    const W = this.W, H = this.H, endY = H * (HZ - .03 + Math.random() * .08), segs = [];
    const walk = (x, y, ang, len, width, depth) => {
      let px = x, py = y;
      for (let i = 0, n = Math.ceil(len / 14); i < n; i++) {
        const a = ang + (Math.random() - .5) * .95, sl = 10 + Math.random() * 12;
        const nx = px + Math.sin(a) * sl, ny = py + Math.cos(a) * sl;
        segs.push([px, py, nx, ny, width]); px = nx; py = ny;
        if (depth < 2 && Math.random() < .15) walk(px, py, ang + (Math.random() < .5 ? -1 : 1) * (.5 + Math.random() * .5), len * .35, width * .55, depth + 1);
        if (py > endY) break;
      }
    };
    walk(W * (.15 + Math.random() * .7), -4, (Math.random() - .5) * .25, endY, 2.2, 0);
    return segs;
  }

  /* --- цикл --- */
  start() {
    if (this.running) return;
    this.running = true; this.last = performance.now();
    const minDt = 1000 / this.fps;
    const tick = now => {
      if (!this.running) return;
      this.raf = requestAnimationFrame(tick);
      const dt = now - this.last;
      if (dt < minDt - 2) return;
      this.last = now;
      const d = Math.min(dt, 100) / 1000;
      this.t += d; this.step(d); this.draw();
      if (SkyArt.ready && this.onTick && (this.tickAcc += d) > .8) { this.tickAcc = 0; this.onTick(); }
    };
    this.raf = requestAnimationFrame(tick);
  }
  stop() { this.running = false; cancelAnimationFrame(this.raf); }

  /* --- отрисовка --- */
  draw() {
    const c = this.ctx, s = this.st;
    if (!s) return;
    const art = SkyArt.ready && this.sw;
    if (art) this.paint_(c, this.octx, s, false);
    else {
      this.skyFill(c, s);
      this.stars_(c, s, this.t);
      this.moon_(c, s);
      this.sun_(c, s);
      this.clouds_(c, s, this.t);
    }
    this.scrim_(c);
    if (!art) this.ground_(c, s);
    this.fog_(c, s, this.t);
    this.precip_(c, s);
    this.lightning_(c, s);
    this.glassDrops_(c, s);
  }

  skyFill(c, s) {
    const W = this.W, H = this.H, hy = H * HZ;
    const g = c.createLinearGradient(0, 0, 0, hy + H * .02);
    g.addColorStop(0, rgb(s.top));
    g.addColorStop(.5, rgb(s.mid));
    g.addColorStop(1, rgb(s.hor));
    c.fillStyle = g; c.fillRect(0, 0, W, H);
    // свечение у горизонта в сторону солнца: золото на рассвете, персик на закате
    const glow = (1 - smooth(-1, 14, s.e)) * smooth(-10, -1, s.e) * (1 - s.w.gray * .8) * (1 - s.w.fog * .7);
    if (glow > .01) {
      const gx = this.cel.sunX * W, gy = hy;
      const rg = c.createRadialGradient(gx, gy, 0, gx, gy, W * 1.05);
      const col = s.ev ? [255, 156, 98] : [255, 190, 120];
      rg.addColorStop(0, rgb(col, .5 * glow)); rg.addColorStop(.45, rgb(col, .16 * glow)); rg.addColorStop(1, rgb(col, 0));
      c.fillStyle = rg; c.fillRect(0, 0, W, H);
    }
    // дымка у горизонта: небо светлеет к земле
    const hz = c.createLinearGradient(0, hy - H * .2, 0, hy + H * .02);
    hz.addColorStop(0, rgb(s.hor, 0)); hz.addColorStop(1, rgb(s.hor, .38));
    c.fillStyle = hz; c.fillRect(0, hy - H * .2, W, H * .22);
  }

  stars_(c, s, t) {
    const vis = (1 - smooth(-11, -2, s.e)) * (1 - s.w.cloud * .95) * (1 - s.w.fog);
    if (vis < .02) return;
    for (const st of this.stars) {
      const tw = this.motion ? .78 + .22 * Math.sin(t * 1.3 + st.p) : 1;
      c.fillStyle = `rgba(236,240,255,${(vis * st.b * tw).toFixed(3)})`;
      if (st.s > 1.05) { c.beginPath(); c.arc(st.x * this.W, st.y * this.H, st.s * .62, 0, TAU); c.fill(); }
      else c.fillRect(st.x * this.W, st.y * this.H, st.s, st.s);
    }
  }

  sun_(c, s) {
    const cel = this.cel, W = this.W, H = this.H;
    const a = cel.sunA * Math.pow(Math.max(s.sunVis, .0001), .8);
    const aGlow = cel.sunA * (1 - s.w.rain * .9) * (1 - s.w.storm) * (.25 + .75 * s.sunVis);   // сквозь облака остаётся пятно света
    if (aGlow < .02) return;
    const x = cel.sunX * W, y = cel.sunY * H;
    const warm = 1 - smooth(0, 16, s.e);
    const col = mix([255, 248, 232], [255, 168, 104], warm);
    c.globalCompositeOperation = 'screen';
    softBlob(c, x, y, 300, col, (.1 + .2 * warm) * aGlow);
    softBlob(c, x, y, 110, col, (.28 + .3 * warm) * aGlow);
    c.globalCompositeOperation = 'source-over';
    if (a > .02) {
      c.fillStyle = rgb(mix([255, 253, 246], [255, 226, 186], warm), a);
      c.beginPath(); c.arc(x, y, 17, 0, TAU); c.fill();
    }
  }

  moon_(c, s) {
    const cel = this.cel, W = this.W, H = this.H;
    const a = cel.moonA * (1 - s.w.cloud * .85) * (1 - s.w.fog * .6);
    if (a < .02) return;
    const x = cel.moonX * W, y = cel.moonY * H, r = 22;
    c.globalCompositeOperation = 'screen'; softBlob(c, x, y, 150, [170, 196, 244], .26 * a); c.globalCompositeOperation = 'source-over';
    c.save(); c.beginPath(); c.arc(x, y, r, 0, TAU); c.clip();
    const g = c.createRadialGradient(x - 7, y - 7, 2, x, y, r);
    g.addColorStop(0, rgb([252, 252, 244], a)); g.addColorStop(1, rgb([206, 212, 214], a));
    c.fillStyle = g; c.fillRect(x - r, y - r, r * 2, r * 2);
    c.fillStyle = `rgba(120,128,142,${(.17 * a).toFixed(3)})`;
    for (const [dx, dy, rr] of [[-6, -4, 7], [5, 3, 9], [-2, 9, 5], [8, -8, 4]]) { c.beginPath(); c.arc(x + dx, y + dy, rr, 0, TAU); c.fill(); }
    c.restore();
  }

  /* --- нарисованное небо ---
     Порядок: небо (плавная смена по времени) → погодная вуаль → облака → обесцвечивание → затемнение.
     Кадр телефона берёт около трети ширины картины, остальное нужно для сдвига. */
  paint_(c, o, s, th) {
    const A = SkyArt, W = this.W, H = this.H, w = s.w, an = this.an, wt = this.sw, src = th ? A.th : A.img;
    const layers = [];
    for (const n of SKY_TIMES) if (wt[n] > .004) layers.push(n);
    // 1) небо без облаков
    let acc = 0;
    for (const n of layers) {
      acc += wt[n];
      const im = src[n][0], dw = im.width * H / im.height, dx = -this.pan * (dw - W);
      c.globalAlpha = wt[n] / acc; c.drawImage(im, dx, 0, dw, H);
    }
    c.globalAlpha = 1;
    // 2) пасмурно: небо уходит в ровный серый, окрашенный по времени суток
    const gv = clamp(w.gray * .8 + w.fog * .3, 0, .85);
    if (gv > .01) {
      const g = c.createLinearGradient(0, 0, 0, H);
      g.addColorStop(0, rgb(an.top, gv)); g.addColorStop(.55, rgb(an.mid, gv)); g.addColorStop(1, rgb(an.hor, gv));
      c.fillStyle = g; c.fillRect(0, 0, W, H);
    }
    // 3) облака: слой чуть крупнее и плывёт относительно неба
    const ac = .12 + .88 * smooth(.1, .95, w.cloud), ap = .1;
    const uc = ap + (1 - 2 * ap) * this.pan + ap * Math.sin(this.dph + this.seed * .7);
    const draw = (g, k) => {
      const im = src[k][1], sc = 1.12 * H / im.height, dw = im.width * sc, dh = im.height * sc;
      return [im, -uc * (dw - W), H - dh, dw, dh];
    };
    if (layers.length === 1) {
      const [im, x, y, dw, dh] = draw(c, layers[0]);
      c.globalAlpha = ac; c.drawImage(im, x, y, dw, dh); c.globalAlpha = 1;
    } else {
      o.clearRect(0, 0, W, H); acc = 0;
      for (const n of layers) {
        acc += wt[n];
        const [im, x, y, dw, dh] = draw(o, n);
        o.globalAlpha = wt[n] / acc; o.drawImage(im, x, y, dw, dh);
      }
      o.globalAlpha = 1;
      c.globalAlpha = ac; c.drawImage(th ? this.s2 : this.off, 0, 0, W, H); c.globalAlpha = 1;
    }
    // 4) серость и тьма непогоды
    const gs = clamp(w.gray * 1.05);
    if (gs > .01) { c.globalCompositeOperation = 'saturation'; c.fillStyle = rgb([128, 128, 128], gs); c.fillRect(0, 0, W, H); c.globalCompositeOperation = 'source-over'; }
    if (w.dark > .01) { c.fillStyle = rgb([10, 16, 32], Math.min(.9, w.dark * 1.1)); c.fillRect(0, 0, W, H); }
    // у нарисованных облаков ночью, на рассвете и закате свет яркий; при сплошной облачности это слишком ярко для текста
    const tw = wt.night + .7 * wt.dusk + .55 * wt.dawn + .45 * wt.sunset + .1 * wt.morning, nd = tw * w.cloud * (.2 + .4 * w.gray);
    if (nd > .01) { c.fillStyle = rgb([8, 12, 26], nd); c.fillRect(0, 0, W, H); }
  }

  /* Снимает цвета с готовой картины: небо сверху, посередине и у горизонта, и фон под цифрами, оповещениями, карточкой и панелью.
     Для каждого места кроме среднего цвета берутся самые тёмные и самые светлые пятна: по ним выбирается цвет текста и плотность стекла. */
  sample() {
    if (!this.sc) {
      this.sc = document.createElement('canvas'); this.s2 = document.createElement('canvas');
      for (const cv of [this.sc, this.s2]) { cv.width = 41; cv.height = 89; }
      this.sx = this.sc.getContext('2d', { willReadFrequently: true }); this.sx2 = this.s2.getContext('2d');
    }
    const sx = this.sx;
    sx.setTransform(.1, 0, 0, .1, 0, 0); this.sx2.setTransform(.1, 0, 0, .1, 0, 0);
    this.paint_(sx, this.sx2, this.st, true);
    const d = sx.getImageData(0, 0, 41, 89).data;
    // Среднее по месту и самые тёмные и самые светлые пятна размером 20×20 (как кусочки под буквами): по ним выбирается цвет текста
    const reg = (x0, y0, x1, y1) => {
      const px = [], bl = [], mean = a => { const r = [0, 0, 0]; for (const p of a) { r[0] += p[0]; r[1] += p[1]; r[2] += p[2]; } return r.map(v => v / a.length); };
      const at = (x, y) => { const i = (y * 41 + x) * 4; return [d[i], d[i + 1], d[i + 2]]; };
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) px.push(at(x, y));
      for (let y = y0; y + 2 <= y1; y += 2) for (let x = x0; x + 2 <= x1; x += 2) bl.push(mean([at(x, y), at(x + 1, y), at(x, y + 1), at(x + 1, y + 1)]));
      const srt = bl.map(p => [lum(p), p]).sort((a, b) => a[0] - b[0]).map(e => e[1]), n = Math.max(1, Math.round(srt.length * .05));
      return { m: mean(px), D: mean(srt.slice(0, n)), L: mean(srt.slice(-n)) };
    };
    const worse = (a, b) => ({ m: a.m, D: lum(a.D) < lum(b.D) ? a.D : b.D, L: lum(a.L) > lum(b.L) ? a.L : b.L });
    const R = { top: reg(0, 0, 41, 22), mid: reg(0, 22, 41, 54), hor: reg(0, 54, 41, 89), hero: worse(reg(2, 13, 39, 30), reg(2, 30, 39, 34)), alert: reg(2, 35, 39, 46), card: reg(2, 60, 39, 80), dock: reg(6, 81, 35, 86) };
    const bk = {};
    for (const k of ['hero', 'alert', 'card', 'dock']) { const f = k === 'hero' ? .85 : .7; bk[k] = R[k].m; bk[k + 'D'] = mix(R[k].m, R[k].D, f); bk[k + 'L'] = mix(R[k].m, R[k].L, f); }
    let nw = { top: R.top.m, mid: R.mid.m, hor: R.hor.m, bk };
    const o = this.samp;
    if (o && o.wk === this.wk && Math.abs(o.h - this.h) < .15) {            // небольшое сглаживание, чтобы дрейф облаков не дёргал тему
      const f = .4, L = (a, b) => mix(a, b, f), bb = {};
      for (const k in bk) bb[k] = L(o.bk[k], bk[k]);
      nw = { top: L(o.top, nw.top), mid: L(o.mid, nw.mid), hor: L(o.hor, nw.hor), bk: bb };
    }
    nw.wk = this.wk; nw.h = this.h;
    this.samp = nw;
  }

  /* --- облака --- */
  lightFor(cx, cy) {
    const cel = this.cel, W = this.W, H = this.H;
    const moon = cel.e < -6 && cel.moonA > .08;
    const sx = (moon ? cel.moonX : cel.sunX) * W, sy = (moon ? cel.moonY : cel.sunY) * H;
    let dx = sx - cx, dy = sy - cy - H * .06;
    const k = 1 - smooth(-2, 18, cel.e);                  // чем ниже солнце, тем свет идёт сбоку
    dy = dy * (1 - .55 * k) - H * .05 * (1 - k);          // при высоком солнце сверху, у горизонта — сбоку
    const d = Math.hypot(dx, dy) || 1;
    return [dx / d, dy / d];
  }
  clouds_(c, s, t) {
    const W = this.W, H = this.H, v = 10 + WIND[this.wk] * 1.0, o = this.octx, q = this.q;
    const cc = this.cc, fl = cc.flat;
    const mid = mix(cc.lit, cc.sh, .32), litStr = 1 - fl * .5;
    for (const cl of this.clouds) {
      let a = cl.a * smooth(cl.th, cl.th + .14, s.w.cloud);
      if (cl.deck) a *= .7 + .3 * s.w.dark;
      a = Math.min(1, a);
      if (a < .01) continue;
      const w = cl.w, span = W + w * 1.6;
      const X = ((cl.x * span + t * v * cl.sp) % span) - w * .8;
      const Y = cl.y * H;
      const bx = Math.max(0, X - w * .08), by = Math.max(0, Y - w * .8), bw = Math.min(W, X + w * 1.08) - bx, bh = Math.min(H, Y + w * .12) - by;
      if (bw <= 4 || bh <= 4) continue;
      const L = this.lightFor(X + w * .5, Y - w * .25);
      o.clearRect(bx, by, bw, bh);
      o.globalCompositeOperation = 'source-over';
      const soft = fl > .35 || cl.deck;                    // пасмурно: мягкая «вата» без выпуклых шаров
      if (soft) {
        for (let i = 0; i < cl.c.length; i++) {
          const k = cl.c[i]; if (k[2] < .075) continue;
          const rr = k[2] * w * (1 + .012 * Math.sin(t * .45 + i * 1.7 + cl.ph)), fy = k[3];
          const cx = X + k[0] * w, cy = Y + k[1] * w, ry = rr * fy * (cl.deck ? 1 : .9);
          const R = rr * 1.25;
          o.save(); o.translate(cx, cy); o.scale(1, ry / rr);
          const g = o.createRadialGradient(-L[0] * rr * .15, -rr * .28, 0, 0, 0, R);
          g.addColorStop(0, rgb(mix(cc.lit, cc.hi, .25), .94)); g.addColorStop(.5, rgb(mix(cc.lit, cc.sh, .28), .88)); g.addColorStop(.82, rgb(mix(cc.lit, cc.sh, .5), .45)); g.addColorStop(1, rgb(cc.sh, 0));
          o.fillStyle = g; o.beginPath(); o.arc(0, 0, R, 0, TAU); o.fill(); o.restore();
        }
      } else {
        // 1) силуэт одним цветом
        o.fillStyle = rgb(mid); o.beginPath();
        for (let i = 0; i < cl.c.length; i++) {
          const k = cl.c[i], rr = k[2] * w * (1 + .012 * Math.sin(t * .45 + i * 1.7 + cl.ph)), cx = X + k[0] * w, cy = Y + k[1] * w;
          o.moveTo(cx + rr, cy); o.ellipse(cx, cy, rr, rr * k[3], 0, 0, TAU);
        }
        o.fill();
        // 2) каждый шар: светлая сторона к солнцу, тень с обратной стороны (от крупных к мелким)
        for (let i = 0; i < cl.c.length; i++) {
          const k = cl.c[i], rr = k[2] * w * (1 + .012 * Math.sin(t * .45 + i * 1.7 + cl.ph)), fy = k[3];
          const cx = X + k[0] * w, cy = Y + k[1] * w, ry = rr * fy;
          const g = o.createLinearGradient(cx + L[0] * rr, cy + L[1] * ry, cx - L[0] * rr, cy - L[1] * ry);
          g.addColorStop(0, rgb(cc.lit, .96)); g.addColorStop(.46, rgb(cc.lit, .78 * litStr + .1)); g.addColorStop(.78, rgb(mix(cc.lit, cc.sh, .6), .55)); g.addColorStop(1, rgb(cc.sh, .78));
          o.fillStyle = g;
          o.beginPath(); o.ellipse(cx, cy, rr, ry, 0, 0, TAU); o.fill();
          if (rr > w * .035) {                               // блик у кромки, обращённой к солнцу
            const hx_ = cx + L[0] * rr * .5, hy_ = cy + L[1] * ry * .5;
            const rg = o.createRadialGradient(hx_, hy_, 0, hx_, hy_, rr * .6);
            rg.addColorStop(0, rgb(cc.hi, .8 * litStr)); rg.addColorStop(1, rgb(cc.hi, 0));
            o.fillStyle = rg;
            o.beginPath(); o.ellipse(cx, cy, rr, ry, 0, 0, TAU); o.fill();
          }
        }
      }
      // 3) общий свет и тень по всему облаку, чтобы форма читалась целиком
      o.globalCompositeOperation = 'source-atop';
      const gx = X + w * .5, gy = Y - w * .22, gl = w * .55;
      const og = o.createLinearGradient(gx + L[0] * gl, gy + L[1] * gl, gx - L[0] * gl, gy - L[1] * gl);
      og.addColorStop(0, rgb(cc.hi, .3 * litStr)); og.addColorStop(.5, rgb(cc.hi, 0)); og.addColorStop(1, rgb(cc.sh, soft ? .18 : .42));
      o.fillStyle = og; o.fillRect(bx, by, bw, bh);
      if (!cl.deck) {
        const bg = o.createLinearGradient(0, Y - w * .2, 0, Y + w * .02);
        bg.addColorStop(0, rgb(cc.sh, 0)); bg.addColorStop(1, rgb(cc.sh, .6));
        o.fillStyle = bg; o.fillRect(bx, Y - w * .2, bw, w * .22);
      }
      if (!cl.deck) {                                      // плоское основание у кучевых
        o.globalCompositeOperation = 'destination-out';
        const cut = o.createLinearGradient(0, Y - w * .015, 0, Y + w * .05);
        cut.addColorStop(0, 'rgba(0,0,0,0)'); cut.addColorStop(1, 'rgba(0,0,0,1)');
        o.fillStyle = cut; o.fillRect(bx, Y - w * .015, bw, by + bh - (Y - w * .015) + 2);
      }
      o.globalCompositeOperation = 'source-over';
      c.globalAlpha = a;
      c.drawImage(this.off, bx * q, by * q, bw * q, bh * q, bx, by, bw, bh);
      c.globalAlpha = 1;
    }
  }

  /* --- вуаль под цифрами: едва заметно светлит или темнит небо там, где нет стекла --- */
  scrim_(c) {
    const sc = this.scrim; if (!sc || sc.a < .01) return;
    const g = c.createLinearGradient(0, 96, 0, 440);
    g.addColorStop(0, rgb(sc.tint, 0)); g.addColorStop(.2, rgb(sc.tint, sc.a)); g.addColorStop(.72, rgb(sc.tint, sc.a)); g.addColorStop(1, rgb(sc.tint, 0));
    c.fillStyle = g; c.fillRect(0, 96, this.W, 344);
  }

  /* --- земля: дальние гряды в дымке --- */
  ground_(c, s) {
    const W = this.W, H = this.H, hy = H * HZ, w = s.w;
    const { far, near, base } = groundColors(s);
    const ridge = (dy, a1, f1, p1, a2, f2, p2, col, bot) => {
      const g = c.createLinearGradient(0, hy + dy - a1 - a2, 0, H);
      g.addColorStop(0, rgb(col)); g.addColorStop(.35, rgb(mix(col, bot, .55))); g.addColorStop(1, rgb(bot));
      c.fillStyle = g; c.beginPath(); c.moveTo(0, H);
      for (let x = 0; x <= W + 6; x += 6) c.lineTo(x, hy + dy - a1 * (.5 + .5 * Math.sin(x * f1 + p1)) - a2 * (.5 + .5 * Math.sin(x * f2 + p2)));
      c.lineTo(W, H); c.closePath(); c.fill();
    };
    ridge(-4, 20, .0052, 1.1, 8, .017, 2.4, far, mix(far, near, .5));
    ridge(26, 13, .0075, 4.0, 6, .026, .6, mix(near, far, .25), base);
    // тёплая кромка на дальней гряде в сторону солнца (рассвет, закат)
    const rim = (1 - smooth(-1, 12, s.e)) * smooth(-9, -1, s.e) * (1 - w.gray) * (1 - w.fog);
    if (rim > .02) {
      const gx = this.cel.sunX * W, rg = c.createRadialGradient(gx, hy, 0, gx, hy, W * .8);
      rg.addColorStop(0, `rgba(255,170,110,${(.34 * rim).toFixed(3)})`); rg.addColorStop(1, 'rgba(255,170,110,0)');
      c.fillStyle = rg; c.fillRect(0, hy - 40, W, 90);
    }
  }

  /* --- туман --- */
  ensureFog() {
    const col = this.st.fogC, sig = col.map(v => Math.round(v / 6)).join();
    if (sig === this.fogSig && this.fogTile) return;
    this.fogSig = sig;
    const cv = document.createElement('canvas'); cv.width = 1024; cv.height = 260;
    const g = cv.getContext('2d'), r = mulberry32(777);
    for (let i = 0; i < 44; i++) {
      const x = r() * 1024, y = 40 + r() * 180, rr = 40 + r() * 90, sx = 2 + r() * 1.6, al = .26 + r() * .26;
      for (const dx of [-1024, 0, 1024]) { g.save(); g.translate(x + dx, y); g.scale(sx, 1); softBlob(g, 0, 0, rr, col, al); g.restore(); }
    }
    g.globalCompositeOperation = 'destination-in';                 // края полосы растворяются, без резкой линии
    const fm = g.createLinearGradient(0, 0, 0, 260);
    fm.addColorStop(0, 'rgba(0,0,0,0)'); fm.addColorStop(.3, 'rgba(0,0,0,1)'); fm.addColorStop(.7, 'rgba(0,0,0,1)'); fm.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = fm; g.fillRect(0, 0, 1024, 260);
    this.fogTile = cv;
  }
  fog_(c, s, t) {
    const w = s.w, W = this.W, H = this.H, hy = H * (SkyArt.ready ? .8 : HZ);
    if (w.fog <= 0 || !this.fogTile) return;
    const g = c.createLinearGradient(0, hy - H * .4, 0, H);
    g.addColorStop(0, rgb(s.fogC, 0)); g.addColorStop(.5, rgb(s.fogC, .5 * w.fog)); g.addColorStop(1, rgb(s.fogC, .8 * w.fog));
    c.fillStyle = g; c.fillRect(0, 0, W, H);
    const L = [[hy - H * .22, 1, 5, .62], [hy - H * .06, 1.5, 11, .78], [hy + H * .1, 2.3, 20, .86]];
    for (const [y, sc, sp, al] of L) {
      const tw = 1024 * sc, off = (t * sp) % tw;
      c.globalAlpha = Math.min(1, al * w.fog);
      for (let x = -off; x < W; x += tw) c.drawImage(this.fogTile, x, y - 130 * sc, tw, 260 * sc);
    }
    c.globalAlpha = 1;
  }

  /* --- дождь и снег --- */
  precip_(c, s) {
    const W = this.W, H = this.H, hy = H * HZ, w = s.w;
    if (this.rain.length) {
      const sl = clamp(WIND[this.wk] / 90, 0, .55);
      const col = mix([170, 186, 214], [232, 238, 246], s.day);
      // капли трёх классов: далёкие короткие бледные, ближние длинные яркие
      const cls = [[0, .36, .8, .2], [.36, .75, 1.15, .34], [.75, 1.01, 1.7, .55]];
      c.lineCap = 'round';
      for (const [z0, z1, lw, al] of cls) {
        c.lineWidth = lw; c.strokeStyle = rgb(col, al * (.6 + .4 * w.rain)); c.beginPath();
        for (const p of this.rain) {
          if (p.z < z0 || p.z >= z1) continue;
          c.moveTo(p.x, p.y); c.lineTo(p.x - sl * p.l, p.y - p.l);
        }
        c.stroke();
      }
      // брызги на земле
      c.lineWidth = 1;
      for (const sp of this.splash) {
        const u = sp.t / .42, rx = 1.5 + u * sp.m;
        c.strokeStyle = rgb(col, (1 - u) * .5);
        c.beginPath(); c.ellipse(sp.x, sp.y, rx, rx * .34, 0, 0, TAU); c.stroke();
      }
      // водяная пыль над землёй
      const mg = c.createLinearGradient(0, H * .66, 0, H);
      mg.addColorStop(0, rgb(col, 0)); mg.addColorStop(1, rgb(col, .16 * w.rain));
      c.fillStyle = mg; c.fillRect(0, H * .66, W, H * .34);
    }
    if (this.snow.length) {
      const col = mix([204, 214, 232], [255, 255, 255], s.day);
      for (const p of this.snow) {
        const a = .4 + p.z * .5;
        if (p.s > 2.6) { softBlob(c, p.x, p.y, p.s * 1.7, col, a * .8); continue; }  // близкие хлопья вне фокуса
        c.fillStyle = rgb(col, a);
        c.beginPath(); c.arc(p.x, p.y, p.s, 0, TAU); c.fill();
      }
      // снежная дымка у земли
      const mg = c.createLinearGradient(0, H * .58, 0, H);
      mg.addColorStop(0, rgb(col, 0)); mg.addColorStop(1, rgb(col, .2 * w.snow));
      c.fillStyle = mg; c.fillRect(0, H * .58, W, H * .42);
    }
  }

  /* --- молния: вспышки не чаще одной серии в 6 секунд, без стробоскопа --- */
  lightning_(c, s) {
    const W = this.W, H = this.H, w = s.w;
    if (!w.storm) return;
    const live = this.motion && this.flash > .02 && this.bolt;
    const stat = this.staticBolt && !this.motion;
    if (!live && !stat) return;
    if (!this.bolt) this.bolt = this.mkBolt();
    const f = live ? this.flash : .6, bx = this.bolt[0][0];
    c.globalCompositeOperation = 'screen';
    const rg = c.createRadialGradient(bx, H * .08, 0, bx, H * .08, W * .95);
    rg.addColorStop(0, `rgba(176,196,255,${(.42 * f * f).toFixed(3)})`); rg.addColorStop(1, 'rgba(176,196,255,0)');
    c.fillStyle = rg; c.fillRect(0, 0, W, H * .7);
    c.fillStyle = `rgba(200,214,255,${(.1 * f * f).toFixed(3)})`; c.fillRect(0, 0, W, H);
    c.globalCompositeOperation = 'source-over';
    c.lineJoin = 'round'; c.lineCap = 'round';
    for (const [lw, al] of [[7, .14], [3.4, .3]]) {
      c.strokeStyle = `rgba(190,208,255,${(al * f).toFixed(3)})`;
      c.beginPath(); for (const g of this.bolt) { c.lineWidth = lw * g[4] / 2.2; c.moveTo(g[0], g[1]); c.lineTo(g[2], g[3]); } c.stroke();
    }
    c.strokeStyle = `rgba(246,248,255,${Math.min(1, .95 * f + .05).toFixed(3)})`;
    for (const g of this.bolt) { c.lineWidth = Math.max(.8, g[4] * .85); c.beginPath(); c.moveTo(g[0], g[1]); c.lineTo(g[2], g[3]); c.stroke(); }
  }

  /* --- капли на «стекле» экрана --- */
  glassDrops_(c, s) {
    if (!this.drops.length) return;
    const w = s.w.rain, day = s.day;
    for (const d of this.drops) {
      if (d.trail.length > 1) {
        c.strokeStyle = `rgba(255,255,255,${(.14 * w).toFixed(3)})`; c.lineWidth = d.r * .55; c.lineCap = 'round';
        c.beginPath(); c.moveTo(d.trail[0][0], d.trail[0][1]); for (const p of d.trail) c.lineTo(p[0], p[1]); c.stroke();
      }
      const rx = d.r, ry = d.r * 1.22;
      c.fillStyle = `rgba(255,255,255,${(.09 * w).toFixed(3)})`;
      c.beginPath(); c.ellipse(d.x, d.y, rx, ry, 0, 0, TAU); c.fill();
      c.strokeStyle = `rgba(255,255,255,${((.2 + .12 * day) * w).toFixed(3)})`; c.lineWidth = .8;
      c.beginPath(); c.ellipse(d.x, d.y, rx, ry, 0, 0, TAU); c.stroke();
      c.fillStyle = `rgba(255,255,255,${(.55 * w).toFixed(3)})`;
      c.beginPath(); c.arc(d.x - rx * .32, d.y - ry * .4, Math.max(.7, rx * .24), 0, TAU); c.fill();
      c.fillStyle = `rgba(10,16,30,${(.14 * w).toFixed(3)})`;
      c.beginPath(); c.ellipse(d.x + rx * .2, d.y + ry * .45, rx * .5, ry * .28, 0, 0, TAU); c.fill();
    }
  }
}
