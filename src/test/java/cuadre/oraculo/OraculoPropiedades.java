package cuadre.oraculo;

import io.qameta.allure.Epic;
import cuadre.dominio.credito.CalculadoraFrancesa;
import cuadre.dominio.credito.CalculadoraTcea;
import cuadre.dominio.credito.CondicionesCredito;
import cuadre.dominio.credito.Cronograma;
import cuadre.dominio.credito.Cuota;
import cuadre.mutantes.Bug;
import cuadre.mutantes.Ensamble;
import cuadre.mutantes.Ensamble.Parametros;
import cuadre.oraculo.OraculoFinanciero.Fila;
import cuadre.soporte.Creditos;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas diferenciales: el sistema y el oráculo calculan lo mismo por caminos distintos.
 * Si alguna vez discrepan en un céntimo, uno de los dos está mal, y la prueba muestra el caso exacto.
 */
@Epic("5 · Oráculo financiero")
@Label("Oráculo financiero: sistema contra implementación independiente")
class OraculoPropiedades {

    @Provide
    Arbitrary<CondicionesCredito> condiciones() {
        return Creditos.condiciones();
    }

    @Property(tries = 2000)
    @Label("La TEM coincide con la del oráculo en al menos 30 decimales")
    void temCoincide(@ForAll("condiciones") CondicionesCredito c) {
        assertThat(CalculadoraFrancesa.tem(c.tea()).subtract(OraculoFinanciero.tem(c.tea())).abs())
                .isLessThan(new BigDecimal("1e-30"));
    }

    @Property(tries = 2000)
    @Label("Cada cuota del cronograma coincide al céntimo con la del oráculo")
    void cronogramaCoincide(@ForAll("condiciones") CondicionesCredito c) {
        Cronograma sistema = Ensamble.calculadora(Bug.activo(), Parametros.porDefecto()).calcular(c);
        List<Fila> oraculo = OraculoFinanciero.cronograma(c.monto().aDecimal(), c.plazoMeses(), c.tea(),
                c.tasaDesgravamenMensual(), c.comisionMensual().aDecimal());

        assertThat(sistema.cuotas()).hasSameSizeAs(oraculo);
        for (int k = 0; k < oraculo.size(); k++) {
            Cuota s = sistema.cuotas().get(k);
            Fila o = oraculo.get(k);
            assertThat(new BigDecimal[]{s.saldoInicial().aDecimal(), s.amortizacion().aDecimal(), s.interes().aDecimal(),
                    s.seguro().aDecimal(), s.comision().aDecimal(), s.saldoFinal().aDecimal()})
                    .as("cuota %d (saldo, amortización, interés, seguro, comisión, saldo final)", k + 1)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(o.saldoInicial(), o.amortizacion(), o.interes(), o.seguro(), o.comision(), o.saldoFinal());
        }
    }

    @Property(tries = 1000)
    @Label("La TCEA coincide con la del oráculo (Newton-Raphson contra bisección) en 12 decimales")
    void tceaCoincide(@ForAll("condiciones") CondicionesCredito c) {
        Cronograma cronograma = new CalculadoraFrancesa(Parametros.porDefecto().redondeoInteres()).calcular(c);
        BigDecimal sistema = CalculadoraTcea.tcea(c, cronograma);
        BigDecimal oraculo = OraculoFinanciero.tcea(c.monto().restar(c.comisionDesembolso()).aDecimal(),
                cronograma.pagos().stream().map(d -> d.aDecimal()).toList());
        assertThat(sistema.subtract(oraculo).abs()).isLessThan(new BigDecimal("1e-12"));
    }
}
