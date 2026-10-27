/* ---------- Погода на карточках ----------
   Дождь: верхняя и левая кромки мокрые, капли набухают и стекают.
   Снег: на верхней кромке нарастает сугроб, пока идёт снег.
   Туман: стекло запотевает, пальцем или мышью туман стирается и со временем возвращается.
   Эффекты считаются по прямоугольникам настоящих карточек на экране, поэтому работают на любой странице. */
const FX_SEL = '.hourly.glass,.alert,.fcard.glass,.pcard.glass,.tile.glass,.dock.glass,#pgHome .pill.glass,#pgHome .rbtn.glass';
const approach = (v, to, up, down) => (to > v ? Math.min(to, v + up) : Math.max(to, v - down));

class CardFx {
  constructor(cv, screen) {
    this.cv = cv; this.ctx = cv.getContext('2d'); this.screen = screen;
    this.W = SCENE_W; this.H = SCENE_H; this.q = 1; this.sc = 1;
    this.on = true; this.w = null; this.s = null; this.key = '';
    this.wet = 0; this.snow = 0; this.fog = 0; this.rain = 0; this.t = 0; this.regT = 0;
    this.cards = []; this.map = new WeakMap(); this.tile = null; this.brush = null;
    this.tilt = { x: 0, y: 0, tx: 0, ty: 0, idle: 0, sensor: false };
    try { window.addEventListener('deviceorientation', e => { if (e.gamma == null || e.beta == null) return; this.tilt.sensor = true; this.tilt.tx = clamp(e.gamma / 25, -1, 1); this.tilt.ty = clamp((e.beta - 55) / 25, -1, 1); }); } catch (_) {}
    screen.addEventListener('pointermove', e => {                       // без датчика (компьютер) наклоном служит положение мыши
      if (this.tilt.sensor) return;
      const sr = screen.getBoundingClientRect();
      this.tilt.tx = clamp(((e.clientX - sr.left) / sr.width - .5) * 2.4, -1, 1); this.tilt.ty = clamp(((e.clientY - sr.top) / sr.height - .5) * 2.4, -1, 1); this.tilt.idle = 0;
    });
    screen.addEventListener('pointerdown', e => this.wipe(e));
    screen.addEventListener('pointermove', e => { if (e.buttons & 1) this.wipe(e); });
    for (const t of ['pointerup', 'pointercancel', 'pointerleave']) screen.addEventListener(t, () => this.lift());
  }
  setSize(q) {
    this.q = Math.max(1, q);
    this.cv.width = Math.round(this.W * this.q); this.cv.height = Math.round(this.H * this.q);
    this.ctx.setTransform(this.q, 0, 0, this.q, 0, 0);
  }
  sync(s) {
    this.s = s; this.w = s.w;
    const f = s.fogC, br = clamp((f[0] + f[1] + f[2]) / 765 * 1.1, .15, 1);   // ночью туман серо-синий, днём светлый
    this.screen.style.setProperty('--fx-br', br.toFixed(2));
    this.screen.style.setProperty('--fogb', (this.fog * 7).toFixed(1) + 'px');
  }

