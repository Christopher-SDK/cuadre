// Inicio: la ventana «=» (FIRMA-01), siembra un bug (FIRMA-02), la matriz que se enciende (FIRMA-03), cifras y videos.
import { gsap, ScrollTrigger, observar, odometro, plural, segundos, reducido, pliegue, lenis, aplicarSO } from './comun.js';

const BASE = import.meta.env.BASE_URL;

// ---------------------------------------------------------------- FIRMA-01 · la ventana «=»
// Las barras del signo se separan y, entre ellas, la suite real avanza con el scroll hasta «BUILD SUCCESS».
const heroe = document.getElementById('heroe');
const ventana = document.getElementById('ventana');
const ancla = document.getElementById('ventana-ancla');
const video = ventana.querySelector('video');
const final = ventana.querySelector('.ventana__final');
const pie = ventana.querySelector('.ventana__pie');

if (!reducido) {
  // Transformación que lleva la ventana (centrada, a su tamaño final) a la posición y escala del ancla.
  const haciaAncla = () => {
    const a = ancla.getBoundingClientRect(), v = ventana.getBoundingClientRect();
    const escala = a.width / (v.width / (gsap.getProperty(ventana, 'scale') || 1));
    const cx = a.left + a.width / 2, cy = a.top + a.height / 2;
    const vx = v.left + v.width / 2 - (gsap.getProperty(ventana, 'x') || 0), vy = v.top + v.height / 2 - (gsap.getProperty(ventana, 'y') || 0);
    return { x: cx - vx, y: cy - vy, scale: escala };
  };
  let inicio = haciaAncla();
  gsap.set(ventana, inicio);
  video.preload = 'auto';
  video.addEventListener('loadedmetadata', () => { if (video.currentTime < 0.5) video.currentTime = 0.5; }, { once: true });

  const linea = gsap.timeline({
    defaults: { ease: 'none' },
    scrollTrigger: {
      trigger: heroe, start: 'top top', end: 'bottom bottom', scrub: true, invalidateOnRefresh: true,
      onRefreshInit: () => { gsap.set(ventana, { x: 0, y: 0, scale: 1 }); inicio = haciaAncla(); gsap.set(ventana, inicio); },
      onUpdate: (st) => {
        // El video avanza entre el 30 % y el 95 % del recorrido.
        const p = Math.min(1, Math.max(0, (st.progress - 0.3) / 0.65));
        const buscable = video.seekable.length && video.seekable.end(0) > 0;
        if (video.duration && buscable) video.currentTime = 0.5 + p * (video.duration - 0.55);
        else if (p > 0 && video.paused) video.play().catch(() => {}); // servidor sin Range: se reproduce en vez de arrastrar
        final.style.opacity = p >= 0.97 ? 1 : 0;
        pie.style.opacity = p >= 0.97 ? 1 : 0;
      },
    },
  });
  linea
    .to('.heroe__texto', { y: () => -innerHeight * 0.12, opacity: 0, duration: 0.1 }, 0)
    .to('.heroe__pista', { opacity: 0, duration: 0.08 }, 0)
    .fromTo(ventana, { x: () => inicio.x, y: () => inicio.y, scale: () => inicio.scale }, { x: 0, y: 0, scale: 1, duration: 0.28 }, 0.08)
    .to(ventana, { '--abre': 1, duration: 0.3 }, 0.1);
}

// En teléfono horizontal, lo secundario del héroe baja entero (regla del pliegue).
const texto = document.querySelector('.heroe__texto');
const extra = document.createElement('div');
extra.className = 'envoltura heroe-extra';
extra.hidden = true;
heroe.after(extra);
if (reducido) pliegue(texto, ['.bajada', '.acciones', '.aviso'].map((s) => texto.querySelector(s)), extra);

// ---------------------------------------------------------------- datos de la corrida
const [ESC, M] = await Promise.all([
  fetch(`${BASE}escenarios.json`).then((r) => r.json()),
  fetch(`${BASE}matriz.json`).then((r) => r.json()),
]);
const porBug = Object.fromEntries(M.bugs.map((b) => [b.bug, b]));
const soles = (c) => `${c < 0 ? '−' : ''}${Math.floor(Math.abs(c) / 100).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ')},${String(Math.abs(c) % 100).padStart(2, '0')}`;

