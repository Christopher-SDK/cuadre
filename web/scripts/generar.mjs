// Genera las páginas de la web desde datos/matriz.json (la corrida real de scripts/matriz.sh).
// El contenido queda en el HTML: se lee sin JavaScript y lo indexan los buscadores.
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { resolve } from 'node:path';

const raiz = resolve(import.meta.dirname, '..');
const D = JSON.parse(readFileSync(resolve(raiz, 'datos/matriz.json'), 'utf8'));
const SITIO = 'https://christopher-sdk.github.io/cuadre/';
// Base de las rutas entre páginas: '/' en desarrollo, '/cuadre/' al publicar (los recursos los reescribe Vite).
const B = process.env.BASE || '/';
const REPO = 'https://github.com/Christopher-SDK/cuadre';

const SLUG = {
  DOBLE_CARGO: 'doble-cargo', CARRERA: 'carrera', REDONDEO: 'redondeo', REVERSO_PARCIAL: 'reverso-parcial',
  IMPUESTO_DOBLE: 'impuesto-doble', CRONOGRAMA: 'cronograma', TIMEOUT_A_MEDIAS: 'timeout-a-medias',
};
const CAPAS = ['Unitarias', 'Invariantes', 'Propiedades de crédito', 'Modelo de referencia', 'Concurrencia', 'Oráculo financiero', 'API'];
const CORTO = { 'Propiedades de crédito': 'Crédito', 'Modelo de referencia': 'Modelo', 'Oráculo financiero': 'Oráculo' };

// Escenarios del libro: montos de ejemplo en céntimos, con el comportamiento verificado de cada defecto
// (ITF de ley 0,005 %: S/ 2 000 → 0,10; casos dorados 1 y 2; API real en Docker).
export const ESCENARIOS = {
  NINGUNO: { op: '<strong>Ana</strong> transfiere S/ 2 000,00 a <strong>Beto</strong>. ITF de ley: S/ 0,10.', filas: [['Ana', 299990, 299990], ['Beto', 200000, 200000], ['ITF por pagar', 10, 10], ['Cuenta puente', 0, 0]] },
  DOBLE_CARGO: { op: 'La app no recibe respuesta y <strong>reintenta a los 87 segundos</strong> con la misma clave.', filas: [['Ana', 299990, 99980], ['Beto', 200000, 400000], ['ITF por pagar', 10, 20], ['Cuenta puente', 0, 0]] },
  CARRERA: { op: '<strong>Ana y Carla</strong> le transfieren S/ 1 000,00 a Beto <strong>al mismo tiempo</strong>.', filas: [['Beto', 200000, 100000], ['Ana', 399995, 399995], ['Carla', 399995, 399995], ['ITF por pagar', 10, 5]] },
  REDONDEO: { op: 'Crédito de S/ 1 000,00 a 3 meses con TEM de 1 % (caso dorado 2).', filas: [['Interés cuota 1', 1000, 1000], ['Interés cuota 2', 670, 669], ['Interés cuota 3', 337, 336], ['Saldo final', 0, 0]] },
  REVERSO_PARCIAL: { op: 'Ana <strong>anula la transferencia</strong> de S/ 2 000,00 que hizo por error.', filas: [['Ana', 500000, 499990], ['Beto', 0, 0], ['ITF por pagar', 0, 10], ['Cuenta puente', 0, 0]] },
  IMPUESTO_DOBLE: { op: 'Ana transfiere S/ 2 000,00 a Beto y <strong>el ITF se cobra a los dos</strong>.', filas: [['Ana', 299990, 299990], ['Beto', 200000, 199990], ['ITF por pagar', 10, 20], ['Cuenta puente', 0, 0]] },
  CRONOGRAMA: { op: 'Crédito de S/ 10 000,00 a 12 meses sin interés (caso dorado 1).', filas: [['Cuota 12', 83337, 83333], ['Total amortizado', 1000000, 999996], ['Saldo final', 0, 4], ['Cuotas pagadas', 12, 12, 'n']] },
  TIMEOUT_A_MEDIAS: { op: 'El servidor <strong>cae entre el débito y el crédito</strong> de S/ 2 000,00.', filas: [['Ana', 500000, 299990], ['Beto', 0, 0], ['ITF por pagar', 0, 10], ['Cuenta puente', 0, 200000]] },
};

// Un comando con su variante de Windows (PowerShell). Sin JS se ve la de macOS / Linux.
const SELECTOR_SO = `<div class="so" role="group" aria-label="Tu sistema operativo">
      <button type="button" class="so__boton" data-so="unix" aria-pressed="true">macOS / Linux</button>
      <button type="button" class="so__boton" data-so="win" aria-pressed="false">Windows</button>
    </div>`;
const partes = (c) => esc(c).split(' ').map((t) => `<span class="arg${t.length > 36 ? ' arg--largo' : ''}">${t}</span>`).join(' ');
const aWin = (c) => c.replace(/\.\/mvnw\b/g, '.\\mvnw.cmd').replace(/ (-D[^ ]+)/g, ' "$1"');
const comando = (etiqueta, unix, win, attrs = '') => {
  const w = win ?? aWin(unix);
  return `<div class="comando" data-unix="${esc(unix)}" data-win="${esc(w)}" ${attrs}><div class="comando__texto"><small>${etiqueta}</small><code>${partes(unix)}</code></div><button class="copiar" type="button" data-copiar="${esc(unix)}">Copiar</button></div>`;
};
// Pregunta al final de cada paso de la guía: «sí» marca el paso y lleva al siguiente; «no» abre la ayuda de ese paso.
const respuesta = (paso, si, consejos) => `<div class="respuesta" data-paso="${paso}">
          <p class="respuesta__pregunta">¿Te salió así?</p>
          <div class="respuesta__botones"><button type="button" class="boton boton--primario respuesta__boton" data-respuesta="si">${si}</button><button type="button" class="boton boton--borde respuesta__boton" data-respuesta="no" aria-expanded="false">No, me salió otra cosa</button></div>
          <div class="respuesta__ayuda" hidden><p class="espera__rotulo">Prueba esto</p><ul>${consejos.map((c) => `<li>${c}</li>`).join('')}</ul></div>
        </div>`;