  /* --- какие карточки сейчас на экране --- */
  refresh() {
    const sr = this.screen.getBoundingClientRect(); this.sc = sr.width / this.W || 1;
    const out = [];
    for (const el of this.screen.querySelectorAll(FX_SEL)) {
      const r = el.getBoundingClientRect();
      if (r.width < 8 || r.height < 8) continue;                       // карточки скрытых страниц
      out.push({ el, x: (r.left - sr.left) / this.sc, y: (r.top - sr.top) / this.sc, w: r.width / this.sc, h: r.height / this.sc, room: 0 });
    }
    for (const c of out) {                                             // сколько места над карточкой для сугроба
      let room = c.y - 44;
      for (const o of out) {
        if (o === c) continue;
        const ob = o.y + o.h;
        if (ob <= c.y + 2 && Math.min(o.x + o.w, c.x + c.w) - Math.max(o.x, c.x) > 20) room = Math.min(room, c.y - ob);
      }
      c.room = Math.max(0, room);
    }
    this.cards = out;
  }
  st(c) {
    let s = this.map.get(c.el);
    if (!s) {
      const r = mulberry32((Math.random() * 1e9) | 0);
      s = { rnd: r, drops: [], spawn: 0, R: 16, size: '', ph: [r() * 6, r() * 6, r() * 6, r() * 6], beads: [], mist: [], grain: [], fac: [], fog: null };
      this.map.set(c.el, s);
    }
    const sz = Math.round(c.w) + 'x' + Math.round(c.h);
    if (s.size !== sz) {
      s.size = sz;
      s.R = Math.min(parseFloat(getComputedStyle(c.el).borderTopLeftRadius) || 16, c.w / 2, c.h / 2);
      s.beads = Array.from({ length: Math.min(14, Math.max(2, Math.round(c.w / 26))) }, () => ({ u: s.rnd(), r: .8 + s.rnd() * 1.2 }));
      const nT = Math.min(110, Math.round(c.w * Math.min(c.h * .5, 60) / 420)), nL = Math.min(34, Math.round(c.h / 9));
      s.grain = Array.from({ length: Math.min(90, Math.max(8, Math.round(c.w / 3.2))) }, () => ({ u: .02 + s.rnd() * .96, v: s.rnd(), r: .3 + s.rnd() * .45, l: s.rnd() < .55 }));    // мелкое зерно в теле сугроба
      s.fac = Array.from({ length: Math.min(40, Math.max(6, Math.round(c.w / 9))) }, () => ({ u: .04 + s.rnd() * .92, v: .05 + s.rnd() * .5, nx: s.rnd() * 2 - 1, ny: s.rnd() * 2 - 1, sz: 1.1 + s.rnd() * 1.9 }));   // грани льдинок: каждая вспыхивает при своём наклоне
      s.mist = [];                                                      // мелкие неподвижные капли: густо у верхней кромки, реже книзу, и вдоль левой
      for (let i = 0; i < nT; i++) s.mist.push({ x: s.R * .4 + s.rnd() * (c.w - s.R * .8), y: 2 + Math.pow(s.rnd(), 2) * Math.min(c.h * .5, 60), r: .55 + s.rnd() * s.rnd() * 1.5, k: .5 + s.rnd() * .5 });
      for (let i = 0; i < nL; i++) s.mist.push({ x: 2 + Math.pow(s.rnd(), 2.4) * 22, y: s.R * .5 + s.rnd() * (c.h - s.R), r: .55 + s.rnd() * s.rnd() * 1.4, k: .5 + s.rnd() * .5 });
    }
    return s;
  }

  /* --- ход времени --- */
  frame(dt) { this.step(dt); this.draw(); }
  warm() { for (let i = 0; i < 100; i++) this.step(.2); this.draw(); }      // для замершего кадра: сразу показать уже «намокшее» и «заметённое»
  step(dt) {
    if (!this.w) return;
    this.t += dt;
    const tl = this.tilt; tl.idle += dt;
    if (!tl.sensor && tl.idle > 2.5) { tl.tx = Math.sin(this.t * .42) * .85; tl.ty = Math.cos(this.t * .31) * .5; }     // никто не двигает: слегка покачиваем сами
    tl.x += (tl.tx - tl.x) * Math.min(1, dt * 5); tl.y += (tl.ty - tl.y) * Math.min(1, dt * 5);
    const w = this.w, o = this.on;
    this.rain = o ? w.rain : 0;
    this.wet = approach(this.wet, this.rain > .05 ? 1 : 0, dt / 3, dt / 10);
    this.snow = approach(this.snow, o && w.snow > .05 ? clamp(w.snow / .7, .2, 1.3) : 0, dt / 12, dt / 6);
    this.fog = approach(this.fog, o && w.fog > .25 ? w.fog : 0, dt / 2.5, dt / 3);
    this.screen.classList.toggle('fx-fog', this.fog > .1);
    this.refresh();
    this.regT += dt;
    const regen = this.regT > .5; if (regen) this.regT = 0;
    for (const c of this.cards) {
      const st = this.st(c);
      if (this.rain > .05 || st.drops.length) this.simDrops(c, st, dt);
      if (this.fog > .02) {
        if (!st.fog || st.fog.sz !== st.size) this.mkFog(c, st);
        st.fog.cv.style.opacity = clamp(this.fog / .9).toFixed(2);
        if (regen && st.fog.wiped) { st.fog.mctx.globalCompositeOperation = 'destination-out'; st.fog.mctx.fillStyle = 'rgba(0,0,0,.035)'; st.fog.mctx.fillRect(0, 0, st.fog.mask.width, st.fog.mask.height); st.fog.dirty = true; }
        if (st.fog.dirty) this.renderFog(st.fog);
      } else if (st.fog) { st.fog.cv.remove(); st.fog = null; }
    }
  }

