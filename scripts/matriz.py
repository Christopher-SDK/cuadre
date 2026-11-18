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
    },
    "CARRERA": {
        "titulo": "Carrera entre transferencias simultáneas",
        "cliente": "Dos pagos llegan al mismo tiempo y uno «pisa» al otro: el saldo que ve el cliente no coincide con sus movimientos.",
        "causa": "Se lee el saldo sin bloqueo y se escribe el valor calculado en Java (leer → modificar → escribir).",
    },
    "REDONDEO": {
        "titulo": "Interés truncado en vez de redondeado",
        "cliente": "El cronograma parece correcto y termina en cero, pero el interés de algunos meses queda un céntimo por debajo de lo pactado.",
        "causa": "Una «mejora» para no cobrar fracciones al cliente usa RoundingMode.DOWN en el interés mensual.",
    },
    "REVERSO_PARCIAL": {
        "titulo": "Reverso que no devuelve el impuesto",
        "cliente": "Le anulan una transferencia hecha por error y le devuelven el monto, pero el ITF cobrado se queda en el banco.",
        "causa": "Alguien interpretó que «el ITF no es reembolsable» y excluyó del reverso las líneas del impuesto.",
    },
    "IMPUESTO_DOBLE": {
        "titulo": "ITF cobrado a ambos lados",
        "cliente": "Le envían S/ 2 000 y recibe S/ 1 999,90: al que recibe también le descontaron el impuesto.",
        "causa": "La regla «el ITF grava débitos y créditos» se aplicó al pie de la letra en una transferencia interna.",
    },
    "CRONOGRAMA": {
        "titulo": "Última cuota sin ajustar",
        "cliente": "Paga todas sus cuotas y el sistema sigue mostrándole una deuda (o un saldo a favor) de unos céntimos.",
        "causa": "La última cuota se calcula igual que las demás y no absorbe los céntimos que dejó el redondeo.",
    },
    "TIMEOUT_A_MEDIAS": {
        "titulo": "Timeout entre débito y crédito",
        "cliente": "El servidor cae a mitad de la transferencia: el dinero sale de su cuenta y no llega a ninguna; al reintentar, se lo cobran otra vez.",
        "causa": "La transferencia se partió en dos transacciones para liberar antes el bloqueo de la cuenta origen.",
    },
}

CAPAS = [
    ("concurrencia", "Concurrencia"), ("estado", "Modelo de referencia"), ("oraculo", "Oráculo financiero"),
    ("credito", "Propiedades de crédito"), ("propiedades", "Invariantes"), ("api", "API"), ("unitario", "Unitarias"),
    ("arquitectura", "Arquitectura"),
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
                "prueba": caso.get("name"),
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
        # El caso mínimo que se muestra: primero una secuencia reducida del modelo (cuenta la historia completa),
        # luego un ejemplo reducido por jqwik, luego una aserción descrita ("[…]"); dentro de cada grupo, el más corto.
        orden = {"cadena": 0, "ejemplo": 1, "mensaje": 2}
        candidatos = sorted(fallas, key=lambda f: (orden[f["tipoCaso"]], not f["casoMinimo"].startswith("["),
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
            "reglaQueLoAtrapo": primera["prueba"] if primera else None,
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
        "bugs": filas,
    }

    (opciones.carpeta / "matriz.json").write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")

    lineas = ["| Bug | ¿Detectado? | Prueba que lo detectó | Pruebas que fallaron | Tiempo hasta detectarlo | Caso mínimo |",
              "|---|---|---|---|---|---|"]
    for f in filas:
        caso = (f["casoMinimo"] or "—").replace("\n", "<br>").replace("|", "\\|")
        lineas.append(f"| `{f['bug']}` | {'✅ sí' if f['detectado'] else '❌ no'} | {f['reglaQueLoAtrapo'] or '—'} "
                      f"| {len(f['detectadoPor'])} ({', '.join(f['capas'])}) "
                      f"| {f['segundosHastaDetectar'] if f['segundosHastaDetectar'] is not None else '—'} s | {caso} |")
    tabla = "\n".join(lineas)
    puntaje = f"**{detectados} de {len(filas)} bugs detectados**"
    if limpio is not None:
        puntaje += f" · sistema limpio: {limpio['pruebas']} pruebas, {len(limpio['fallas'])} fallas"
    (opciones.carpeta / "matriz.md").write_text(puntaje + "\n\n" + tabla + "\n", encoding="utf-8")

    if opciones.pagina:
        opciones.pagina.parent.mkdir(parents=True, exist_ok=True)
        opciones.pagina.write_text("window.MATRIZ = " + json.dumps(resultado, ensure_ascii=False, indent=2) + ";\n",
                                   encoding="utf-8")

    print()
    for f in filas:
        print(f"{'✅' if f['detectado'] else '❌'} {f['bug']:<18} {len(f['detectadoPor']):>3} pruebas lo detectan "
              f"· la más rápida: {f['reglaQueLoAtrapo'] or '—'} ({f['segundosHastaDetectar']} s)")
    print()
    print(puntaje.replace("**", ""))

    if limpio is not None and limpio["fallas"]:
        print("El sistema limpio tiene fallas:", *resultado["limpio"]["detalle"], sep="\n  ")
        return 1
    return 0 if detectados == len(filas) else 1


if __name__ == "__main__":
    sys.exit(main())
