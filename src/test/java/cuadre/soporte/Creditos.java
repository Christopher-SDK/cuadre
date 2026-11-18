package cuadre.soporte;

import cuadre.dominio.credito.CondicionesCredito;
import cuadre.dominio.dinero.Dinero;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.credito.CalculadoraFrancesa;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static cuadre.dominio.dinero.Moneda.PEN;

/** Generadores de condiciones de crédito realistas: consumo, vehicular e hipotecario. */
public final class Creditos {

    private Creditos() {
    }

    public static Arbitrary<CondicionesCredito> condiciones() {
        Arbitrary<Long> monto = Arbitraries.oneOf(
                Arbitraries.longs().between(500_00, 30_000_00),
                Arbitraries.longs().between(30_000_00, 1_000_000_00));
        Arbitrary<Integer> plazo = Arbitraries.oneOf(
                Arbitraries.integers().between(1, 60),
                Arbitraries.integers().between(61, 360));
        Arbitrary<BigDecimal> tea = Arbitraries.oneOf(
                Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("0.30")).ofScale(4),
                Arbitraries.bigDecimals().between(new BigDecimal("0.30"), new BigDecimal("1.20")).ofScale(4),
                Arbitraries.of(BigDecimal.ZERO, new BigDecimal("0.12"), new BigDecimal("0.2499")));
        Arbitrary<BigDecimal> desgravamen = Arbitraries.oneOf(
                Arbitraries.just(BigDecimal.ZERO),
                Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("0.0010")).ofScale(5));
        Arbitrary<Long> comisionMensual = Arbitraries.oneOf(Arbitraries.just(0L), Arbitraries.longs().between(1, 20_00));
        Arbitrary<Long> comisionDesembolso = Arbitraries.oneOf(Arbitraries.just(0L), Arbitraries.longs().between(1, 300_00));
        return Combinators.combine(monto, plazo, tea, desgravamen, comisionMensual, comisionDesembolso)
                .as((m, n, t, d, cm, cd) -> new CondicionesCredito(Dinero.centimos(m, PEN), n, t, d,
                        Dinero.centimos(cm, PEN), Dinero.centimos(cd, PEN)))
                .filter(Creditos::viable);
    }

    /**
     * Con plazos muy largos, tasas altas o montos chicos, el redondeo de la cuota a céntimos acumula más que el saldo
     * y el sistema rechaza el crédito (ver {@code TceaFrenteATeaTest}). Aquí generamos solo créditos que se otorgan,
     * y lo decide una calculadora limpia: así un mutante del cronograma no puede esconderse detrás del filtro.
     */
    static boolean viable(CondicionesCredito c) {
        try {
            new CalculadoraFrancesa(RoundingMode.HALF_UP).calcular(c);
            return true;
        } catch (OperacionRechazada noViable) {
            return false;
        }
    }
}