  /* --- дождь --- */
  simDrops(c, st, dt) {
    const r = this.rain, R = st.R, rnd = st.rnd;
    if (r > .05) {
      st.spawn -= dt;
      if (st.spawn <= 0 && st.drops.length < 16) {
        const top = rnd() < .6, k = clamp((c.w + c.h) / 520, .35, 1.3);
        st.drops.push(top
          ? { e: 't', x: R * .5 + rnd() * (c.w - R), y: 1.6, r: .8, rmax: 1.8 + rnd() * 2.0, grow: 1.3 + rnd() * 2.2, ph: 0, vy: 0, mt: 0, pt: 0, trav: 24 + rnd() * Math.min(80, c.h * .55), tr: [], fade: 1, dead: false }
          : { e: 'l', x: 2.5 + rnd() * 1.1, y: R * .4 + rnd() * c.h * .3, r: .8, rmax: 1.6 + rnd() * 1.3, grow: .8 + rnd() * 1.6, ph: 0, vy: 0, mt: 0, pt: 0, trav: c.h - R * .4, tr: [], fade: 1, dead: false });
        st.spawn = (.4 + rnd() * .9) / ((.35 + r * 1.2) * k);
      }
    }
    for (const d of st.drops) {
      for (const p of d.tr) p.a += dt;
      while (d.tr.length && d.tr[0].a > 3.2) d.tr.shift();
      if (d.dead) { d.fade -= dt * 1.2; continue; }
      if (d.ph === 0) {                                                  // капля набухает у кромки
        d.r = Math.min(d.rmax, d.r + (d.rmax - .8) / d.grow * dt);
        if (d.r >= d.rmax - .01) { d.ph = 1; d.vy = 12; d.mt = .5 + rnd() * 1.1; }
      } else if (d.pt > 0) {                                             // остановилась, набирает воду
        d.pt -= dt; d.r = Math.min(d.rmax, d.r + dt * .25);
        if (d.pt <= 0) { d.mt = .4 + rnd() * 1.0; d.vy = 12; }
      } else {                                                           // ползёт вниз, оставляя след
        d.mt -= dt; d.vy = Math.min(d.vy + 95 * dt, 38 + d.rmax * 26); d.y += d.vy * dt; d.r = Math.max(.75, d.r - dt * .22);
        if (d.e === 't') d.x += (rnd() - .5) * .5 * dt * 10;
        if (!d.tr.length || d.y - d.tr[d.tr.length - 1].y >= 4) d.tr.push({ x: d.x, y: d.y, a: 0 });
        if (d.mt <= 0) { d.pt = .3 + rnd() * 1.1; d.vy = 0; }
      }
      if (d.y > d.trav) d.dead = true;
    }
    st.drops = st.drops.filter(d => !(d.dead && d.fade <= 0 && !d.tr.length));
  }