// ---------------------------------------------------------------- FIRMA-02 · siembra un bug
const libro = document.getElementById('libro');
const filas = libro.querySelector('[data-filas]');
const op = libro.querySelector('[data-op]');
const veredicto = document.getElementById('veredicto');
const vTexto = document.getElementById('veredicto-texto');
const vTrazo = document.getElementById('veredicto-trazo');
const chips = [...document.querySelectorAll('.chip[data-bug]')];
let actual = 'NINGUNO';

function pintar(bug, animar) {
  const previas = [...filas.rows];
  filas.textContent = '';
  let peor = null;
  ESC[bug].filas.forEach(([c, m, s, n], i) => {
    const d = s - m, num = n === 'n';
    const f = (v) => (num ? String(v) : soles(v));
    const tr = filas.insertRow();
    if (d) { tr.className = 'mal'; peor ??= { c, d, num }; }
    [c, f(m), f(s), d === 0 ? '—' : `${d > 0 ? '+' : '−'}${f(Math.abs(d))}`].forEach((t, j) => {
      const td = tr.insertCell();
      td.className = ['con', 'mod', 'sis', 'dif'][j];
      if (animar && j > 0 && previas[i]?.cells[j]?.textContent !== t) {
        const sp = document.createElement('span'); sp.className = 'cambia'; sp.textContent = t; td.append(sp);
      } else td.textContent = t;
    });
  });
  return peor;
}

function sembrar(bug, animar) {
  if (bug === actual && animar) return;
  actual = bug;
  chips.forEach((c) => c.setAttribute('aria-pressed', String(c.dataset.bug === bug)));
  op.innerHTML = ESC[bug].op;
  const peor = pintar(bug, animar);
  const limpio = bug === 'NINGUNO';
  veredicto.classList.toggle('mal', !limpio);
  veredicto.querySelector('.sello')?.remove();
  vTexto.textContent = '';
  const fuerte = document.createElement('strong');
  if (limpio) {
    vTrazo.setAttribute('d', 'M6 9h12M6 15h12');
    fuerte.textContent = 'Cuadra: el sistema coincide con el modelo';
    vTexto.append(fuerte, `Sin defectos activos, la suite completa pasa: ${plural(M.limpio.pruebas, 'prueba')}, ${plural(M.limpio.fallas, 'falla')}.`);
    return;
  }
  const b = porBug[bug];
  vTrazo.setAttribute('d', 'M6 9h12M6 15h12M8 19L16 5');
  fuerte.textContent = `Descuadre de ${peor.num ? Math.abs(peor.d) : `S/ ${soles(Math.abs(peor.d))}`} en «${peor.c}»`;
  vTexto.append(fuerte, `Lo atrapó «${b.regla}» en ${segundos(b.segundos)} · ${plural(b.pruebas, 'prueba falla', 'pruebas fallan')}.`);
  const sello = document.createElement('a');
  sello.className = `sello${animar && !reducido ? ' cae' : ''}`;
  sello.href = `${BASE}bug/${b.slug}/`;
  sello.textContent = b.detectado ? 'Detectado' : 'No detectado';
  sello.setAttribute('aria-label', `Detectado: ver el caso de ${bug}`);
  veredicto.append(sello);
}
chips.forEach((c) => c.addEventListener('click', () => sembrar(c.dataset.bug, true)));

// La tarjeta no cambia de alto al cambiar de escenario: se mide el mayor y se reserva.
function reservarAlto() {
  libro.style.minHeight = '';
  const previo = actual;
  let max = 0;
  for (const bug of Object.keys(ESC)) { sembrar(bug, false); max = Math.max(max, libro.offsetHeight); }
  sembrar(previo, false);
  libro.style.minHeight = `${max}px`;
}
reservarAlto();
document.fonts?.ready.then(reservarAlto);
let ancho = innerWidth;
addEventListener('resize', () => { if (innerWidth !== ancho) { ancho = innerWidth; reservarAlto(); } });

// ---------------------------------------------------------------- cifras
const odos = [...document.querySelectorAll('[data-odometro]')].map(odometro);
observar('#cifras', () => odos.forEach((o) => o.classList.add('rueda')));