const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const soles = (c) => (c < 0 ? '−' : '') + Math.floor(Math.abs(c) / 100).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ',' + String(Math.abs(c) % 100).padStart(2, '0');
const seg = (s) => s == null ? '—' : s < 1 ? Math.max(1, Math.round(s * 1000)) + ' ms' : s < 90 ? s.toFixed(1).replace('.', ',') + ' s' : Math.round(s / 60) + ' min';
const plural = (n, uno, varios) => n + ' ' + (n === 1 ? uno : (varios || uno + 's'));
const L_PRUEBAS = D.limpio.pruebas;
// Prueba rápida por bug, medida a mano el 2026-11-18 (con dependencias ya descargadas): tiempo y primera línea de la falla.
const RAPIDA = {
  DOBLE_CARGO: ['unos segundos', 'CuentasPropiedades.idempotencia'],
  CARRERA: ['2 a 3 min: repite la carrera muchas veces', 'ConcurrenciaPruebas.reintentosSimultaneos'],
  REDONDEO: ['unos segundos', 'CasosDoradosTest.temUnoPorCiento'],
  REVERSO_PARCIAL: ['unos segundos', 'CuentasPropiedades.reversoDejaTodoComoEstaba'],
  IMPUESTO_DOBLE: ['unos segundos', 'CuentasPropiedades.itfUnaSolaVez'],
  CRONOGRAMA: ['unos segundos', 'CasosDoradosTest.sinInteres'],
  TIMEOUT_A_MEDIAS: ['unos 15 s', 'ConcurrenciaPruebas.reintentosSimultaneos'],
};
const claseRapida = (bug) => RAPIDA[bug][1].split('.')[0];
const fecha = new Date(D.generado).toLocaleDateString('es-PE', { day: 'numeric', month: 'long', year: 'numeric' });

function filasLibro(bug) {
  return ESCENARIOS[bug].filas.map(([c, m, s, n]) => {
    const d = s - m, num = n === 'n';
    const f = (v) => num ? String(v) : soles(v);
    const dif = d === 0 ? '—' : (d > 0 ? '+' : '−') + f(Math.abs(d));
    return `<tr${d ? ' class="mal"' : ''}><td class="con">${esc(c)}</td><td class="mod">${f(m)}</td><td class="sis">${f(s)}</td><td class="dif">${dif}</td></tr>`;
  }).join('');
}

const libro = (bug, titulo, id) => `
      <section class="libro" id="${id}" aria-labelledby="${id}-titulo">
        <div class="libro__cab"><h2 class="libro__titulo" id="${id}-titulo">${titulo}</h2><span class="etiqueta">ejemplo</span></div>
        <p class="libro__op" data-op>${ESCENARIOS[bug].op}</p>
        <table>
          <caption class="sr">Saldo esperado por el modelo de referencia frente al saldo del sistema</caption>
          <colgroup><col class="c-con"><col class="c-num"><col class="c-num"><col class="c-num dif"></colgroup>
          <thead><tr><th scope="col">Concepto</th><th scope="col">Modelo</th><th scope="col">Sistema</th><th scope="col" class="dif">Diferencia</th></tr></thead>
          <tbody data-filas>${filasLibro(bug)}</tbody>
        </table>`;

const ICONO = `<svg viewBox="0 0 32 32" aria-hidden="true"><rect class="m-fondo" width="32" height="32" rx="8"/><path class="m-linea" d="M9 12.5h14M9 19.5h14"/></svg>`;
const SOL_LUNA = `<svg class="i-luna" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3a9 9 0 1 0 9 9 7 7 0 0 1-9-9z"/></svg><svg class="i-sol" viewBox="0 0 24 24" aria-hidden="true" hidden><circle cx="12" cy="12" r="4.5"/><path class="rayos" d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3M4.6 4.6l2.1 2.1M17.3 17.3l2.1 2.1M4.6 19.4l2.1-2.1M17.3 6.7l2.1-2.1"/></svg>`;