  /* --- отрисовка поверх карточек (капли, сугробы) --- */
  draw() {
    const c = this.ctx;
    c.clearRect(0, 0, this.W, this.H);
    if (!this.s) return;
    for (const k of this.cards) {
      const st = this.map.get(k.el); if (!st) continue;
      if (this.wet > .02 || st.drops.length) this.drawWet(c, k, st);
      if (this.snow > .02) this.drawDrift(c, k, st);
    }
  }
  rr(c, x, y, w, h, R) {
    c.beginPath(); c.moveTo(x + R, y); c.arcTo(x + w, y, x + w, y + h, R); c.arcTo(x + w, y + h, x, y + h, R); c.arcTo(x, y + h, x, y, R); c.arcTo(x, y, x + w, y, R); c.closePath();
  }
  drawWet(c, k, st) {
    const a = this.wet, R = st.R, { x, y, w, h } = k;
    if (a > .02) {
      c.save(); this.rr(c, x, y, w, h, R); c.clip();
      let g = c.createLinearGradient(0, y, 0, y + Math.min(h * .5, 46));   // верх стекла намок: тёмный мокрый слой, внутри светлая кромка
      g.addColorStop(0, `rgba(18,30,52,${(.14 * a).toFixed(3)})`); g.addColorStop(1, 'rgba(18,30,52,0)');
      c.fillStyle = g; c.fillRect(x, y, w, Math.min(h * .5, 46));
      g = c.createLinearGradient(0, y, 0, y + 22);
      g.addColorStop(0, `rgba(255,255,255,${(.5 * a).toFixed(3)})`); g.addColorStop(1, 'rgba(255,255,255,0)');
      c.fillStyle = g; c.fillRect(x, y, w, 22);
      g = c.createLinearGradient(x, 0, x + 16, 0);                        // и у левой
      g.addColorStop(0, `rgba(255,255,255,${(.4 * a).toFixed(3)})`); g.addColorStop(1, 'rgba(255,255,255,0)');
      c.fillStyle = g; c.fillRect(x, y, 16, h);
      c.restore();
      c.lineWidth = 1.2; c.lineCap = 'round';
      c.strokeStyle = `rgba(255,255,255,${(.8 * a).toFixed(3)})`;
      c.beginPath(); c.moveTo(x + R * .75, y + .8); c.lineTo(x + w - R * .75, y + .8); c.stroke();
      g = c.createLinearGradient(0, y + R * .6, 0, y + h * .85);
      g.addColorStop(0, `rgba(255,255,255,${(.8 * a).toFixed(3)})`); g.addColorStop(1, 'rgba(255,255,255,0)');
      c.strokeStyle = g; c.beginPath(); c.moveTo(x + .8, y + R * .6); c.lineTo(x + .8, y + h * .85); c.stroke();
      this.mistDraw(c, x, y, st, a);
      for (const b of st.beads) this.bead(c, x + R * .6 + b.u * (w - R * 1.2), y + 2.2, b.r, a);   // бусинки воды на кромке
    }
    for (const d of st.drops) {
      const al = d.dead ? Math.max(0, d.fade) : 1;
      if (d.tr.length > 1) {
        c.lineCap = 'round'; c.lineWidth = Math.max(.8, d.r * .7);
        for (let i = 1; i < d.tr.length; i++) {
          const p = d.tr[i - 1], q = d.tr[i];
          c.strokeStyle = `rgba(255,255,255,${(.4 * (1 - q.a / 3.2) * (.4 + .6 * i / d.tr.length)).toFixed(3)})`;
          c.beginPath(); c.moveTo(x + p.x, y + p.y); c.lineTo(x + q.x, y + q.y); c.stroke();
        }
      }
      if (al > .02) this.bead(c, x + d.x, y + d.y, d.r, al);
    }
  }
  mistDraw(c, x, y, st, a) {                                              // всё одним проходом: кольца, заливка, блики
    const m = st.mist; if (!m.length) return;
    c.beginPath();
    for (const d of m) { const r = d.r * (.6 + .4 * a); c.moveTo(x + d.x + r, y + d.y); c.ellipse(x + d.x, y + d.y, r, r * 1.15, 0, 0, TAU); }
    c.fillStyle = `rgba(255,255,255,${(.14 * a).toFixed(3)})`; c.fill();
    c.strokeStyle = `rgba(255,255,255,${(.5 * a).toFixed(3)})`; c.lineWidth = .7; c.stroke();
    c.beginPath();
    for (const d of m) if (d.r > .85) { c.moveTo(x + d.x - d.r * .3 + .5, y + d.y - d.r * .4); c.arc(x + d.x - d.r * .3, y + d.y - d.r * .4, Math.max(.4, d.r * .25), 0, TAU); }
    c.fillStyle = `rgba(255,255,255,${(.85 * a).toFixed(3)})`; c.fill();
  }
  bead(c, X, Y, r, a) {
    const ry = r * 1.22;
    c.fillStyle = `rgba(255,255,255,${(.18 * a).toFixed(3)})`; c.beginPath(); c.ellipse(X, Y, r, ry, 0, 0, TAU); c.fill();
    c.strokeStyle = `rgba(255,255,255,${(.62 * a).toFixed(3)})`; c.lineWidth = .8; c.beginPath(); c.ellipse(X, Y, r, ry, 0, 0, TAU); c.stroke();
    c.fillStyle = `rgba(10,16,30,${(.28 * a).toFixed(3)})`; c.beginPath(); c.ellipse(X + r * .2, Y + ry * .45, r * .5, ry * .28, 0, 0, TAU); c.fill();
    c.fillStyle = `rgba(255,255,255,${(.8 * a).toFixed(3)})`; c.beginPath(); c.arc(X - r * .32, Y - ry * .4, Math.max(.5, r * .26), 0, TAU); c.fill();
  }

