package cuadre.credito;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.credito.CalculadoraFrancesa;
import cuadre.dominio.credito.CalculadoraTcea;
import cuadre.dominio.credito.CondicionesCredito;
import cuadre.dominio.dinero.Dinero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static cuadre.dominio.OperacionRechazada.Motivo.CONDICIONES_INVALIDAS;
import static cuadre.dominio.dinero.Moneda.PEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dos hallazgos de las pruebas de propiedades, fijados como casos concretos. */
@DisplayName("Crédito: casos encontrados por las propiedades")
class TceaFrenteATeaTest {

    final CalculadoraFrancesa calculadora = new CalculadoraFrancesa(RoundingMode.HALF_UP);

    @Test
    @DisplayName("«TCEA ≥ TEA si hay costos» es falso: con costos de céntimos, el redondeo de la cuota pesa más")
    void tceaPuedeQuedarBajoLaTea() {
        Dinero monto = Dinero.de("502.04", PEN);
        BigDecimal tea = new BigDecimal("0.0935");
        CondicionesCredito conSeguro = new CondicionesCredito(monto, 18, tea, new BigDecimal("0.00001"),
                Dinero.cero(PEN), Dinero.cero(PEN));
        CondicionesCredito sinCostos = CondicionesCredito.sinCostos(monto, 18, tea);

        BigDecimal tceaConSeguro = CalculadoraTcea.tcea(conSeguro, calculadora.calcular(conSeguro));
        BigDecimal tceaSinCostos = CalculadoraTcea.tcea(sinCostos, calculadora.calcular(sinCostos));

        assertThat(tceaConSeguro).as("TCEA con seguro").isLessThan(tea);
        assertThat(tceaConSeguro).as("pero el seguro sí encarece el crédito").isGreaterThan(tceaSinCostos);
    }

    @Test
    @DisplayName("S/ 500 a 330 meses sin interés se rechaza: la cuota redondeada (1,52) pagaría la deuda antes de la última cuota")
    void condicionesNoViables() {
        CondicionesCredito c = CondicionesCredito.sinCostos(Dinero.de("500.00", PEN), 330, BigDecimal.ZERO);
        assertThatThrownBy(() -> calculadora.calcular(c))
                .isInstanceOfSatisfying(OperacionRechazada.class, e -> assertThat(e.motivo()).isEqualTo(CONDICIONES_INVALIDAS));
    }
}
