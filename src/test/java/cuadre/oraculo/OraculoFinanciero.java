package cuadre.oraculo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Segunda implementación, independiente, del cronograma y la TCEA. No comparte código con el dominio
 * y usa otro enfoque a propósito:
 * <ul>
 *   <li>trabaja en soles con BigDecimal de escala 2 (el dominio usa céntimos enteros);</li>
 *   <li>60 dígitos de precisión (el dominio, 34);</li>
 *   <li>TEM por bisección de x¹² = 1 + TEA (el dominio, Newton-Raphson);</li>
 *   <li>cuota como S / Σ (1 + i)^−k (el dominio, fórmula cerrada S·i / (1 − (1 + i)^−n));</li>
 *   <li>TIR por bisección del valor presente (el dominio, Newton-Raphson).</li>
 * </ul>
 * Lo único compartido es el contrato de negocio: interés y desgravamen se redondean al céntimo, mitades hacia arriba,
 * y la última cuota amortiza todo el saldo restante.
 */
public final class OraculoFinanciero {

    static final MathContext MC = new MathContext(60, RoundingMode.HALF_EVEN);

    public record Fila(int numero, BigDecimal saldoInicial, BigDecimal amortizacion, BigDecimal interes,
                       BigDecimal seguro, BigDecimal comision, BigDecimal saldoFinal) {
        public BigDecimal total() {
            return amortizacion.add(interes).add(seguro).add(comision);
        }
    }

    private OraculoFinanciero() {
    }

    public static BigDecimal tem(BigDecimal tea) {
        BigDecimal objetivo = BigDecimal.ONE.add(tea);
        BigDecimal bajo = BigDecimal.ONE, alto = objetivo;
        for (int i = 0; i < 260; i++) {
            BigDecimal medio = bajo.add(alto).divide(BigDecimal.TWO, MC);
            if (medio.pow(12, MC).compareTo(objetivo) < 0) {
                bajo = medio;
            } else {
                alto = medio;
            }
        }
        return bajo.add(alto).divide(BigDecimal.TWO, MC).subtract(BigDecimal.ONE, MC);
    }

    public static BigDecimal cuotaBase(BigDecimal saldo, int n, BigDecimal i) {
        BigDecimal sumaDescuentos = BigDecimal.ZERO;
        BigDecimal descuento = BigDecimal.ONE;
        BigDecimal v = BigDecimal.ONE.divide(BigDecimal.ONE.add(i), MC);
        for (int k = 1; k <= n; k++) {
            descuento = descuento.multiply(v, MC);
            sumaDescuentos = sumaDescuentos.add(descuento, MC);
        }
        return saldo.divide(sumaDescuentos, MC).setScale(2, RoundingMode.HALF_UP);
    }

    public static List<Fila> cronograma(BigDecimal monto, int n, BigDecimal tea, BigDecimal desgravamen, BigDecimal comision) {
        BigDecimal i = tem(tea);
        BigDecimal cuota = cuotaBase(monto, n, i);
        BigDecimal saldo = monto.setScale(2);
        List<Fila> filas = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            BigDecimal interes = saldo.multiply(i, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal seguro = saldo.multiply(desgravamen, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amortizacion = k == n ? saldo : cuota.subtract(interes);
            BigDecimal saldoFinal = saldo.subtract(amortizacion);
            filas.add(new Fila(k, saldo, amortizacion, interes, seguro, comision.setScale(2), saldoFinal));
            saldo = saldoFinal;
        }
        return filas;
    }

    /** TIR mensual por bisección: el valor presente de los pagos cae al subir la tasa. */
    public static BigDecimal tcea(BigDecimal recibido, List<BigDecimal> pagos) {
        BigDecimal bajo = new BigDecimal("-0.5"), alto = BigDecimal.TEN;
        for (int iteracion = 0; iteracion < 300; iteracion++) {
            BigDecimal medio = bajo.add(alto).divide(BigDecimal.TWO, MC);
            if (valorPresente(pagos, medio).compareTo(recibido) > 0) {
                bajo = medio;
            } else {
                alto = medio;
            }
        }
        BigDecimal r = bajo.add(alto).divide(BigDecimal.TWO, MC);
        return BigDecimal.ONE.add(r).pow(12, MC).subtract(BigDecimal.ONE, MC);
    }

    static BigDecimal valorPresente(List<BigDecimal> pagos, BigDecimal r) {
        BigDecimal v = BigDecimal.ONE.divide(BigDecimal.ONE.add(r), MC);
        BigDecimal descuento = BigDecimal.ONE, total = BigDecimal.ZERO;
        for (BigDecimal pago : pagos) {
            descuento = descuento.multiply(v, MC);
            total = total.add(pago.multiply(descuento, MC), MC);
        }
        return total;
    }
}