// ---------------------------------------------------------------- FIRMA-03 · la matriz que se enciende
const tabla = document.getElementById('tabla-matriz');
const cab = document.getElementById('matriz-cab');
const cuerpo = document.getElementById('matriz-filas');
const limpiar = () => { delete tabla.dataset.foco; tabla.querySelectorAll('.foco, .foco-suave').forEach((e) => e.classList.remove('foco', 'foco-suave')); };
function enfocarFila(tr) {
  limpiar(); tabla.dataset.foco = 'fila'; tr.classList.add('foco');
  tr.querySelectorAll('td[data-capa]').forEach((td) => {
    if (!td.querySelector('.celda--cero')) { td.classList.add('foco'); cab.querySelector(`th[data-capa="${td.dataset.capa}"]`).classList.add('foco'); }
  });
}
function enfocarCapa(k) {
  limpiar(); tabla.dataset.foco = 'capa';
  cab.querySelector(`th[data-capa="${k}"]`).classList.add('foco');
  cuerpo.querySelectorAll(`td[data-capa="${k}"]`).forEach((td) => { if (!td.querySelector('.celda--cero')) { td.classList.add('foco'); td.parentElement.classList.add('foco-suave'); } });
}
cuerpo.querySelectorAll('tr').forEach((tr) => {
  tr.addEventListener('mouseenter', () => enfocarFila(tr));
  tr.addEventListener('click', (e) => { if (!e.target.closest('a')) enfocarFila(tr); });
  tr.querySelector('a').addEventListener('focus', () => enfocarFila(tr));
});
cab.querySelectorAll('th[data-capa] button').forEach((b) => {
  const k = b.parentElement.dataset.capa;
  ['mouseenter', 'focus', 'click'].forEach((ev) => b.addEventListener(ev, () => enfocarCapa(k)));
});
tabla.addEventListener('mouseleave', limpiar);

// ---------------------------------------------------------------- lista de bugs: el título pulsado viaja a su ficha
document.querySelectorAll('.entrada').forEach((a) => a.addEventListener('click', () => { a.querySelector('h3').style.viewTransitionName = 'bug-titulo'; }));
addEventListener('pageshow', () => document.querySelectorAll('.entrada h3').forEach((h) => { h.style.viewTransitionName = ''; }));

// ---------------------------------------------------------------- videos: corren a la vez mientras se ven, cada uno con su pausa
const videos = document.querySelectorAll('.video video');
if (!reducido && 'IntersectionObserver' in window) {
  const pausados = new WeakSet();
  videos.forEach((v) => {
    v.addEventListener('pause', () => { if (!v.dataset.auto) pausados.add(v); delete v.dataset.auto; });
    v.addEventListener('play', () => pausados.delete(v));
  });
  const io = new IntersectionObserver((es) => es.forEach(({ target: v, isIntersecting }) => {
    if (isIntersecting) { if (!pausados.has(v)) { v.preload = 'auto'; if (!v.dataset.visto) { v.dataset.visto = '1'; v.currentTime = v.duration ? v.duration * 0.3 : 0; } v.play().catch(() => {}); } }
    else if (!v.paused) { v.dataset.auto = '1'; v.pause(); }
  }), { threshold: 0.35 });
  videos.forEach((v) => io.observe(v));
}
observar('.video.revela');
ScrollTrigger.refresh();

// Al volver de una ficha, el navegador restaura el scroll antes de que el libro reserve su alto: se corrige al final.
addEventListener('pagehide', () => sessionStorage.setItem('cuadre-y', String(scrollY)));
const y = Number(sessionStorage.getItem('cuadre-y'));
if (performance.getEntriesByType('navigation')[0]?.type === 'back_forward' && y) {
  if (lenis) lenis.scrollTo(y, { immediate: true }); else scrollTo(0, y);
}

