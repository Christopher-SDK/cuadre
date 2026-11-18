package cuadre.dominio.credito;

import cuadre.dominio.dinero.Dinero;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Tasa de costo efectivo anual: la tasa interna de retorno mensual de lo que el cliente
 * realmente recibe y paga, anualizada como (1 + TIR)^12 − 1. Se resuelve con Newton-Raphson.
 */
public final class CalculadoraTcea {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal TOLERANCIA = new BigDecimal("1e-28");

    private CalculadoraTcea() {
    }

    public static BigDecimal tcea(Dinero recibido, List<Dinero> pagosMensuales, BigDecimal estimacionMensual) {
        BigDecimal r = estimacionMensual;
        for (int iteracion = 0; iteracion < 200; iteracion++) {
            BigDecimal v = BigDecimal.ONE.divide(BigDecimal.ONE.add(r), MC);
            BigDecimal descuento = BigDecimal.ONE;
            BigDecimal valor = recibido.aDecimal().negate();
            BigDecimal derivada = BigDecimal.ZERO;
            for (int k = 1; k <= pagosMensuales.size(); k++) {
                BigDecimal pago = pagosMensuales.get(k - 1).aDecimal();
                derivada = derivada.subtract(pago.multiply(BigDecimal.valueOf(k), MC).multiply(descuento, MC).multiply(v.pow(2, MC), MC), MC);
                descuento = descuento.multiply(v, MC);
                valor = valor.add(pago.multiply(descuento, MC), MC);
            }
            BigDecimal paso = valor.divide(derivada, MC);
            r = r.subtract(paso, MC);
            if (paso.abs().compareTo(TOLERANCIA) < 0) {
                break;
            }
        }
        return BigDecimal.ONE.add(r).pow(12, MC).subtract(BigDecimal.ONE, MC);
    }

    public static BigDecimal tcea(CondicionesCredito condiciones, Cronograma cronograma) {
        return tcea(condiciones.monto().restar(condiciones.comisionDesembolso()), cronograma.pagos(),
                CalculadoraFrancesa.tem(condiciones.tea()));
    }
}
