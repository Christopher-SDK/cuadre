package cuadre.dominio.dinero;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Monto exacto en céntimos. No existe ningún camino desde {@code double}:
 * los montos entran como texto o como céntimos enteros.
 */
public record Dinero(long centimos, Moneda moneda) implements Comparable<Dinero> {

    public Dinero {
        Objects.requireNonNull(moneda, "moneda");
    }

    public static Dinero cero(Moneda moneda) {
        return new Dinero(0, moneda);
    }

    public static Dinero centimos(long centimos, Moneda moneda) {
        return new Dinero(centimos, moneda);
    }

    /** Acepta "150", "150.5" o "150.50". Más de dos decimales es un error, nunca se redondea en silencio. */
    public static Dinero de(String monto, Moneda moneda) {
        BigDecimal valor;
        try {
            valor = new BigDecimal(monto.strip());
        } catch (NumberFormatException | NullPointerException e) {
            throw new OperacionRechazada(Motivo.MONTO_INVALIDO, "Monto no numérico: " + monto);
        }
        return de(valor, moneda);
    }

    public static Dinero de(BigDecimal valor, Moneda moneda) {
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new OperacionRechazada(Motivo.MONTO_INVALIDO, "Un monto tiene como máximo 2 decimales: " + valor);
        }
        try {
            return new Dinero(valor.movePointRight(2).longValueExact(), moneda);
        } catch (ArithmeticException e) {
            throw new OperacionRechazada(Motivo.MONTO_INVALIDO, "Monto fuera de rango: " + valor);
        }
    }

    public Dinero sumar(Dinero otro) {
        exigirMismaMoneda(otro);
        return new Dinero(Math.addExact(centimos, otro.centimos), moneda);
    }

    public Dinero restar(Dinero otro) {
        exigirMismaMoneda(otro);
        return new Dinero(Math.subtractExact(centimos, otro.centimos), moneda);
    }

    public Dinero negar() {
        return new Dinero(Math.negateExact(centimos), moneda);
    }

    /** La regla de redondeo es obligatoria: es una decisión de negocio, no un detalle técnico. */
    public Dinero multiplicar(BigDecimal factor, RoundingMode redondeo) {
        return de(aDecimal().multiply(factor).setScale(2, redondeo), moneda);
    }

    public BigDecimal aDecimal() {
        return BigDecimal.valueOf(centimos, 2);
    }

    public boolean esPositivo() {
        return centimos > 0;
    }

    public boolean esNegativo() {
        return centimos < 0;
    }

    public boolean esCero() {
        return centimos == 0;
    }

    public boolean menorQue(Dinero otro) {
        return compareTo(otro) < 0;
    }

    @Override
    public int compareTo(Dinero otro) {
        exigirMismaMoneda(otro);
        return Long.compare(centimos, otro.centimos);
    }

    private void exigirMismaMoneda(Dinero otro) {
        if (moneda != otro.moneda) {
            throw new OperacionRechazada(Motivo.MONEDA_INCOMPATIBLE,
                    "No se mezclan monedas: " + moneda + " y " + otro.moneda);
        }
    }

    @Override
    public String toString() {
        return moneda + " " + aDecimal().toPlainString();
    }
}
