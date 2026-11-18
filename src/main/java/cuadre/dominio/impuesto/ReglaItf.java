package cuadre.dominio.impuesto;

import cuadre.dominio.dinero.Dinero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Impuesto a las transacciones financieras, inspirado en el ITF peruano.
 * Regla de Cuadre: se cobra una sola vez, a quien ordena el débito (retiro o transferencia).
 */
public class ReglaItf {

    public enum Redondeo {
        /**
         * Art. 13 del TUO de la Ley 28194: se suprime el tercer decimal y el segundo
         * se ajusta a 0 si es menor que 5, o a 5 si es mayor. Equivale a bajar al múltiplo de 0,05.
         */
        LEY_PERUANA,
        TRUNCAR,
        MEDIO_ARRIBA
    }

    public record Cargos(Dinero ordenante, Dinero beneficiario) {
    }

    private final BigDecimal tasa;
    private final Redondeo redondeo;

    public ReglaItf(BigDecimal tasa, Redondeo redondeo) {
        if (tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Tasa de ITF fuera de rango: " + tasa);
        }
        this.tasa = tasa;
        this.redondeo = Objects.requireNonNull(redondeo);
    }

    public BigDecimal tasa() {
        return tasa;
    }

    public Dinero sobre(Dinero monto) {
        return switch (redondeo) {
            case TRUNCAR -> monto.multiplicar(tasa, RoundingMode.DOWN);
            case MEDIO_ARRIBA -> monto.multiplicar(tasa, RoundingMode.HALF_UP);
            case LEY_PERUANA -> {
                long centimos = monto.multiplicar(tasa, RoundingMode.DOWN).centimos();
                yield Dinero.centimos(centimos - centimos % 5, monto.moneda());
            }
        };
    }

    public Cargos deTransferencia(Dinero monto) {
        return new Cargos(sobre(monto), Dinero.cero(monto.moneda()));
    }
}
