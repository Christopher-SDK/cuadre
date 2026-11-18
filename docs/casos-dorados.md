# Casos dorados de crédito

Tres créditos calculados a mano, sin el código de Cuadre. La prueba
[`CasosDoradosTest`](../src/test/java/cuadre/oraculo/CasosDoradosTest.java) exige que el sistema reproduzca
estas tablas al céntimo. Se verificaron además con un tercer cálculo independiente (`decimal` de Python, 50 dígitos).

## Reglas del contrato

| Concepto | Regla |
|---|---|
| TEM | `(1 + TEA)^(1/12) − 1`, sin redondear |
| Cuota base (amortización + interés) | `S · i / (1 − (1 + i)^−n)`, redondeada al céntimo, mitad hacia arriba |
| Interés del mes | `saldo · TEM`, redondeado al céntimo, mitad hacia arriba |
| Desgravamen del mes | `saldo · tasa mensual`, redondeado al céntimo, mitad hacia arriba |
| Amortización | `cuota base − interés`; en la última cuota, **todo el saldo restante** |
| Total a pagar | `amortización + interés + desgravamen + comisión mensual` |
| TCEA | TIR mensual `r` de los flujos del cliente; `TCEA = (1 + r)^12 − 1` |

El cliente recibe `monto − comisión de desembolso` y paga el total de cada cuota.

---

## Caso 1 — S/ 10 000 a 12 meses, sin interés

1. TEA = 0 → TEM = 0 → cuota base = 10 000 / 12 = 833,333… → **833,33**.
2. Las cuotas 1 a 11 amortizan 833,33 cada una: 11 × 833,33 = 9 166,63.
3. Queda 10 000 − 9 166,63 = **833,37**: la cuota 12 lo amortiza entero.

| # | Saldo inicial | Amortización | Interés | Total | Saldo final |
|---|---:|---:|---:|---:|---:|
| 1 | 10 000,00 | 833,33 | 0,00 | 833,33 | 9 166,67 |
| … | … | … | … | … | … |
| 11 | 1 666,70 | 833,33 | 0,00 | 833,33 | 833,37 |
| 12 | 833,37 | **833,37** | 0,00 | **833,37** | **0,00** |

Sin la regla de la última cuota (bug `CRONOGRAMA`) el crédito terminaría con **0,04** de deuda.
TCEA = 0 %: el cliente recibe 10 000 y paga exactamente 10 000.

## Caso 2 — S/ 1 000 a 3 meses, TEM de 1 % exacto

Se elige `TEA = 1,01^12 − 1 = 0,126825030131969720661201` (decimal exacto) para que TEM = 0,01.

1. Cuota base = 1 000 · 0,01 / (1 − 1,01^−3).
   1,01^3 = 1,030301 → 1,01^−3 = 0,9705901479… → 1 − 0,9705901479 = 0,0294098521…
   10 / 0,0294098521 = 340,0221… → **340,02**.
2. Mes 1: interés = 1 000,00 · 0,01 = **10,00**; amortización = 340,02 − 10,00 = 330,02; saldo = 669,98.
3. Mes 2: interés = 669,98 · 0,01 = 6,6998 → **6,70** (el 9 sube el céntimo); amortización = 333,32; saldo = 336,66.
4. Mes 3: interés = 336,66 · 0,01 = 3,3666 → **3,37**; amortiza todo el saldo 336,66; total = **340,03**.

| # | Saldo inicial | Amortización | Interés | Total | Saldo final |
|---|---:|---:|---:|---:|---:|
| 1 | 1 000,00 | 330,02 | 10,00 | 340,02 | 669,98 |
| 2 | 669,98 | 333,32 | 6,70 | 340,02 | 336,66 |
| 3 | 336,66 | 336,66 | 3,37 | 340,03 | 0,00 |

Con el bug `REDONDEO` (truncar) el mes 2 cobraría 6,69 y el mes 3 3,36: este caso lo delata.

**TCEA.** Hay que encontrar `r` tal que `340,02/(1+r) + 340,02/(1+r)² + 340,03/(1+r)³ = 1 000`.
Con r = 1 % el lado izquierdo da 1 000,0035… (> 1 000), así que r es apenas mayor que 1 %.
Resolviendo: r = 1,0001771…% → TCEA = 1,010001771^12 − 1 = **12,6849 %**, por encima de la TEA (12,6825 %)
porque el redondeo de la cuota favoreció al banco en dos milésimas de punto.

## Caso 3 — S/ 5 000 a 6 meses, TEA 25 %, con costos

Desgravamen 0,05 % mensual sobre el saldo, comisión de S/ 5,00 por cuota y S/ 50,00 al desembolsar.

1. TEM = 1,25^(1/12) − 1 = 0,018769265121506…
2. Cuota base = 5 000 · i / (1 − (1 + i)^−6) = 888,93.
3. Mes 1: interés = 5 000 · 0,018769265 = 93,846… → 93,85; desgravamen = 5 000 · 0,0005 = 2,50;
   amortización = 888,93 − 93,85 = 795,08; total = 795,08 + 93,85 + 2,50 + 5,00 = **896,43**.
4. Se repite con el nuevo saldo. Mes 6: amortiza el saldo restante 872,52.

| # | Saldo inicial | Amortización | Interés | Desgravamen | Comisión | Total | Saldo final |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 5 000,00 | 795,08 | 93,85 | 2,50 | 5,00 | 896,43 | 4 204,92 |
| 2 | 4 204,92 | 810,01 | 78,92 | 2,10 | 5,00 | 896,03 | 3 394,91 |
| 3 | 3 394,91 | 825,21 | 63,72 | 1,70 | 5,00 | 895,63 | 2 569,70 |
| 4 | 2 569,70 | 840,70 | 48,23 | 1,28 | 5,00 | 895,21 | 1 729,00 |
| 5 | 1 729,00 | 856,48 | 32,45 | 0,86 | 5,00 | 894,79 | 872,52 |
| 6 | 872,52 | 872,52 | 16,38 | 0,44 | 5,00 | 894,34 | 0,00 |

Suma de amortizaciones: 795,08 + 810,01 + 825,21 + 840,70 + 856,48 + 872,52 = **5 000,00**.

**TCEA.** El cliente recibe 5 000 − 50 = **4 950** y paga las seis cuotas de la columna Total.
La TIR mensual es 2,3921541 % → TCEA = **32,80 %**, frente a una TEA de 25 %.
La diferencia de casi 8 puntos es el costo real del seguro y las comisiones: por eso la TCEA, y no la TEA,
es la tasa que se debe comparar entre créditos.
