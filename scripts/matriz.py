#!/usr/bin/env python3
"""Lee los reportes de Surefire de cada corrida (uno por bug) y arma la matriz de detección.

Uso: python3 scripts/matriz.py target/matriz [--pagina docs/matriz.js]
Escribe target/matriz/matriz.json y target/matriz/matriz.md; con --pagina, también los datos de la página demo.
Termina con código 1 si el sistema limpio tiene fallas o si algún bug pasó sin ser detectado.
"""
import argparse
import html
import json
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

BUGS = {
    "DOBLE_CARGO": {
        "titulo": "Doble cargo en un reintento",
        "cliente": "Su app no recibió respuesta, reintentó la transferencia a los dos minutos y el banco le cobró dos veces.",
        "causa": "La ventana de idempotencia de 24 horas se convierte a milisegundos sin el factor 1000: dura 86,4 segundos.",
        "clase": "src/main/java/cuadre/mutantes/VentanaDeReintentos.java",
        "regla": "Idempotencia",
        "caso": ["Ana tiene S/ 5 000,00 y transfiere S/ 2 000,00 a Beto con la clave «pago-alquiler».", "Su app no recibe la respuesta a tiempo.", "La app reintenta a los 87 segundos con la misma clave.", "La ventana de idempotencia, mal convertida, duró 86,4 segundos: el sistema no reconoce la clave y vuelve a transferir."],
        "esperado": "Ana: S/ 2 999,90",
        "obtenido": "Ana: S/ 999,80",
    },
    "CARRERA": {
        "titulo": "Carrera entre transferencias simultáneas",
        "cliente": "Dos pagos llegan al mismo tiempo y uno «pisa» al otro: el saldo que ve el cliente no coincide con sus movimientos.",
        "causa": "Se lee el saldo sin bloqueo y se escribe el valor calculado en Java (leer → modificar → escribir).",
        "clase": "src/main/java/cuadre/mutantes/LecturaSinBloqueo.java",
        "regla": "16 clientes transfiriéndose",
        "caso": ["Tres cuentas empiezan con S/ 1 500,00 cada una.", "16 clientes lanzan a la vez 640 transferencias cruzadas entre ellas (semilla fija por repetición).", "Dos transacciones leen el mismo saldo sin bloqueo y cada una escribe el suyo: la segunda pisa a la primera.", "Al terminar, el auditor compara cada saldo con la suma de sus movimientos."],
        "esperado": "Saldo = suma de sus movimientos",
        "obtenido": "4 cuentas descuadradas",
    },
    "REDONDEO": {
        "titulo": "Interés truncado en vez de redondeado",
        "cliente": "El cronograma parece correcto y termina en cero, pero el interés de algunos meses queda un céntimo por debajo de lo pactado.",
        "causa": "Una «mejora» para no cobrar fracciones al cliente usa RoundingMode.DOWN en el interés mensual.",
        "clase": "src/main/java/cuadre/mutantes/InteresSinCobroDeFracciones.java",
        "regla": "Caso 2:",
        "caso": ["Crédito de S/ 1 000,00 a 3 meses con TEM de 1 % exacto (caso dorado 2).", "Cuota 1: interés sobre S/ 1 000,00 = 10,00.", "Cuota 2: interés sobre S/ 669,98 = 6,6998, que se redondea al céntimo.", "El sistema trunca en lugar de redondear."],
        "esperado": "Interés cuota 2: S/ 6,70",
        "obtenido": "Interés cuota 2: S/ 6,69",
    },
    "REVERSO_PARCIAL": {
        "titulo": "Reverso que no devuelve el impuesto",
        "cliente": "Le anulan una transferencia hecha por error y le devuelven el monto, pero el ITF cobrado se queda en el banco.",
        "causa": "Alguien interpretó que «el ITF no es reembolsable» y excluyó del reverso las líneas del impuesto.",
        "clase": "src/main/java/cuadre/mutantes/ReversoSinItf.java",
        "regla": "Reverso:",
        "caso": ["Ana tiene S/ 5 000,00 y transfiere S/ 2 000,00 a Beto; paga S/ 0,10 de ITF.", "Ana pide anular la transferencia hecha por error.", "El reverso devuelve los S/ 2 000,00 pero omite las líneas del impuesto."],
        "esperado": "Ana: S/ 5 000,00",
        "obtenido": "Ana: S/ 4 999,90",
    },
    "IMPUESTO_DOBLE": {
        "titulo": "ITF cobrado a ambos lados",
        "cliente": "Le envían S/ 2 000 y recibe S/ 1 999,90: al que recibe también le descontaron el impuesto.",
        "causa": "La regla «el ITF grava débitos y créditos» se aplicó al pie de la letra en una transferencia interna.",
        "clase": "src/main/java/cuadre/mutantes/ItfSobreDebitosYCreditos.java",
        "regla": "cobra el ITF una sola vez",
        "caso": ["Ana transfiere S/ 2 000,00 a Beto.", "El sistema cobra el ITF a Ana: S/ 0,10.", "Y también se lo cobra a Beto al acreditarle."],
        "esperado": "Beto: S/ 2 000,00",
        "obtenido": "Beto: S/ 1 999,90",
    },
    "CRONOGRAMA": {
        "titulo": "Última cuota sin ajustar",
        "cliente": "Paga todas sus cuotas y el sistema sigue mostrándole una deuda (o un saldo a favor) de unos céntimos.",
        "causa": "La última cuota se calcula igual que las demás y no absorbe los céntimos que dejó el redondeo.",
        "clase": "src/main/java/cuadre/mutantes/CuotaFijaHastaElFinal.java",
        "regla": "Caso 1:",
        "caso": ["Crédito de S/ 10 000,00 a 12 meses sin interés (caso dorado 1).", "Cuota base: 10 000 / 12 = 833,33. Las 11 primeras amortizan S/ 9 166,63.", "Quedan S/ 833,37, pero la cuota 12 vuelve a ser de 833,33."],
        "esperado": "Saldo final: S/ 0,00",
        "obtenido": "Saldo final: S/ 0,04",
    },
    "TIMEOUT_A_MEDIAS": {
        "titulo": "Timeout entre débito y crédito",
        "cliente": "El servidor cae a mitad de la transferencia: el dinero sale de su cuenta y no llega a ninguna; al reintentar, se lo cobran otra vez.",
        "causa": "La transferencia se partió en dos transacciones para liberar antes el bloqueo de la cuenta origen.",
        "clase": "src/main/java/cuadre/mutantes/TransferenciaEnDosTramos.java",
        "regla": "El libro mayor siempre cuadra",
        "caso": ["Ana tiene S/ 5 000,00 y transfiere S/ 2 000,00 a Beto.", "Se confirma el débito: el dinero pasa de Ana a la cuenta puente.", "El servidor cae antes del crédito."],
        "esperado": "Cuenta puente: S/ 0,00",
        "obtenido": "Cuenta puente: S/ 2 000,00",
    },
}