function cabeza({ titulo, descripcion, canonica, tipo = 'website', ld }) {
  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; media-src 'self'; font-src 'self'; connect-src 'self'; base-uri 'self'; form-action 'none'; object-src 'none'">
  <meta name="referrer" content="strict-origin-when-cross-origin">
  <title>${esc(titulo)}</title>
  <meta name="description" content="${esc(descripcion)}">
  <link rel="canonical" href="${canonica}">
  <meta property="og:type" content="${tipo}">
  <meta property="og:site_name" content="Cuadre">
  <meta property="og:title" content="${esc(titulo)}">
  <meta property="og:description" content="${esc(descripcion)}">
  <meta property="og:url" content="${canonica}">
  <meta property="og:image" content="${SITIO}og.png">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="theme-color" content="#0a0f0d">
  <meta name="referrer" content="strict-origin-when-cross-origin">
  <link rel="icon" href="/icono.svg" type="image/svg+xml">
  <link rel="icon" href="/icono-32.png" type="image/png" sizes="32x32">
  <link rel="apple-touch-icon" href="/icono-180.png">
  <link rel="manifest" href="/manifest.webmanifest">
  <link rel="preload" href="/fuentes/InterTight-600_700.woff2" as="font" type="font/woff2" crossorigin>
  <link rel="preload" href="/fuentes/Inter-400_600.woff2" as="font" type="font/woff2" crossorigin>
  <style>
    @view-transition { navigation: auto; }
    html { background: #0a0f0d; color-scheme: dark; }
    html[data-theme="light"] { background: #f5f7f6; color-scheme: light; }
    @media (prefers-color-scheme: light) { html:not([data-theme="dark"]) { background: #f5f7f6; color-scheme: light; } }
  </style>
  <script>
    document.documentElement.classList.add('js');
    try { var t = localStorage.getItem('cuadre-tema'); if (t) document.documentElement.dataset.theme = t; } catch (e) {}
    // Al volver de una ficha, el título viaja de regreso a su entrada en la lista (y al ir, el de destino recibe el mismo nombre).
    addEventListener('pagereveal', function (e) {
      if (!e.viewTransition || !window.navigation || !navigation.activation || !navigation.activation.from) return;
      var partes = new URL(navigation.activation.from.url).pathname.split('/').filter(Boolean);
      var i = partes.indexOf('bug');
      if (i < 0 || !partes[i + 1]) return;
      var t = document.querySelector('[data-titulo-bug="' + partes[i + 1] + '"]');
      if (!t || t.tagName === 'H1') return;
      t.style.viewTransitionName = 'bug-titulo';
      e.viewTransition.finished.finally(function () { t.style.viewTransitionName = ''; });
    });
  </script>
  <script type="application/ld+json">${JSON.stringify(ld)}</script>
  <link rel="stylesheet" href="/src/estilos.css">
</head>
<body>
<a class="saltar" href="#contenido">Saltar al contenido</a>
<header class="cab">
  <div class="envoltura cab__fila">
    <a class="marca" href="${B}" aria-label="Cuadre, inicio">${ICONO}<span>Cuadre</span></a>
    <nav class="cab__nav" aria-label="Secciones">
      <a class="ancla" href="${B}#sembrar">Siembra un bug</a>
      <a class="ancla ancla--media" href="${B}#matriz">Matriz</a>
      <a class="ancla" href="${B}#bugs">Los 7 bugs</a>
      <a class="ancla ancla--media" href="${B}#como">Cómo se prueba</a>
      <a class="ancla" href="${B}#correr">Pruébalo tú</a>
      <a href="${REPO}">GitHub</a>
    </nav>
    <button class="boton-tema" id="tema" type="button" aria-label="Cambiar entre modo claro y oscuro" aria-pressed="false">${SOL_LUNA}</button>
  </div>
</header>
`;
}

const NOTAS_HTML = `
    <ol class="notas" id="notas">
      <li id="nota-1">Resultado de <code>scripts/matriz.sh</code>: la suite completa se corre una vez sin defectos y una vez por cada <code>CUADRE_BUG</code>. Corrida del ${fecha}.</li>
      <li id="nota-2">Pruebas reportadas por Surefire en la corrida sin defectos: unitarias, propiedades (jqwik), modelo de referencia, concurrencia sobre PostgreSQL (Testcontainers), oráculo y API.</li>
      <li id="nota-3">Tiempo de la suite completa en la corrida que generó esta página: GitHub Actions, con PostgreSQL real en Testcontainers. En una máquina local con la imagen ya descargada tarda menos.</li>
    </ol>
`;
const pie = (conNotas) => `
<footer class="pie">
  <div class="envoltura">
${conNotas ? NOTAS_HTML : ''}    <div class="pie__fila">
      <p><strong>Datos 100 % ficticios.</strong> Los parámetros (ITF, TEA, TCEA, desgravamen) están inspirados en conceptos usados en Perú, pero Cuadre no es un sistema certificado ni representa a ninguna entidad financiera real.</p>
      <p class="pie__autor">© 2026 Christopher Arias · MIT</p>
      <nav aria-label="Enlaces del pie"><a href="${REPO}">Código en GitHub</a><a href="${B}allure/">Reporte Allure</a><a href="${REPO}/blob/main/docs/casos-dorados.md">Casos dorados</a><a href="${REPO}/blob/main/LICENSE">Licencia MIT</a></nav>
    </div>
  </div>
</footer>
`;

const scripts = (...nombres) => nombres.map((n) => `<script type="module" src="/src/${n}.js"></script>`).join('\n') + '\n</body>\n</html>\n';
const cifra = (n, texto, nota) => `${n}<a class="nota-ref" href="#nota-${nota}" aria-label="nota ${nota}"><sup>${nota}</sup></a>`;

// ------------------------------------------------------------------ inicio
function inicio() {
  const L = D.limpio;
  const capasPor = (b) => CAPAS.map((c) => b.detectadoPor.filter((d) => d.capa === c).length);
  const maximo = Math.max(1, ...D.bugs.flatMap(capasPor));
  const total = D.bugs.reduce((t, b) => t + b.detectadoPor.length, 0);
  const filasMatriz = D.bugs.map((b, i) => {
    const celdas = capasPor(b).map((n, k) => `<td data-capa="${k}"><span class="celda${n ? (n / maximo < 0.4 ? ' celda--baja' : '') : ' celda--cero'}" style="--n:${(n / maximo).toFixed(2)}" aria-label="${plural(n, 'prueba')} de ${CAPAS[k]}">${n}</span></td>`).join('');
    const p = Math.min(1, Math.log10(Math.max(0.001, b.segundosHastaDetectar) * 1000) / Math.log10(60000));
    return `<tr data-fila="${i}"><th scope="row"><a href="${B}bug/${SLUG[b.bug]}/"><span>${esc(b.titulo)}</span><code>${b.bug}</code></a></th>${celdas}<td class="t"><span class="tiempo" style="--p:${p.toFixed(3)}"><i aria-hidden="true"></i>${seg(b.segundosHastaDetectar)}</span></td></tr>`;
  }).join('\n            ');
  const entradas = D.bugs.map((b, i) => {
    const f = ESCENARIOS[b.bug].filas.find((x) => x[1] !== x[2]);
    const num = f && f[3] === 'n';
    const v = (x) => (num ? '' : 'S/ ') + (num ? x : soles(x));
    const dif = f ? `<span class="entrada__dif" aria-label="${esc(f[0])}: debía ser ${v(f[1])}, el sistema dejó ${v(f[2])}"><span>${esc(f[0])}</span><ins>${v(f[1])}</ins><del>${v(f[2])}</del></span>` : '';
    return `<li class="revela" style="--i:${i % 2}"><a class="entrada" href="${B}bug/${SLUG[b.bug]}/"><span class="entrada__folio">${String(i + 1).padStart(2, '0')}</span><code class="entrada__codigo">${b.bug}</code><h3 data-titulo-bug="${SLUG[b.bug]}">${esc(b.titulo)}</h3><p>${esc(b.cliente)}</p>${dif}<span class="entrada__pie"><span class="estado ${b.detectado ? 'estado--ok' : 'estado--mal'}">${b.detectado ? 'Detectado' : 'No detectado'}</span><span>Ver el caso <span class="flecha" aria-hidden="true">→</span></span></span></a></li>`;
  }).join('\n      ');

  const html = cabeza({
    titulo: 'Cuadre · QA Automation en banca: 7 bugs sembrados, 7 detectados',
    descripcion: 'Un core bancario de práctica con pruebas de propiedades, modelo de referencia, concurrencia y oráculo financiero. Míralo correr y córrelo en 1 minuto.',
    canonica: SITIO,
    ld: { '@context': 'https://schema.org', '@type': 'SoftwareSourceCode', name: 'Cuadre', description: 'Mini core bancario de código abierto para demostrar QA Automation con pruebas basadas en propiedades, modelo de referencia y oráculo financiero.', codeRepository: REPO, programmingLanguage: 'Java', license: 'https://opensource.org/licenses/MIT', inLanguage: 'es' },
  }) + `
<main id="contenido">
  <section class="heroe" id="heroe" aria-labelledby="titular">
    <div class="heroe__fijo">
      <div class="envoltura heroe__rejilla">
        <div class="heroe__texto">
          <p class="antetitulo">QA Automation · banca · open source</p>
          <h1 class="cartel" id="titular" aria-label="En banca, el peor bug no es un botón roto. Es un descuadre.">
            <span class="linea" aria-hidden="true"><span style="--i:0">En banca, el peor bug</span></span>
            <span class="linea" aria-hidden="true"><span style="--i:1">no es un botón roto.</span></span>
            <span class="linea" aria-hidden="true"><span style="--i:2"><em>Es un descuadre.</em></span></span>
          </h1>
          <p class="bajada">Cuadre es un core bancario de práctica con una suite que atrapa los ${D.puntaje.total} bugs que le sembramos.</p>
          <div class="acciones">
            <a class="boton boton--primario" href="${REPO}">Ver el código <span class="boton__igual" aria-hidden="true">=</span></a>
            <a class="boton boton--borde" href="#correr">Pruébalo tú</a>
          </div>
          <p class="aviso">Proyecto de portafolio&nbsp;· no es un banco&nbsp;· datos ficticios</p>
        </div>
        <div class="ventana__ancla" id="ventana-ancla" aria-hidden="true"><span></span><span></span></div>
      </div>
      <figure class="ventana" id="ventana" aria-label="Grabación real de la suite: ${L.pruebas} pruebas, ${L.fallas} fallas">
        <div class="ventana__hueco">
          <video class="ventana__video" src="/video/suite-scrub.mp4" poster="/video/suite-inicio.jpg" muted playsinline preload="metadata" aria-hidden="true"></video>
          <img class="ventana__final" src="/video/suite-fin.jpg" alt="" width="1280" height="720" decoding="async">
        </div>
        <span class="ventana__barra ventana__barra--sup" aria-hidden="true"></span>
        <span class="ventana__barra ventana__barra--inf" aria-hidden="true"></span>
        <figcaption class="ventana__pie"><span class="ventana__estado">BUILD SUCCESS</span> <span>${L.pruebas} pruebas</span> · <span>${L.fallas} fallas</span> · <span>${seg(L.segundosSuite)}</span> · <span>grabación real</span></figcaption>
      </figure>
      <p class="heroe__pista" aria-hidden="true">Baja: la suite corre con el scroll ↓</p>
    </div>
  </section>

  <section class="seccion envoltura" id="sembrar" aria-labelledby="sembrar-titulo">
    <div class="sembrar">
      <div class="sembrar__texto">
        <p class="antetitulo">Siembra un bug</p>
        <h2 class="h2 revela" id="sembrar-titulo">Rómpelo a propósito y mira quién lo atrapa</h2>
        <p class="intro revela">Los 7 defectos ya vienen en el código, apagados; con <code>CUADRE_BUG</code> (o <code>-Dcuadre.bug</code>) se enciende uno. El modelo de referencia dice lo que debía pasar; el sistema, lo que pasó. Si no coinciden, una prueba falla.</p>
        <ol class="tres revela">
          <li><span><strong>Siembra</strong> un defecto con <code>CUADRE_BUG</code>.</span></li>
          <li><span><strong>Corre</strong> la misma suite: propiedades, modelo, concurrencia, oráculo.</span></li>
          <li><span><strong>Lee</strong> qué capa lo atrapó y el caso mínimo que lo reproduce.</span></li>
        </ol>
      </div>
${libro('NINGUNO', 'El libro', 'libro')}
        <div class="siembra">
          <div class="chips" id="chips" role="group" aria-label="Elegir el defecto activo">
            <span class="chips__prefijo" aria-hidden="true">CUADRE_BUG=</span>
            ${['NINGUNO', ...D.bugs.map((b) => b.bug)].map((b) => `<button class="chip" type="button" data-bug="${b}" aria-pressed="${b === 'NINGUNO'}">${b === 'NINGUNO' ? 'limpio' : b}</button>`).join('\n            ')}
          </div>
        </div>
        <div class="veredicto" id="veredicto" aria-live="polite">
          <span class="veredicto__icono" aria-hidden="true"><svg viewBox="0 0 24 24"><path id="veredicto-trazo" d="M6 9h12M6 15h12"/></svg></span>
          <span class="veredicto__texto" id="veredicto-texto"><strong>Cuadra: el sistema coincide con el modelo</strong>Sin defectos activos, la suite completa pasa: ${plural(L.pruebas, 'prueba')}, ${plural(L.fallas, 'falla')}.</span>
        </div>
      </section>
    </div>
  </section>

  <section class="envoltura franja-cifras" aria-label="Resultado de la última corrida">
    <dl class="cifras revela" id="cifras">
      <div class="cifras__grande"><dt>Bugs sembrados detectados</dt><dd><span data-odometro>${D.puntaje.detectados}/${D.puntaje.total}</span><a class="nota-ref" href="#nota-1" aria-label="nota 1"><sup>1</sup></a></dd></div>
      <div><dt>Pruebas en la suite</dt><dd><span data-odometro>${L.pruebas}</span><a class="nota-ref" href="#nota-2" aria-label="nota 2"><sup>2</sup></a></dd></div>
      <div><dt>Fallas sin bugs</dt><dd><span data-odometro>${L.fallas}</span><a class="nota-ref" href="#nota-2" aria-label="nota 2"><sup>2</sup></a></dd></div>
      <div><dt>Suite completa</dt><dd><span data-odometro>${seg(L.segundosSuite)}</span><a class="nota-ref" href="#nota-3" aria-label="nota 3"><sup>3</sup></a></dd></div>
    </dl>
  </section>

  <section class="seccion envoltura" id="matriz" aria-labelledby="matriz-titulo">
    <div class="seccion__cab"><div>
      <h2 class="h2 revela" id="matriz-titulo">Matriz de detección</h2>
      <p class="intro revela">Cada celda cuenta las pruebas de esa capa que fallaron con el bug activo.
        <span class="solo-puntero">Pasa el cursor por un bug para ver qué capas lo atraparon, o por una capa para ver qué atrapó.</span>
        <span class="solo-tactil">Toca un bug o una capa para resaltar sus cruces; desliza la tabla hacia los lados.</span></p>
    </div></div>
    <div class="matriz revela" id="tabla-matriz">
      <div class="matriz__marco" tabindex="0" role="region" aria-label="Matriz de detección, desplazable">
        <table>
          <caption class="sr">Pruebas que fallaron por bug y por capa de prueba</caption>
          <thead><tr id="matriz-cab"><th scope="col">Bug</th>${CAPAS.map((c, k) => `<th scope="col" data-capa="${k}"><button type="button" title="${c}" aria-label="Resaltar los bugs que atrapó la capa ${c}">${CORTO[c] || c}</button></th>`).join('')}<th scope="col" class="t">Primera en fallar</th></tr></thead>
          <tbody id="matriz-filas">
            ${filasMatriz}
          </tbody>
        </table>
      </div>
      <div class="matriz__pie"><span>Más intensa la celda, más pruebas de esa capa fallaron. La barra de tiempo usa escala logarítmica.</span><span>${plural(total, 'falla provocada', 'fallas provocadas')} · ${D.puntaje.detectados} de ${D.puntaje.total} bugs detectados</span></div>
    </div>
  </section>

  <section class="seccion envoltura" id="bugs" aria-labelledby="bugs-titulo">
    <div class="seccion__cab"><div>
      <h2 class="h2 revela" id="bugs-titulo">Los 7 bugs, como los viviría un cliente</h2>
      <p class="intro revela">Errores que pasan en producción, sembrados a propósito para medir si la suite los atrapa. Vienen apagados en el código; tú puedes <a href="#enciende">encender cualquiera</a>. Entra a cada uno para ver el caso paso a paso.</p>
    </div></div>
    <ol class="lista-bugs">
      ${entradas}
    </ol>
  </section>

  <section class="seccion envoltura" id="como" aria-labelledby="como-titulo">
    <div class="seccion__cab"><div>
      <h2 class="h2 revela" id="como-titulo">Cómo se prueba</h2>
      <p class="intro revela">Siete capas, cada una con su trabajo. La que más pesa: las propiedades, que prueban una regla contra cientos de secuencias y reducen la falla al caso mínimo.</p>
    </div></div>
    <div class="dos">
      <ol class="capas">
        <li class="revela"><strong>Unitarias</strong><span>Dinero exacto en céntimos, partida doble y la regla del ITF del art. 13 de la Ley 28194.</span></li>
        <li class="revela"><strong>Invariantes</strong><span>Con jqwik: conservación del dinero, libro cuadrado, sobregiro, idempotencia, reverso exacto y reintentos tras una caída.</span></li>
        <li class="revela"><strong>Propiedades de crédito</strong><span>Amortizaciones que suman el principal, saldo final en 0,00 y TCEA coherente con los costos.</span></li>
        <li class="revela"><strong>Modelo de referencia</strong><span>Cadenas aleatorias de comandos comparadas paso a paso contra un modelo obvio.</span></li>
        <li class="revela"><strong>Concurrencia</strong><span>16 hilos con arranque sincronizado sobre PostgreSQL real, cinco repeticiones con semilla fija.</span></li>
        <li class="revela"><strong>Oráculo financiero</strong><span>Cronograma y TCEA calculados por un camino independiente y comparados al céntimo, más casos dorados hechos a mano.</span></li>
        <li class="revela"><strong>API</strong><span>REST Assured: códigos HTTP, validaciones e Idempotency-Key, con errores problem+json.</span></li>
      </ol>
      <figure class="video video--alto revela">
        <video src="/video/reduce.mp4" poster="/video/reduce.jpg" muted playsinline loop controls preload="none" aria-label="Grabación real: jqwik reduce la falla del doble cargo a un reintento a los 87 segundos"></video>
        <figcaption><strong>El caso mínimo, en vivo.</strong> Con <code>CUADRE_BUG=DOBLE_CARGO</code>, jqwik encuentra la falla y la reduce sola: un reintento a los <strong>87 s</strong>, justo después de que venza la ventana mal convertida de 86,4 s.</figcaption>
      </figure>
    </div>
  </section>

  <section class="seccion envoltura" id="terminal" aria-labelledby="terminal-titulo">
    <div class="seccion__cab"><div>
      <h2 class="h2 revela" id="terminal-titulo">En la terminal</h2>
      <p class="intro revela">Grabaciones reales. Solo se recortaron las esperas de Maven.</p>
    </div></div>
    <div class="videos">
      <figure class="video revela">
        <video src="/video/bug.mp4" poster="/video/bug.jpg" muted playsinline loop controls preload="none" aria-label="Grabación real: con IMPUESTO_DOBLE, la prueba muestra que el destino recibe menos de lo enviado"></video>
        <figcaption><strong>Un bug, atrapado.</strong> <code>CUADRE_BUG=IMPUESTO_DOBLE</code>: el destino recibe 9 995 céntimos en vez de 10 000.</figcaption>
      </figure>
      <figure class="video revela" style="--i:1">
        <video src="/video/api.mp4" poster="/video/api.jpg" muted playsinline loop controls preload="none" aria-label="Grabación real de la API en Docker: dos envíos con la misma clave devuelven la misma operación"></video>
        <figcaption><strong>La API, en Docker.</strong> Dos envíos con la misma <code>Idempotency-Key</code>: una sola operación, el 422 sin fondos y la auditoría que cuadra.</figcaption>
      </figure>
    </div>
  </section>

  <section class="seccion envoltura" id="correr" aria-labelledby="correr-titulo">
    <div class="seccion__cab guia__cab-seccion"><div>
      <p class="antetitulo">Pruébalo tú</p>
      <h2 class="h2 revela" id="correr-titulo">Compruébalo en tu máquina, en 4 pasos</h2>
      <p class="intro revela">No subes ni configuras nada. <strong>Los 7 bugs ya vienen dentro del código, apagados.</strong> Enciendes uno solo para una corrida y, al terminar, el sistema vuelve a estar correcto.</p>
    </div>
    ${SELECTOR_SO}</div>
    <div class="progreso revela" aria-live="polite">
      <div class="progreso__barra" aria-hidden="true"><span></span></div>
      <p class="progreso__texto"><strong data-progreso>0 de 4 pasos listos</strong> · <span data-atrapados>0 de 7 bugs atrapados por ti</span></p>
      <button type="button" class="progreso__reiniciar" hidden>Empezar de nuevo</button>
    </div>
    <ol class="guia">
      <li class="guia__paso revela" data-paso="1">
        <h3 class="guia__titulo">Ten listos Java 21 y Docker</h3>
        <p class="guia__texto">Docker Desktop tiene que estar <strong>abierto</strong>: las pruebas levantan un PostgreSQL real dentro de él. No hace falta instalar Maven ni crear una base de datos.</p>
        <div class="comandos">
          ${comando('Revisa Java: debe decir 21', 'java -version')}
          ${comando('Revisa Docker: debe responder sin «Cannot connect»', 'docker info')}
        </div>
        <p class="guia__enlaces">¿Te falta algo? <a href="https://adoptium.net/temurin/releases/?version=21">Descargar Java 21</a> · <a href="https://www.docker.com/products/docker-desktop/">Descargar Docker Desktop</a></p>
        ${respuesta(1, 'Sí, Java 21 y Docker responden', [
          'Si <code>docker info</code> dice «Cannot connect to the Docker daemon», abre Docker Desktop y espera a que diga que está corriendo.',
          'Si <code>java -version</code> muestra otro número, instala Java 21 con el enlace de arriba.<span data-solo="unix"> En macOS, si tienes varios: <code>export JAVA_HOME=$(/usr/libexec/java_home -v 21)</code>.</span><span data-solo="win"> En Windows, márcale «Set JAVA_HOME» al instalar.</span>',
          'Si dice «command not found», cierra la terminal y ábrela de nuevo después de instalar.',
        ])}
      </li>
      <li class="guia__paso revela" data-paso="2">
        <h3 class="guia__titulo">Descarga el proyecto</h3>
        <div class="comandos">${comando('Clónalo y entra a la carpeta', 'git clone https://github.com/Christopher-SDK/cuadre.git && cd cuadre', 'git clone https://github.com/Christopher-SDK/cuadre.git; cd cuadre')}</div>
        <p class="guia__enlaces">¿Sin Git? <a href="${REPO}/archive/refs/heads/main.zip">Descarga el ZIP</a>, descomprímelo y abre una terminal dentro de la carpeta.</p>
        ${respuesta(2, 'Sí, estoy dentro de la carpeta cuadre', [
          '«git: command not found»: descarga el ZIP con el enlace de arriba.',
          '«already exists»: ya lo descargaste antes; entra con <code>cd cuadre</code>.',
          'Para comprobar que estás en el lugar correcto, debe existir el archivo <code>mvnw</code> en la carpeta.',
        ])}
      </li>
      <li class="guia__paso revela" data-paso="3">
        <h3 class="guia__titulo">Córrelo con el sistema correcto</h3>
        <div class="comandos">${comando('La suite completa, sin bugs (~1 min; la primera vez tarda más porque descarga todo)', './mvnw verify')}</div>
        <div class="espera"><p class="espera__rotulo">Deberías ver</p><pre class="espera__salida"><span>[INFO] Tests run: ${L_PRUEBAS}, Failures: 0, Errors: 0, Skipped: 0</span>
<span class="ok">[INFO] BUILD SUCCESS</span></pre></div>
        ${respuesta(3, 'Sí, BUILD SUCCESS', [
          '«Could not find a valid Docker environment»: Docker Desktop no está abierto. Ábrelo y vuelve a correr el comando.',
          '«release version 21 not supported»: Maven está usando otro Java. Vuelve al paso 1.',
          '<span data-solo="unix">«permission denied: ./mvnw»: <code>chmod +x mvnw</code> y otra vez.</span><span data-solo="win">«no se reconoce ./mvnw»: en Windows es <code>.\\mvnw.cmd</code>; copia el comando con el botón.</span>',
          'Si se queda descargando, es la primera vez: espera unos minutos.',
          'Si ves fallas sin haber encendido ningún bug, eso no debería pasar: <a href="${REPO}/issues">abre un issue</a> con la salida.',
        ])}
      </li>
      <li class="guia__paso revela" data-paso="4" id="enciende">
        <h3 class="guia__titulo">Enciende un bug y mira cómo lo atrapan</h3>
        <p class="guia__texto">Elige uno. El comando cambia solo. <code>-Dcuadre.bug=NOMBRE</code> es lo mismo que la variable <code>CUADRE_BUG=NOMBRE</code>: usa la que prefieras.</p>
        <div class="chips guia__chips" role="group" aria-label="Elige el bug que quieres encender">
          ${D.bugs.map((b, k) => `<button type="button" class="chip" data-guia-bug="${b.bug}" data-clase="${claseRapida(b.bug)}" data-slug="${SLUG[b.bug]}" data-titulo="${esc(b.titulo)}" data-cliente="${esc(b.cliente)}" data-tiempo="${RAPIDA[b.bug][0]}" data-falla="${RAPIDA[b.bug][1]}" aria-pressed="${k === 2}">${b.bug}</button>`).join('\n          ')}
        </div>
        <div class="guia__bug" aria-live="polite"><p><strong data-guia-titulo>${esc(D.bugs[2].titulo)}</strong> <span data-guia-cliente>${esc(D.bugs[2].cliente)}</span></p></div>
        <div class="comandos">
          ${comando('Prueba rápida: solo las pruebas que lo atrapan (<span data-guia-tiempo>' + RAPIDA[D.bugs[2].bug][0] + '</span>)', `./mvnw test -Dcuadre.bug=${D.bugs[2].bug} -Dtest=${claseRapida(D.bugs[2].bug)}`, null, 'data-guia="rapida"')}
          ${comando('La suite completa con el bug encendido', `./mvnw test -Dcuadre.bug=${D.bugs[2].bug}`, null, 'data-guia="completa"')}
        </div>
        <div class="espera espera--falla"><p class="espera__rotulo">Deberías ver</p><pre class="espera__salida"><span>[ERROR] Failures:</span>
<span>[ERROR]   <span data-guia-falla>${RAPIDA[D.bugs[2].bug][1]}</span>:…</span>
<span class="mal">[INFO] BUILD FAILURE</span></pre>
          <p class="espera__nota"><strong>Que falle es la prueba de que funciona:</strong> la suite atrapó el bug. Corre otra vez sin <code>-Dcuadre.bug</code> y vuelve a salir en verde. <a data-guia-ficha href="${B}bug/${SLUG[D.bugs[2].bug]}/">Ver el caso completo de este bug →</a></p>
        </div>
        ${respuesta(4, 'Sí, BUILD FAILURE: lo atrapó', [
          'Si te salió BUILD SUCCESS, el bug no se encendió: copia el comando con el botón, que incluye <code>-Dcuadre.bug=…</code>.',
          '«CUADRE_BUG desconocido»: el nombre debe ser uno de los 7, tal cual, en mayúsculas.',
          'CARRERA tarda de 2 a 3 minutos y TIMEOUT_A_MEDIAS unos 15 s: espera a que termine.',
          'Si ves «Could not find a valid Docker environment», Docker Desktop se cerró: ábrelo de nuevo.',
        ])}
        <p class="guia__logro" data-logro hidden></p>
      </li>
    </ol>
    <div class="guia__extra revela">
      <div class="comandos">${comando('Opcional · la matriz completa: limpio + los 7 bugs (~10–15 min, requiere python3; en Windows, desde Git Bash o WSL)', 'scripts/matriz.sh', 'bash scripts/matriz.sh')}</div>
    </div>
    <div class="ayuda revela">
      <h3 class="guia__titulo">Si algo sale mal</h3>
      ${[
        ['<code>Could not find a valid Docker environment</code>', 'Docker Desktop no está abierto. Ábrelo, espera a que diga que está corriendo y vuelve a lanzar el comando.'],
        ['<code>release version 21 not supported</code>', 'Maven está usando otro Java. Instala Java 21 y revisa que <code>java -version</code> diga 21.<span data-solo="unix"> En macOS, si tienes varios: <code>export JAVA_HOME=$(/usr/libexec/java_home -v 21)</code>.</span><span data-solo="win"> En Windows, reinstala Java 21 marcando «Set JAVA_HOME» y abre una terminal nueva.</span>'],
        ['<code>permission denied: ./mvnw</code>', 'En macOS o Linux, dale permiso una vez: <code>chmod +x mvnw</code>. En Windows no aplica: usa <code>.\\mvnw.cmd</code>.'],
        ['<code>CUADRE_BUG desconocido</code>', 'El nombre del bug no existe. Usa uno de los 7 de arriba, tal cual, en mayúsculas.'],
        ['La primera corrida tarda varios minutos', 'Es normal: descarga Maven, las dependencias y la imagen de PostgreSQL. Las siguientes tardan alrededor de un minuto.'],
      ].map(([q, a]) => `<details class="ayuda__item" name="ayuda"><summary>${q}</summary><p>${a}</p></details>`).join('\n      ')}
    </div>
    <div class="cierre revela">
      <a class="boton boton--primario boton--grande" href="${REPO}">Ver el código <span class="boton__igual" aria-hidden="true">=</span></a>
      <p>¿Encontraste algo raro? <a href="${REPO}/issues">Abre un issue</a>: también es una prueba.</p>
    </div>
  </section>
</main>
` + pie(true) + scripts('comun', 'inicio');
  writeFileSync(resolve(raiz, 'index.html'), html);
}

// ------------------------------------------------------------------ fichas
function ficha(b, i) {
  const slug = SLUG[b.bug];
  const ant = D.bugs[(i - 1 + D.bugs.length) % D.bugs.length], sig = D.bugs[(i + 1) % D.bugs.length];
  const url = `${SITIO}bug/${slug}/`;
  const html = cabeza({
    titulo: `${b.titulo} · bug sembrado · Cuadre`,
    descripcion: `${b.cliente} Lo atrapó «${b.reglaQueLoAtrapo}».`.slice(0, 300),
    canonica: url, tipo: 'article',
    ld: { '@context': 'https://schema.org', '@type': 'TechArticle', headline: b.titulo, description: b.cliente, url, inLanguage: 'es', isPartOf: { '@type': 'SoftwareSourceCode', name: 'Cuadre', codeRepository: REPO } },
  }) + `
<main id="contenido" class="ficha">
  <section class="ficha__heroe envoltura" aria-labelledby="titulo">
    <a class="volver" href="${B}#bugs"><span aria-hidden="true">←</span> Los 7 bugs</a>
    <div class="ficha__fila"><code>CUADRE_BUG=${b.bug}</code><span class="estado ${b.detectado ? 'estado--ok' : 'estado--mal'}">${b.detectado ? 'Detectado' : 'No detectado'}</span><span class="ficha__folio">${String(i + 1).padStart(2, '0')} / ${String(D.bugs.length).padStart(2, '0')}</span></div>
    <div class="ficha__rejilla">
      <div>
        <h1 id="titulo" data-titulo-bug="${slug}">${esc(b.titulo)}</h1>
        <div class="impacto" id="impacto">
          <p class="impacto__rotulo">Qué le pasaría a un cliente</p>
          <p class="impacto__texto">${esc(b.cliente)}</p>
        </div>
      </div>
      <svg class="distinto" viewBox="0 0 200 200" aria-hidden="true"><rect x="10" y="62" width="180" height="22" rx="11"/><rect x="10" y="116" width="180" height="22" rx="11"/><path pathLength="1" d="M140 24 L60 176"/></svg>
    </div>
  </section>
  <div class="envoltura" id="extra" hidden></div>

  <section class="envoltura ficha__datos" aria-label="Causa y prueba">
    <dl class="datos">
      <div class="dato"><dt>Cómo llegó al código</dt><dd>${esc(b.causa)}</dd><dd class="dato__pie">Vive en <a href="${REPO}/blob/main/${b.clase}">${esc(b.clase.split('/').pop())}</a></dd></div>
      <div class="dato dato--regla"><dt>La regla que lo atrapó</dt><dd class="dato__regla">«${esc(b.reglaQueLoAtrapo)}»</dd><dd class="dato__pie">${esc(b.capaDeLaRegla)} · falló en ${seg(b.segundosDeLaRegla)} · ${plural(b.detectadoPor.length, 'prueba')} de ${plural(b.capas.length, 'capa')} fallan</dd></div>
    </dl>
${libro(b.bug, 'El libro con este bug', 'libro')}
    </section>
  </section>

  <section class="seccion seccion--corta envoltura" aria-labelledby="caso-titulo">
    <div class="seccion__cab"><div>
      <h2 class="h2 revela" id="caso-titulo">El caso, paso a paso</h2>
      <p class="intro revela">El defecto contado como lo viviría un cliente. Debajo está la salida real de la prueba, tal como la imprime la suite.</p>
    </div></div>
    <ol class="pasos" id="pasos">
      ${b.caso.map((p, k) => `<li style="--i:${k}">${esc(p)}</li>`).join('\n      ')}
    </ol>
    <div class="resultado" id="resultado" style="--i:${b.caso.length}">
      <div class="resultado__cifras"><span class="resultado__esperado">Esperado · ${esc(b.esperado)}</span><span class="resultado__obtenido">Obtenido · ${esc(b.obtenido)}</span></div>
      <button class="boton boton--borde" id="reproducir" type="button">Reproducir otra vez</button>
    </div>
    <details class="crudo">
      <summary>Salida real de la suite</summary>
      <p class="crudo__de">De «${esc(b.casoMinimoDe)}»:</p>
      <pre>${esc(b.casoMinimo)}</pre>
      <p class="crudo__de">${b.semilla ? 'Semilla de jqwik para repetirla: ' + esc(b.semilla) : /semilla \d+/.test(b.casoMinimo || '') ? 'La semilla de la repetición va en el mensaje.' : 'Sin semilla: la prueba no genera datos al azar.'}</p>
    </details>
  </section>

  <section class="seccion seccion--corta envoltura" aria-labelledby="todas-titulo">
    <div class="seccion__cab"><div><h2 class="h2 revela" id="todas-titulo">Todas las pruebas que lo detectan</h2></div></div>
    <ul class="lista">
      ${b.detectadoPor.map((d) => `<li class="revela"><span>${esc(d.prueba)}</span><small>${esc(d.capa)} · ${seg(d.segundos)}</small></li>`).join('\n      ')}
    </ul>
  </section>

  <section class="seccion seccion--corta envoltura" aria-labelledby="local-titulo">
    <div class="seccion__cab"><div>
      <h2 class="h2 revela" id="local-titulo">Reprodúcelo en tu máquina</h2>
      <p class="intro revela">Java 21 y Docker abierto. Los comandos se adaptan a tu sistema.</p>
    </div>
    ${SELECTOR_SO}</div>
    <div class="comandos">
      ${comando('Prueba rápida: solo las pruebas que lo atrapan', `./mvnw test -Dcuadre.bug=${b.bug} -Dtest=${claseRapida(b.bug)}`)}
      ${comando('Toda la suite con este bug encendido', `./mvnw test -Dcuadre.bug=${b.bug}`)}
    </div>
    <p class="guia__enlaces">Debe terminar en <code>BUILD FAILURE</code>: eso es la suite atrapándolo. ¿Primera vez? <a href="${B}#correr">Sigue la guía de 4 pasos</a>.</p>
  </section>

  <nav class="seccion seccion--corta envoltura vecinos" aria-label="Otros bugs">
    <a href="${B}bug/${SLUG[ant.bug]}/"><small>Bug anterior</small><strong>${esc(ant.titulo)}</strong></a>
    <a href="${B}bug/${SLUG[sig.bug]}/"><small>Bug siguiente</small><strong>${esc(sig.titulo)}</strong></a>
  </nav>
</main>
` + pie(false) + scripts('comun', 'ficha');
  mkdirSync(resolve(raiz, 'bug', slug), { recursive: true });
  writeFileSync(resolve(raiz, 'bug', slug, 'index.html'), html);
}

// ------------------------------------------------------------------ 404
function noEncontrada() {
  const html = cabeza({ titulo: 'Página no encontrada · Cuadre', descripcion: 'La dirección no existe.', canonica: SITIO + '404.html', ld: { '@context': 'https://schema.org', '@type': 'WebPage', name: 'Página no encontrada' } })
    .replace('<meta name="description"', '<meta name="robots" content="noindex">\n  <meta name="description"') + `
<main id="contenido">
<section class="envoltura pagina-404" aria-labelledby="titulo-404">
  <p class="antetitulo">Error 404</p>
  <h1 id="titulo-404" class="cartel" aria-label="Esta página no cuadra.">
    <span class="linea" aria-hidden="true"><span style="--i:0">Esta página</span></span>
    <span class="linea" aria-hidden="true"><span style="--i:1"><em>no cuadra.</em></span></span>
  </h1>
  <p class="bajada">La dirección no existe o cambió de lugar. El libro mayor, en cambio, sigue en su sitio.</p>
  <div class="acciones"><a class="boton boton--primario" href="${B}">Volver al inicio <span class="boton__igual" aria-hidden="true">=</span></a><a class="boton boton--borde" href="${B}#bugs">Ver los 7 bugs</a></div>
</section>
</main>
` + pie(false) + scripts('comun');
  writeFileSync(resolve(raiz, '404.html'), html);
}

rmSync(resolve(raiz, 'bug'), { recursive: true, force: true });
inicio();
D.bugs.forEach(ficha);
noEncontrada();
writeFileSync(resolve(raiz, 'publico/sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n  <url><loc>${SITIO}</loc></url>\n${D.bugs.map((b) => `  <url><loc>${SITIO}bug/${SLUG[b.bug]}/</loc></url>`).join('\n')}\n</urlset>\n`);
writeFileSync(resolve(raiz, 'publico/robots.txt'), `User-agent: *\nAllow: /\nSitemap: ${SITIO}sitemap.xml\n`);
writeFileSync(resolve(raiz, 'publico/escenarios.json'), JSON.stringify(ESCENARIOS));
writeFileSync(resolve(raiz, 'publico/matriz.json'), JSON.stringify({ limpio: D.limpio, bugs: D.bugs.map((b) => ({ bug: b.bug, slug: SLUG[b.bug], clase: b.clase, regla: b.reglaQueLoAtrapo, capa: b.capaDeLaRegla, segundos: b.segundosDeLaRegla, pruebas: b.detectadoPor.length, detectado: b.detectado })) }));
console.log(`generadas: inicio, ${D.bugs.length} fichas, 404`);
