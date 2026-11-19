"""QA B1 · spec propia de Cuadre sobre el build servido como GitHub Pages.

Uso: python3 qa/spec.py [http://127.0.0.1:4896/cuadre]
Cada comprobación imprime PASS/FAIL con su evidencia; termina con código 1 si alguna falla.
"""
import sys
from playwright.sync_api import sync_playwright

U = (sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:4896/cuadre').rstrip('/')
RUTAS = ['/', '/bug/doble-cargo/', '/bug/carrera/', '/bug/redondeo/', '/bug/reverso-parcial/',
         '/bug/impuesto-doble/', '/bug/cronograma/', '/bug/timeout-a-medias/']
resultados = []


def check(nombre, ok, evidencia=''):
    resultados.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  {nombre}  {evidencia}")


with sync_playwright() as p:
    b = p.chromium.launch(channel='chrome')  # Chrome real: Chromium de Playwright no trae H.264

    # 1 · rutas, títulos, h1 y errores de consola
    pg = b.new_page(viewport={'width': 1440, 'height': 900})
    errores = []
    pg.on('pageerror', lambda e: errores.append(str(e)))
    pg.on('console', lambda m: errores.append(m.text) if m.type == 'error' else None)
    titulos = set()
    for r in RUTAS:
        resp = pg.goto(U + r)
        pg.wait_for_timeout(500)
        h1 = pg.locator('h1').count()
        titulos.add(pg.title())
        check(f'ruta {r}', resp.status == 200 and h1 == 1, f'status {resp.status}, h1 {h1}')
    check('títulos únicos', len(titulos) == len(RUTAS), f'{len(titulos)}/{len(RUTAS)}')
    p404 = b.new_page()
    resp = p404.goto(U + '/no-existe/')
    p404.close()
    check('ruta inexistente responde 404', resp.status == 404, f'status {resp.status}')

    # 2 · imágenes y videos cargan (densidad 2, carga forzada)
    pg2 = b.new_page(viewport={'width': 1440, 'height': 900}, device_scale_factor=2)
    for r in ['/bug/carrera/', '/']:
        pg2.goto(U + r)
        pg2.evaluate("document.querySelectorAll('img').forEach(i => i.loading = 'eager')")
        pg2.wait_for_timeout(1200)
        rotas = pg2.evaluate("[...document.images].filter(i => !i.complete || i.naturalWidth === 0).map(i => i.src)")
        check(f'imágenes sin romper {r}', not rotas, str(rotas[:2]))
    videos = pg2.evaluate("""Promise.all([...document.querySelectorAll('video')].map(v => new Promise(res => {
        v.preload = 'auto'; v.muted = true;
        const fin = () => res([v.currentSrc.split('/').pop(), v.readyState, v.videoWidth]);
        if (v.readyState >= 2) fin(); else { v.addEventListener('loadeddata', fin, { once: true }); setTimeout(fin, 6000); v.load(); }
    })))""")
    check('todos los videos cargan', all(rs >= 2 and w > 0 for _, rs, w in videos), str(videos))

    # 3 · la ranura del «=» en reposo tiene el color del fondo (defecto reportado por el usuario, 2026-11-18)
    for esquema in ('light', 'dark'):
        pe = b.new_page(viewport={'width': 1440, 'height': 900}, color_scheme=esquema)
        pe.goto(U + '/')
        pe.wait_for_timeout(1000)
        datos = pe.evaluate("""(() => {
            const h = document.querySelector('.ventana__hueco'), cs = getComputedStyle(h);
            return { clip: cs.clipPath, fondo: getComputedStyle(document.body).backgroundColor };
        })()""")
        cerrada = '50%' in datos['clip'] or datos['clip'].startswith('inset(50%')
        check(f'ranura del «=» cerrada en reposo ({esquema})', cerrada, datos['clip'])
        pe.close()

    # 4 · scrub: el video avanza al bajar y retrocede al subir
    pg.goto(U + '/')
    pg.wait_for_timeout(1500)
    alto = pg.evaluate("document.getElementById('heroe').offsetHeight - innerHeight")
    t = []
    for f in (0.2, 0.7, 0.95, 0.5):
        pg.evaluate(f'scrollTo(0, {alto} * {f})')
        pg.wait_for_timeout(700)
        t.append(round(pg.evaluate("document.querySelector('.ventana__video').currentTime"), 2))
    check('scrub avanza y retrocede', t[1] > t[0] and t[2] > t[1] and t[3] < t[2], str(t))

    # 5 · clic real en todos los botones del inicio
    pg.goto(U + '/#sembrar')
    pg.wait_for_timeout(800)
    for chip in pg.locator('.chip[data-bug]').all():
        bug = chip.get_attribute('data-bug')
        chip.click()
        pg.wait_for_timeout(250)
        activo = chip.get_attribute('aria-pressed') == 'true'
        mal = pg.locator('#veredicto.mal').count() == 1
        sello = pg.locator('#veredicto .sello').count()
        ok = activo and ((bug == 'NINGUNO' and not mal and sello == 0) or (bug != 'NINGUNO' and mal and sello == 1))
        check(f'chip {bug}', ok, f'aria-pressed={activo} descuadre={mal} sello={sello}')
    ctx = b.new_context(viewport={'width': 1440, 'height': 900}, permissions=['clipboard-read', 'clipboard-write'])
    pc = ctx.new_page()
    pc.goto(U + '/#correr')
    pc.wait_for_timeout(600)
    for boton in pc.locator('.copiar').all():
        boton.click()
        pc.wait_for_timeout(200)
        copiado = pc.evaluate('navigator.clipboard.readText()')
        check(f'copiar «{boton.get_attribute("data-copiar")}»', copiado == boton.get_attribute('data-copiar') and boton.inner_text() == 'Copiado', copiado)
    antes = pc.evaluate("document.getElementById('tema').getAttribute('aria-pressed')")
    pc.click('#tema')
    despues = pc.evaluate("document.getElementById('tema').getAttribute('aria-pressed')")
    check('botón de tema cambia y lo anuncia', antes != despues, f'{antes} → {despues}')
    pc.goto(U + '/bug/redondeo/')
    pc.wait_for_timeout(600)
    pc.click('#reproducir')
    pc.wait_for_timeout(100)
    check('«Reproducir otra vez» reinicia el caso', pc.locator('#pasos.reproduce').count() == 1)
    ctx.close()

    # 5b · guía «Pruébalo tú»: sistema operativo y bug elegido arman el comando que se copia
    ctx2 = b.new_context(viewport={'width': 1440, 'height': 900}, permissions=['clipboard-read', 'clipboard-write'])
    pq = ctx2.new_page()
    pq.goto(U + '/#correr')
    pq.wait_for_timeout(600)
    pq.click('.so__boton[data-so="unix"]')
    for chip in pq.locator('[data-guia-bug]').all():
        bug, clase = chip.get_attribute('data-guia-bug'), chip.get_attribute('data-clase')
        chip.click()
        cmd = pq.inner_text('[data-guia="rapida"] code')
        ok = cmd == f'./mvnw test -Dcuadre.bug={bug} -Dtest={clase}' and chip.get_attribute('aria-pressed') == 'true'
        check(f'guía arma el comando de {bug}', ok, cmd)
    pq.click('.so__boton[data-so="win"]')
    win = pq.inner_text('[data-guia="rapida"] code')
    pq.click('[data-guia="rapida"] .copiar')
    pq.wait_for_timeout(200)
    copiado = pq.evaluate('navigator.clipboard.readText()')
    check('guía en Windows usa mvnw.cmd y copia lo que se ve', win.startswith('.\\mvnw.cmd test "-Dcuadre.bug=') and copiado == win, win)
    pq.reload()
    pq.wait_for_timeout(600)
    check('el sistema operativo elegido se recuerda', pq.get_attribute('.so__boton[data-so="win"]', 'aria-pressed') == 'true')
    pq.goto(U + '/bug/carrera/')
    pq.wait_for_timeout(500)
    pq.goto(U + '/#correr'); pq.wait_for_timeout(500)
    pq.evaluate("localStorage.removeItem('cuadre-guia')"); pq.reload(); pq.wait_for_timeout(600)
    for n in (1, 2, 3):
        pq.click(f'[data-paso="{n}"] [data-respuesta="si"]')
    pq.click('[data-guia-bug="CRONOGRAMA"]')
    pq.click('[data-paso="4"] [data-respuesta="si"]')
    pq.click('[data-paso="3"] [data-respuesta="no"]')
    ok = (pq.inner_text('[data-progreso]') == '4 de 4 pasos listos' and pq.inner_text('[data-atrapados]') == '1 de 7 bugs atrapados por ti'
          and pq.is_visible('[data-paso="3"] .respuesta__ayuda') and pq.locator('.guia__paso.hecho').count() == 4
          and 'atrapado' in pq.get_attribute('[data-guia-bug="CRONOGRAMA"]', 'class'))
    check('guía interactiva: pasos, ayuda y bugs atrapados', ok, pq.inner_text('[data-progreso]') + ' · ' + pq.inner_text('[data-atrapados]'))
    pq.reload(); pq.wait_for_timeout(600)
    check('el progreso de la guía se recuerda', pq.locator('.guia__paso.hecho').count() == 4)
    pq.click('.progreso__reiniciar'); pq.wait_for_timeout(200)
    check('«Empezar de nuevo» limpia el progreso', pq.locator('.guia__paso.hecho').count() == 0 and pq.inner_text('[data-atrapados]').startswith('0 de 7'))
    pq.goto(U + '/bug/carrera/'); pq.wait_for_timeout(500)
    check('las fichas respetan el sistema operativo', pq.inner_text('.comando code').startswith('.\\mvnw.cmd'), pq.inner_text('.comando code'))
    ctx2.close()

    # 6 · matriz: enfocar un bug con teclado enciende sus capas
    pg.goto(U + '/#matriz')
    pg.wait_for_timeout(700)
    pg.focus('#matriz-filas tr:nth-child(3) th a')
    encendidas = pg.locator('#tabla-matriz td.foco').count()
    check('matriz se enciende con teclado', encendidas >= 2, f'{encendidas} celdas')

    # 7 · View Transitions: ≥ 4 de 5 navegaciones, y volver restaura el scroll
    vt = 0
    for i in range(5):
        pg.goto(U + '/#bugs')
        pg.wait_for_timeout(600)
        pg.evaluate("addEventListener('pageswap', e => sessionStorage.setItem('vt', String(!!e.viewTransition)), { once: true })")
        pg.locator('.entrada').nth(i).click()
        pg.wait_for_load_state('load')
        pg.wait_for_timeout(500)
        vt += pg.evaluate("sessionStorage.getItem('vt') === 'true'")
    check('View Transitions en las navegaciones', vt >= 4, f'{vt}/5')
    pg.goto(U + '/')
    pg.wait_for_timeout(600)
    pg.evaluate("document.getElementById('bugs').scrollIntoView()")
    pg.wait_for_timeout(600)
    y = pg.evaluate('scrollY')
    pg.evaluate("document.querySelectorAll('.entrada')[1].click()")  # sin el auto-scroll de Playwright
    pg.wait_for_load_state('load')
    pg.wait_for_timeout(500)
    pg.go_back()
    pg.wait_for_timeout(1200)
    y2 = pg.evaluate('scrollY')
    check('volver restaura el scroll', abs(y2 - y) < 120, f'{y} → {y2}')

    # 8 · sin errores de consola en todo el recorrido
    check('cero errores de consola', not errores, str(errores[:3]))
    b.close()

fallas = resultados.count(False)
print(f'\nspec · PASS {resultados.count(True)} · FAIL {fallas}')
sys.exit(1 if fallas else 0)