CAPAS = [
    ("unitario", "Unitarias"), ("propiedades", "Invariantes"), ("credito", "Propiedades de crédito"),
    ("estado", "Modelo de referencia"), ("concurrencia", "Concurrencia"), ("oraculo", "Oráculo financiero"),
    ("api", "API"), ("arquitectura", "Arquitectura"),
]


def capa(clase):
    for paquete, nombre in CAPAS:
        if f".{paquete}." in clase:
            return nombre
    return "Otra"


def limpiar(texto, maximo=1400):
    texto = html.unescape(texto or "").replace("\r", "")
    texto = re.sub(r"\n\s+at .*", "", texto)
    texto = re.sub(r"\n\s*\.\.\.\(\d+ remaining lines.*", "", texto)
    texto = re.sub(r"(?m)^[\w.$]+(Error|Exception):\s*$", "", texto)
    lineas, vistas = [], set()
    for linea in texto.split("\n"):
        clave = linea.strip().rstrip(",")
        if clave and clave in vistas:
            continue
        vistas.add(clave)
        lineas.append(linea)
    texto = "\n".join(lineas)
    texto = re.sub(r"\n{3,}", "\n\n", texto).strip()
    return texto[:maximo] + ("…" if len(texto) > maximo else "")


def reportes_jqwik(texto):
    """jqwik encabeza cada reporte con «timestamp = …, <Clase>:<Propiedad> =»; así se asocia cada uno a su prueba."""
    bloques = {}
    partes = re.split(r"timestamp = [^,\n]+, (.+?) = ?\n", texto)
    for i in range(1, len(partes) - 1, 2):
        bloques[partes[i].strip()] = partes[i + 1]
    return bloques


