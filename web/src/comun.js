// Piezas compartidas: tema, scroll suave, entradas al ver, odómetro, copiar y el ajuste del primer pantallazo.
import Lenis from 'lenis';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);
const raiz = document.documentElement;
export const reducido = matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---------------------------------------------------------------- tema
const botonTema = document.getElementById('tema');
const oscuro = () => (raiz.dataset.theme ? raiz.dataset.theme === 'dark' : !matchMedia('(prefers-color-scheme: light)').matches);
function pintarTema() {
  if (!botonTema) return;
  const o = oscuro();
  botonTema.setAttribute('aria-pressed', String(o));
  botonTema.setAttribute('aria-label', o ? 'Modo oscuro activado: cambiar a modo claro' : 'Modo claro activado: cambiar a modo oscuro');
  botonTema.querySelector('.i-sol').hidden = !o;
  botonTema.querySelector('.i-luna').hidden = o;
}
botonTema?.addEventListener('click', () => {
  raiz.dataset.theme = oscuro() ? 'light' : 'dark';
  try { localStorage.setItem('cuadre-tema', raiz.dataset.theme); } catch (e) { /* sin almacenamiento: el tema no se recuerda */ }
  pintarTema();
});
matchMedia('(prefers-color-scheme: light)').addEventListener('change', pintarTema);
pintarTema();

// ---------------------------------------------------------------- scroll suave (solo rueda; el táctil queda nativo)
export let lenis = null;
if (!reducido && matchMedia('(pointer: fine)').matches) {
  lenis = new Lenis({ lerp: 0.12 });
  lenis.on('scroll', ScrollTrigger.update);
  gsap.ticker.add((t) => lenis.raf(t * 1000));
  gsap.ticker.lagSmoothing(0);
}

// ---------------------------------------------------------------- entradas al ver
export function observar(selector, alVer, margen = '0px 0px -8% 0px') {
  const piezas = document.querySelectorAll(selector);
  if (!('IntersectionObserver' in window) || reducido) {
    piezas.forEach((p) => { p.classList.add('visto'); alVer?.(p); });
    return;
  }
  const io = new IntersectionObserver((entradas) => {
    for (const e of entradas) {
      if (!e.isIntersecting) continue;
      e.target.classList.add('visto');
      alVer?.(e.target);
      io.unobserve(e.target);
    }
  }, { rootMargin: margen });
  piezas.forEach((p) => io.observe(p));
}

// ---------------------------------------------------------------- odómetro: cada carácter rueda desde abajo
export function odometro(el) {
  const texto = el.textContent;
  el.textContent = '';
  const caja = document.createElement('span');
  caja.className = 'odometro';
  caja.setAttribute('aria-hidden', 'true');
  [...texto].forEach((c, i) => {
    const s = document.createElement('span');
    s.textContent = c === ' ' ? ' ' : c;
    s.style.setProperty('--i', i);
    caja.append(s);
  });
  const sr = document.createElement('span');
  sr.className = 'sr';
  sr.textContent = texto;
  el.append(caja, sr);
  return caja;
}

// ---------------------------------------------------------------- copiar comandos
document.querySelectorAll('.copiar').forEach((b) => {
  b.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(b.dataset.copiar);
      b.textContent = 'Copiado';
    } catch (e) {
      b.textContent = 'Selecciónalo';
    }
    setTimeout(() => { b.textContent = 'Copiar'; }, 1600);
  });
});

// ---------------------------------------------------------------- primer pantallazo completo
// Si algo del héroe quedaría cortado por el borde inferior, las piezas secundarias bajan enteras.
export function pliegue(heroe, piezas, destino) {
  const marcas = piezas.map((p) => { const m = document.createComment('pieza'); p.before(m); return m; });
  const borde = () => Math.max(...[...heroe.querySelectorAll('h1, p, a, .acciones, .impacto')].filter((e) => e.offsetParent).map((e) => e.getBoundingClientRect().bottom + scrollY));
  function ajustar() {
    piezas.forEach((p, i) => marcas[i].after(p));
    destino.hidden = true;
    for (let k = piezas.length - 1; k >= 0 && borde() > innerHeight - 1; k--) {
      destino.prepend(piezas[k]);
      destino.hidden = false;
    }
  }
  ajustar();
  document.fonts?.ready.then(ajustar);
  let w = innerWidth, h = innerHeight;
  addEventListener('resize', () => { if (innerWidth !== w || innerHeight !== h) { w = innerWidth; h = innerHeight; ajustar(); } });
}

export const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : (varios || uno + 's')}`;
export const segundos = (s) => (s == null ? '—' : s < 1 ? `${Math.max(1, Math.round(s * 1000))} ms` : s < 90 ? `${s.toFixed(1).replace('.', ',')} s` : `${Math.round(s / 60)} min`);
export { gsap, ScrollTrigger };

addEventListener('DOMContentLoaded', () => observar('.revela'));

// Barra: marca la sección en pantalla (inicio) o «Los 7 bugs» (fichas).
const anclas = [...document.querySelectorAll('.cab__nav a.ancla')];
if (document.querySelector('.ficha__heroe')) anclas.find((a) => a.hash === '#bugs')?.setAttribute('aria-current', 'page');
else if ('IntersectionObserver' in window) {
  const io = new IntersectionObserver((es) => es.forEach((e) => {
    if (!e.isIntersecting) return;
    anclas.forEach((a) => (a.hash === `#${e.target.id}` ? a.setAttribute('aria-current', 'location') : a.removeAttribute('aria-current')));
  }), { rootMargin: '-45% 0px -50% 0px' });
  anclas.forEach((a) => { const s = document.getElementById(a.hash.slice(1)); if (s) io.observe(s); });
}