  /* --- снег: сугроб на верхней кромке, растёт вместе с снегопадом --- */
  drawDrift(c, k, st) {
    const a = this.snow, s = this.s, R = st.R, ph = st.ph;
    let hm = (1.5 + 8 * a) * (k.h < 60 ? .75 : 1);
    hm = Math.min(hm, k.room - 1.5);
    if (hm < 1) return;
    const x0 = k.x + R * .5, x1 = k.x + k.w - R * .5, y0 = k.y, n = Math.max(6, Math.round((x1 - x0) / 4));
    const A = mix(mix([172, 188, 218], [255, 255, 255], s.day), s.hor, .16), B = mix(A, [132, 156, 200], .5);
    const pts = [];
    for (let i = 0; i <= n; i++) {
      const u = i / n, env = Math.pow(Math.sin(Math.PI * u), .55);
      const nz = .6 + .2 * Math.sin(u * 19 + ph[0]) + .12 * Math.sin(u * 43 + ph[1]) + .1 * Math.sin(u * 7 + ph[2]) + .07 * Math.sin(u * 97 + ph[3]);
      pts.push([x0 + (x1 - x0) * u, y0 + 1 - hm * env * Math.max(.15, nz)]);
    }
    const top = new Path2D();
    top.moveTo(pts[0][0], pts[0][1]);
    for (let i = 1; i < pts.length - 1; i++) top.quadraticCurveTo(pts[i][0], pts[i][1], (pts[i][0] + pts[i + 1][0]) / 2, (pts[i][1] + pts[i + 1][1]) / 2);
    top.lineTo(pts[pts.length - 1][0], pts[pts.length - 1][1]);
    const body = new Path2D(); body.addPath(top);
    body.lineTo(x1, y0 + 3.2);
    for (let i = n; i >= 0; i--) body.lineTo(x0 + (x1 - x0) * i / n, y0 + 2.6 + .9 * Math.sin(i * .9 + ph[0]));
    body.closePath();
    c.save();
    c.translate(0, 1.8); c.fillStyle = 'rgba(20,34,56,.2)'; c.fill(body); c.translate(0, -1.8);     // тень на стекле под сугробом
    const g = c.createLinearGradient(0, y0 - hm, 0, y0 + 3.2);
    g.addColorStop(0, rgb(A, .98)); g.addColorStop(.6, rgb(mix(A, B, .35), .98)); g.addColorStop(1, rgb(B, .98));
    c.fillStyle = g; c.fill(body);
    c.save(); c.clip(body);                                                                              // зерно снега
    const ptAt = u => { const f = u * n, j = Math.min(n - 1, Math.floor(f)), t = f - j; return { x: pts[j][0] + (pts[j + 1][0] - pts[j][0]) * t, y: pts[j][1] + (pts[j + 1][1] - pts[j][1]) * t }; };
    c.beginPath();
    for (const q of st.grain) { if (!q.l) continue; const t = ptAt(q.u), yy = t.y + q.v * Math.max(1, y0 + 2.6 - t.y); c.moveTo(t.x + q.r, yy); c.arc(t.x, yy, q.r, 0, TAU); }
    c.fillStyle = 'rgba(255,255,255,.55)'; c.fill();
    c.beginPath();
    for (const q of st.grain) { if (q.l) continue; const t = ptAt(q.u), yy = t.y + q.v * Math.max(1, y0 + 2.6 - t.y); c.moveTo(t.x + q.r, yy); c.arc(t.x, yy, q.r, 0, TAU); }
    c.fillStyle = rgb(B, .5); c.fill();
    c.restore();
    c.strokeStyle = 'rgba(255,255,255,.7)'; c.lineWidth = 1.1; c.lineJoin = 'round'; c.stroke(top);     // свет по верхней кромке
    const tl = this.tilt;
    for (const f of st.fac) {                                                                            // искры: вспыхивают, когда наклон совпал с гранью льдинки
      const d = Math.hypot(f.nx - tl.x, f.ny - tl.y), gl = Math.max(0, 1 - d / .52);
      if (gl < .04) continue;
      const t = ptAt(f.u), X = t.x, Y = t.y + f.v * Math.max(1, hm * .8), I = gl * gl * (3 - 2 * gl), L = f.sz * (.7 + 1.6 * I);
      c.strokeStyle = `rgba(255,255,255,${(.85 * I).toFixed(3)})`; c.lineWidth = .7; c.lineCap = 'round';
      c.beginPath(); c.moveTo(X - L, Y); c.lineTo(X + L, Y); c.moveTo(X, Y - L); c.lineTo(X, Y + L); c.stroke();
      c.fillStyle = `rgba(235,246,255,${(.95 * I).toFixed(3)})`; c.beginPath(); c.arc(X, Y, .5 + .7 * I, 0, TAU); c.fill();
    }
    c.restore();
  }