def reporte_de(bloques, prueba):
    for etiqueta, bloque in bloques.items():
        if etiqueta.endswith(":" + prueba):
            return bloque
    return ""


def caso_minimo(mensaje, reporte):
    """El caso más chico que reproduce la falla: la cadena reducida por jqwik, el ejemplo reducido o el mensaje."""
    m = re.search(r"Run failed after the following actions: \[(.*?)\n\s*\]\s*final state", mensaje, re.S)
    if m:
        pasos = [p.strip() for p in m.group(1).strip().split("\n") if p.strip()]
        motivo = re.search(r"\[(paso \d+: [^\]]+)\]", mensaje)
        texto = "\n".join(f"{i}. {p}" for i, p in enumerate(pasos, 1))
        return texto + (f"\n\n→ {motivo.group(1)}" if motivo else ""), len(pasos), "cadena"
    m = re.search(r"Shrunk Sample \(\d+ steps?\)\n-+\n(.*?)\n\n", reporte.split("Original Sample")[0], re.S)
    if m:
        return re.sub(r"^\s+", "", m.group(1), flags=re.M), 1, "ejemplo"
    return limpiar(mensaje, 600), 1, "mensaje"


def semilla(reporte):
    m = re.search(r"seed = (-?\d+)", reporte)
    return m.group(1) if m else None


def leer_corrida(carpeta):
    pruebas, fallas = 0, []
    for xml in sorted(carpeta.glob("TEST-*.xml")):
        raiz = ET.parse(xml).getroot()
        bloques = reportes_jqwik("".join((c.findtext("system-out") or "") for c in raiz.iter("testcase")))
        for caso in raiz.iter("testcase"):
            pruebas += 1
            problema = caso.find("failure")
            if problema is None:
                problema = caso.find("error")
            if problema is None:
                continue
            mensaje = (problema.get("message") or "") + "\n" + (problema.text or "")
            clase = caso.get("classname") or raiz.get("name")
            reporte = reporte_de(bloques, caso.get("name")) or (caso.findtext("system-out") or "")
            caso_reducido, pasos, tipo = caso_minimo(mensaje, reporte)
            fallas.append({
                "prueba": re.sub(r" (repetición \d+ de \d+)$", r" · \1", caso.get("name") or ""),
                "grupo": raiz.get("name").split(".")[-1],
                "capa": capa(raiz.get("name")),
                "segundos": round(float(caso.get("time") or 0), 3),
                "semilla": semilla(reporte),
                "casoMinimo": caso_reducido,
                "pasos": pasos,
                "tipoCaso": tipo,
                "mensaje": limpiar(problema.get("message") or problema.text, 500),
                "clase": clase,
            })
    segundos = carpeta / "segundos"
    return {
        "pruebas": pruebas,
        "fallas": fallas,
        "segundosSuite": int(segundos.read_text().strip()) if segundos.exists() else None,
    }