// ---------------------------------------------------------------- guía «Pruébalo tú»: el bug elegido arma sus comandos
const aWin = (c) => c.replace(/\.\/mvnw\b/g, '.\\mvnw.cmd').replace(/ (-D[^ ]+)/g, ' "$1"');
const fijar = (sel, unix) => { const c = document.querySelector(sel); c.dataset.unix = unix; c.dataset.win = aWin(unix); };
document.querySelectorAll('[data-guia-bug]').forEach((chip) => chip.addEventListener('click', () => {
  const { guiaBug: bug, clase, slug, titulo, cliente, tiempo, falla } = chip.dataset;
  document.querySelectorAll('[data-guia-bug]').forEach((c) => c.setAttribute('aria-pressed', String(c === chip)));
  document.querySelector('[data-guia-titulo]').textContent = titulo;
  document.querySelector('[data-guia-cliente]').textContent = cliente;
  document.querySelector('[data-guia-falla]').textContent = falla;
  document.querySelector('[data-guia-tiempo]').textContent = tiempo;
  document.querySelector('[data-guia-ficha]').href = `${BASE}bug/${slug}/`;
  fijar('[data-guia="rapida"]', `./mvnw test -Dcuadre.bug=${bug} -Dtest=${clase}`);
  fijar('[data-guia="completa"]', `./mvnw test -Dcuadre.bug=${bug}`);
  aplicarSO();
}));

// ---------------------------------------------------------------- guía interactiva: progreso, respuestas y bugs atrapados
const GUIA = 'cuadre-guia';
let estado = { hechos: [], atrapados: [] };
try { estado = { ...estado, ...JSON.parse(localStorage.getItem(GUIA) || '{}') }; } catch (e) { /* sin almacenamiento: empieza de cero */ }
const guardar = () => { try { localStorage.setItem(GUIA, JSON.stringify(estado)); } catch (e) { /* idem */ } };
const pasosGuia = [...document.querySelectorAll('.guia__paso')];
const logro = document.querySelector('[data-logro]');
const reiniciar = document.querySelector('.progreso__reiniciar');

function pintarGuia() {
  pasosGuia.forEach((li) => li.classList.toggle('hecho', estado.hechos.includes(Number(li.dataset.paso))));
  document.querySelectorAll('[data-guia-bug]').forEach((c) => c.classList.toggle('atrapado', estado.atrapados.includes(c.dataset.guiaBug)));
  const n = estado.hechos.length, a = estado.atrapados.length;
  document.querySelector('[data-progreso]').textContent = `${n} de 4 pasos listos`;
  document.querySelector('[data-atrapados]').textContent = `${a} de 7 bugs atrapados por ti`;
  document.querySelector('.progreso__barra span').style.transform = `scaleX(${(n + a / 7) / 5})`;
  reiniciar.hidden = !n && !a;
  logro.hidden = !a;
  logro.textContent = a === 7
    ? '¡Atrapaste los 7! Comprobaste en tu máquina lo mismo que la CI comprueba en cada cambio.'
    : `¡Atrapado! Llevas ${a} de 7. Elige otro bug de la lista y repite.`;
}

function irA(el) {
  const titulo = el.querySelector('.guia__titulo');
  titulo.tabIndex = -1;
  if (lenis) lenis.scrollTo(el, { offset: -90 }); else el.scrollIntoView({ behavior: reducido ? 'auto' : 'smooth', block: 'start' });
  titulo.focus({ preventScroll: true });
}

document.querySelectorAll('.respuesta').forEach((r) => {
  const paso = Number(r.dataset.paso);
  const ayuda = r.querySelector('.respuesta__ayuda');
  const no = r.querySelector('[data-respuesta="no"]');
  r.querySelector('[data-respuesta="si"]').addEventListener('click', () => {
    ayuda.hidden = true; no.setAttribute('aria-expanded', 'false');
    if (!estado.hechos.includes(paso)) estado.hechos.push(paso);
    if (paso === 4) {
      const bug = document.querySelector('[data-guia-bug][aria-pressed="true"]').dataset.guiaBug;
      if (!estado.atrapados.includes(bug)) estado.atrapados.push(bug);
    }
    guardar(); pintarGuia();
    const siguiente = pasosGuia.find((li) => !estado.hechos.includes(Number(li.dataset.paso)));
    if (paso < 4 && siguiente) irA(siguiente);
  });
  no.addEventListener('click', () => {
    ayuda.hidden = !ayuda.hidden;
    no.setAttribute('aria-expanded', String(!ayuda.hidden));
  });
});
reiniciar.addEventListener('click', () => { estado = { hechos: [], atrapados: [] }; guardar(); pintarGuia(); irA(pasosGuia[0]); });
pintarGuia();
