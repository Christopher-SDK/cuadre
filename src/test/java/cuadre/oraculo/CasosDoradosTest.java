package cuadre.oraculo;

import cuadre.dominio.credito.CalculadoraFrancesa;
import cuadre.dominio.credito.CalculadoraTcea;
import cuadre.dominio.credito.CondicionesCredito;
import cuadre.dominio.credito.Cronograma;
import cuadre.dominio.credito.Cuota;
import cuadre.dominio.dinero.Dinero;
import cuadre.mutantes.Bug;
import cuadre.mutantes.Ensamble;
import cuadre.mutantes.Ensamble.Parametros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static cuadre.dominio.dinero.Moneda.PEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Casos calculados a mano y explicados paso a paso en docs/casos-dorados.md.
 * Son la referencia que no depende de ningún código: si el sistema y el oráculo coincidieran en un error,
 * estos casos lo delatarían.
 */
@DisplayName("Casos dorados de crédito (calculados a mano)")
class CasosDoradosTest {

    static Dinero s(String monto) {
        return Dinero.de(monto, PEN);
    }

    static Cronograma cronograma(CondicionesCredito c) {
        return Ensamble.calculadora(Bug.activo(), Parametros.porDefecto()).calcular(c);
    }

    /** {saldo inicial, amortización, interés, seguro, comisión, total, saldo final} */
    static void fila(Cuota q, String... esperado) {
        assertThat(List.of(q.saldoInicial(), q.amortizacion(), q.interes(), q.seguro(), q.comision(), q.total(), q.saldoFinal()))
                .as("cuota %d", q.numero())
                .containsExactly(s(esperado[0]), s(esperado[1]), s(esperado[2]), s(esperado[3]), s(esperado[4]), s(esperado[5]), s(esperado[6]));
    }

    @Test
    @DisplayName("Caso 1: S/ 10 000 a 12 meses sin interés. La última cuota recoge los 4 céntimos del redondeo")
    void sinInteres() {
        Cronograma c = cronograma(CondicionesCredito.sinCostos(s("10000.00"), 12, BigDecimal.ZERO));

        fila(c.cuotas().get(0), "10000.00", "833.33", "0.00", "0.00", "0.00", "833.33", "9166.67");
        fila(c.cuotas().get(10), "1666.70", "833.33", "0.00", "0.00", "0.00", "833.33", "833.37");
        fila(c.cuotas().get(11), "833.37", "833.37", "0.00", "0.00", "0.00", "833.37", "0.00");
        assertThat(CalculadoraTcea.tcea(CondicionesCredito.sinCostos(s("10000.00"), 12, BigDecimal.ZERO), c))
                .isCloseTo(BigDecimal.ZERO, within(new BigDecimal("1e-20")));
    }

    @Test
    @DisplayName("Caso 2: S/ 1 000 a 3 meses con TEM de 1 % exacto. Redondeo del interés mitad hacia arriba")
    void temUnoPorCiento() {
        BigDecimal tea = new BigDecimal("0.126825030131969720661201"); // 1,01^12 − 1, exacto
        CondicionesCredito condiciones = CondicionesCredito.sinCostos(s("1000.00"), 3, tea);
        Cronograma c = cronograma(condiciones);

        assertThat(CalculadoraFrancesa.tem(tea)).isCloseTo(new BigDecimal("0.01"), within(new BigDecimal("1e-30")));
        fila(c.cuotas().get(0), "1000.00", "330.02", "10.00", "0.00", "0.00", "340.02", "669.98");
        fila(c.cuotas().get(1), "669.98", "333.32", "6.70", "0.00", "0.00", "340.02", "336.66");
        fila(c.cuotas().get(2), "336.66", "336.66", "3.37", "0.00", "0.00", "340.03", "0.00");
        assertThat(CalculadoraTcea.tcea(condiciones, c))
                .isCloseTo(new BigDecimal("0.126848745614242"), within(new BigDecimal("1e-15")));
    }

    @Test
    @DisplayName("Caso 3: S/ 5 000 a 6 meses, TEA 25 %, desgravamen 0,05 % mensual, comisión S/ 5 al mes y S/ 50 al desembolsar")
    void conCostos() {
        CondicionesCredito condiciones = new CondicionesCredito(s("5000.00"), 6, new BigDecimal("0.25"),
                new BigDecimal("0.0005"), s("5.00"), s("50.00"));
        Cronograma c = cronograma(condiciones);

        fila(c.cuotas().get(0), "5000.00", "795.08", "93.85", "2.50", "5.00", "896.43", "4204.92");
        fila(c.cuotas().get(1), "4204.92", "810.01", "78.92", "2.10", "5.00", "896.03", "3394.91");
        fila(c.cuotas().get(2), "3394.91", "825.21", "63.72", "1.70", "5.00", "895.63", "2569.70");
        fila(c.cuotas().get(3), "2569.70", "840.70", "48.23", "1.28", "5.00", "895.21", "1729.00");
        fila(c.cuotas().get(4), "1729.00", "856.48", "32.45", "0.86", "5.00", "894.79", "872.52");
        fila(c.cuotas().get(5), "872.52", "872.52", "16.38", "0.44", "5.00", "894.34", "0.00");
        assertThat(CalculadoraTcea.tcea(condiciones, c))
                .isCloseTo(new BigDecimal("0.328006362035199"), within(new BigDecimal("1e-15")));
    }
}