def main():
    args = argparse.ArgumentParser()
    args.add_argument("carpeta", type=Path)
    args.add_argument("--pagina", type=Path)
    args.add_argument("--con-allure", action="store_true", help="el sitio publicado incluye el reporte Allure")
    args.add_argument("--json", type=Path, help="copia de matriz.json para el generador de la web")
    opciones = args.parse_args()

    corridas = {c.name: leer_corrida(c) for c in sorted(opciones.carpeta.iterdir()) if c.is_dir()}
    if not corridas:
        sys.exit(f"No hay corridas en {opciones.carpeta}")

    limpio = corridas.get("NINGUNO")
    filas = []
    for bug, meta in BUGS.items():
        corrida = corridas.get(bug)
        if corrida is None:
            continue
        fallas = sorted(corrida["fallas"], key=lambda f: f["segundos"])
        primera = fallas[0] if fallas else None
        # la regla que se cuenta es la que ilustra el caso de negocio, si está entre las que fallaron
        regla = next((f for f in fallas if meta.get("regla") and meta["regla"] in f["prueba"]), primera)
        # El caso mínimo que se muestra: primero una secuencia corta del modelo (cuenta la historia completa),
        # luego un ejemplo reducido por jqwik, luego una aserción descrita ("[…]"); dentro de cada grupo, el más corto.
        # Una cadena larga (sin reducir) se lee peor que un ejemplo reducido, así que va después de él.
        def prioridad(f):
            if f["tipoCaso"] == "cadena":
                return 0 if f["pasos"] <= 8 else 2
            return 1 if f["tipoCaso"] == "ejemplo" else 3

        candidatos = sorted(fallas, key=lambda f: (prioridad(f), not f["casoMinimo"].startswith("["),
                                                   f["pasos"], len(f["casoMinimo"])))
        minimo = candidatos[0] if candidatos else None
        filas.append({
            "bug": bug, **meta,
            "detectado": bool(fallas),
            "pruebas": corrida["pruebas"],
            "detectadoPor": [{k: f[k] for k in ("prueba", "grupo", "capa", "segundos", "semilla")} for f in fallas],
            "capas": sorted({f["capa"] for f in fallas}),
            "segundosHastaDetectar": primera["segundos"] if primera else None,
            "segundosSuite": corrida["segundosSuite"],
            "casoMinimo": minimo["casoMinimo"] if minimo else None,
            "casoMinimoDe": minimo["prueba"] if minimo else None,
            "primeraEnFallar": primera["prueba"] if primera else None,
            "reglaQueLoAtrapo": regla["prueba"] if regla else None,
            "capaDeLaRegla": regla["capa"] if regla else None,
            "segundosDeLaRegla": regla["segundos"] if regla else None,
            "semilla": minimo["semilla"] if minimo else None,
        })

    detectados = sum(f["detectado"] for f in filas)
    resultado = {
        "generado": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "limpio": None if limpio is None else {
            "pruebas": limpio["pruebas"], "fallas": len(limpio["fallas"]), "segundosSuite": limpio["segundosSuite"],
            "detalle": [f["grupo"] + " · " + f["prueba"] for f in limpio["fallas"]],
        },
        "puntaje": {"detectados": detectados, "total": len(filas)},
        "allure": opciones.con_allure,
        "bugs": filas,
    }

    (opciones.carpeta / "matriz.json").write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")

    lineas = ["| Bug | ¿Detectado? | Prueba que lo detectó | Pruebas que fallaron | Tiempo hasta detectarlo | Caso mínimo |",
              "|---|---|---|---|---|---|"]
    for f in filas:
        caso = (f["casoMinimo"] or "—").replace("\n", "<br>").replace("|", "\\|")
        lineas.append(f"| `{f['bug']}` | {'✅ sí' if f['detectado'] else '❌ no'} | {f['primeraEnFallar'] or '—'} "
                      f"| {len(f['detectadoPor'])} ({', '.join(f['capas'])}) "
                      f"| {f['segundosHastaDetectar'] if f['segundosHastaDetectar'] is not None else '—'} s | {caso} |")
    tabla = "\n".join(lineas)
    puntaje = f"**{detectados} de {len(filas)} bugs detectados**"
    if limpio is not None:
        puntaje += f" · sistema limpio: {limpio['pruebas']} pruebas, {len(limpio['fallas'])} fallas"
    (opciones.carpeta / "matriz.md").write_text(puntaje + "\n\n" + tabla + "\n", encoding="utf-8")

    if opciones.json:
        opciones.json.parent.mkdir(parents=True, exist_ok=True)
        opciones.json.write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")

    if opciones.pagina:
        opciones.pagina.parent.mkdir(parents=True, exist_ok=True)
        opciones.pagina.write_text("window.MATRIZ = " + json.dumps(resultado, ensure_ascii=False, indent=2) + ";\n",
                                   encoding="utf-8")

    print()
    for f in filas:
        print(f"{'✅' if f['detectado'] else '❌'} {f['bug']:<18} {len(f['detectadoPor']):>3} pruebas lo detectan "
              f"· la más rápida: {f['primeraEnFallar'] or '—'} ({f['segundosHastaDetectar']} s)")
    print()
    print(puntaje.replace("**", ""))

    if limpio is not None and limpio["fallas"]:
        print("El sistema limpio tiene fallas:", *resultado["limpio"]["detalle"], sep="\n  ")
        return 1
    return 0 if detectados == len(filas) else 1


if __name__ == "__main__":
    sys.exit(main())