  /* --- туман: слой внутри карточки под текстом, стирается пальцем --- */
  mkTile() {
    const S = 576, cv = document.createElement('canvas'); cv.width = cv.height = S;
    const g = cv.getContext('2d'), r = mulberry32(4242);
    g.fillStyle = 'rgba(255,255,255,.5)'; g.fillRect(0, 0, S, S);
    for (let i = 0; i < 46; i++) {                                    // неровность: где гуще, где реже
      const x = r() * S, y = r() * S, rad = 40 + r() * 110, dark = i % 2 === 0;
      for (const dx of [-S, 0, S]) for (const dy of [-S, 0, S]) {
        const gr = g.createRadialGradient(x + dx, y + dy, 0, x + dx, y + dy, rad);
        gr.addColorStop(0, dark ? 'rgba(0,0,0,.16)' : 'rgba(255,255,255,.12)'); gr.addColorStop(1, dark ? 'rgba(0,0,0,0)' : 'rgba(255,255,255,0)');
        g.globalCompositeOperation = dark ? 'destination-out' : 'source-over'; g.fillStyle = gr; g.fillRect(x + dx - rad, y + dy - rad, rad * 2, rad * 2);
      }
    }
    g.globalCompositeOperation = 'source-over';
    for (let i = 0; i < 2400; i++) {                                  // мелкие капли конденсата
      const x = r() * S, y = r() * S, rad = .5 + r() * r() * 2.2;
      g.fillStyle = `rgba(255,255,255,${(.5 + r() * .5).toFixed(2)})`; g.beginPath(); g.arc(x, y, rad, 0, TAU); g.fill();
      if (rad > 1.2) { g.fillStyle = 'rgba(255,255,255,.2)'; g.beginPath(); g.arc(x - rad * .3, y - rad * .3, rad * .45, 0, TAU); g.fill(); }
    }
    this.tile = cv;
    const b = document.createElement('canvas'); b.width = b.height = 36;
    const bg = b.getContext('2d'), gr = bg.createRadialGradient(18, 18, 0, 18, 18, 18);
    gr.addColorStop(0, 'rgba(0,0,0,1)'); gr.addColorStop(.55, 'rgba(0,0,0,.8)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
    bg.fillStyle = gr; bg.fillRect(0, 0, 36, 36); this.brush = b;
  }
  mkFog(c, st) {
    if (st.fog) st.fog.cv.remove();
    if (!this.tile) this.mkTile();
    const cv = document.createElement('canvas'); cv.width = Math.round(c.w * 2); cv.height = Math.round(c.h * 2);
    cv.className = 'fxf'; cv.setAttribute('aria-hidden', 'true');
    c.el.appendChild(cv);
    const mask = document.createElement('canvas'); mask.width = Math.ceil(c.w / 2); mask.height = Math.ceil(c.h / 2);
    st.fog = { cv, ctx: cv.getContext('2d'), mask, mctx: mask.getContext('2d'), dirty: true, wiped: false, last: null, sz: st.size };
    const pat = st.fog.ctx.createPattern(this.tile, 'repeat'); st.fog.pat = pat;
  }
  renderFog(f) {
    const c = f.ctx; f.dirty = false;
    c.globalCompositeOperation = 'source-over'; c.clearRect(0, 0, f.cv.width, f.cv.height);
    c.fillStyle = f.pat; c.fillRect(0, 0, f.cv.width, f.cv.height);
    c.globalCompositeOperation = 'destination-out'; c.drawImage(f.mask, 0, 0, f.cv.width, f.cv.height);
    c.globalCompositeOperation = 'source-over';
  }
  wipe(e) {
    if (this.fog < .1) return;
    const sr = this.screen.getBoundingClientRect(), s = this.sc || 1;
    const px = (e.clientX - sr.left) / s, py = (e.clientY - sr.top) / s;
    for (const c of this.cards) {
      const st = this.map.get(c.el), f = st && st.fog; if (!f) continue;
      if (px < c.x || px > c.x + c.w || py < c.y || py > c.y + c.h) { f.last = null; continue; }
      const lx = px - c.x, ly = py - c.y, last = f.last;
      const seg = last ? Math.hypot(lx - last[0], ly - last[1]) : 0, n = Math.max(1, Math.ceil(seg / 3));
      for (let i = 1; i <= n; i++) {
        const t = i / n, X = last ? last[0] + (lx - last[0]) * t : lx, Y = last ? last[1] + (ly - last[1]) * t : ly;
        f.mctx.globalCompositeOperation = 'source-over'; f.mctx.drawImage(this.brush, X / 2 - 9, Y / 2 - 9, 18, 18);
      }
      f.last = [lx, ly]; f.dirty = true; f.wiped = true;
      this.renderFog(f);
    }
  }
  lift() { for (const c of this.cards) { const st = this.map.get(c.el); if (st && st.fog) st.fog.last = null; } }
}
