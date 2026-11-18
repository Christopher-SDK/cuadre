package cuadre.credito;

import cuadre.dominio.credito.CalculadoraTcea;
import cuadre.dominio.credito.CondicionesCredito;
import cuadre.dominio.credito.Cronograma;
import cuadre.dominio.credito.Cuota;
import cuadre.dominio.dinero.Dinero;
import cuadre.mutantes.Bug;
import cuadre.mutantes.Ensamble;
import cuadre.mutantes.Ensamble.Parametros;
import cuadre.soporte.Creditos;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Label("Crédito: invariantes del cronograma")
class CreditoPropiedades {

    final Cronograma calcular(CondicionesCredito c) {
        return Ensamble.calculadora(Bug.activo(), Parametros.porDefecto()).calcular(c);
    }

    @Provide
    Arbitrary<CondicionesCredito> condiciones() {
        return Creditos.condiciones();
    }

    @Property(tries = 1000)
    @Label("La suma de las amortizaciones es exactamente el monto prestado")
    void amortizacionesSumanElPrincipal(@ForAll("condiciones") CondicionesCredito c) {
        Statistics.label("plazo").collect(c.plazoMeses() <= 60 ? "hasta 5 años" : "más de 5 años");
        assertThat(calcular(c).totalAmortizado()).isEqualTo(c.monto());
    }

    @Property(tries = 1000)
    @Label("Al pagar la última cuota el saldo queda en 0,00 exactos, ni un céntimo más ni menos")
    void saldoFinalCero(@ForAll("condiciones") CondicionesCredito c) {
        assertThat(calcular(c).saldoFinal()).isEqualTo(Dinero.cero(c.monto().moneda()));
    }

    @Property(tries = 1000)
    @Label("Ninguna cuota ni componente es negativo y el saldo baja cada mes")
    void sinCuotasNegativas(@ForAll("condiciones") CondicionesCredito c) {
        Cronograma cronograma = calcular(c);
        Dinero anterior = c.monto();
        for (Cuota q : cronograma.cuotas()) {
            assertThat(q.amortizacion().esNegativo()).as("amortización cuota %d", q.numero()).isFalse();
            assertThat(q.interes().esNegativo()).as("interés cuota %d", q.numero()).isFalse();
            assertThat(q.seguro().esNegativo()).as("seguro cuota %d", q.numero()).isFalse();
            assertThat(q.total().esPositivo()).as("total cuota %d", q.numero()).isTrue();
            assertThat(q.saldoFinal().menorQue(anterior) || q.saldoFinal().equals(anterior) && q.amortizacion().esCero()).isTrue();
            anterior = q.saldoFinal();
        }
    }

    @Property(tries = 1000)
    @Label("Cobrar seguro o comisiones siempre encarece el crédito: la TCEA con costos supera a la del mismo crédito sin costos")
    void losCostosSubenLaTcea(@ForAll("condiciones") CondicionesCredito c) {
        Cronograma cronograma = calcular(c);
        long costosCobrados = c.comisionDesembolso().centimos() + cronograma.cuotas().stream()
                .mapToLong(q -> q.seguro().centimos() + q.comision().centimos()).sum();
        Assume.that(costosCobrados > 0);
        CondicionesCredito sinCostos = CondicionesCredito.sinCostos(c.monto(), c.plazoMeses(), c.tea());

        assertThat(CalculadoraTcea.tcea(c, cronograma))
                .isGreaterThan(CalculadoraTcea.tcea(sinCostos, calcular(sinCostos)));
    }

    @Property(tries = 500)
    @Label("Sin seguro ni comisiones, la TCEA solo se aparta de la TEA por el redondeo a céntimos (< 0,05 puntos)")
    void tceaIgualTeaSinCostos(@ForAll("condiciones") CondicionesCredito c) {
        CondicionesCredito limpio = CondicionesCredito.sinCostos(c.monto(), c.plazoMeses(), c.tea());
        BigDecimal tcea = CalculadoraTcea.tcea(limpio, calcular(limpio));
        assertThat(tcea.subtract(c.tea()).abs()).isLessThan(new BigDecimal("0.0005"));
    }
}
