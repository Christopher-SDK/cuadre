package cuadre.unitario;

import io.qameta.allure.Epic;
import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.dinero.Dinero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static cuadre.dominio.OperacionRechazada.Motivo.MONEDA_INCOMPATIBLE;
import static cuadre.dominio.OperacionRechazada.Motivo.MONTO_INVALIDO;
import static cuadre.dominio.dinero.Moneda.PEN;
import static cuadre.dominio.dinero.Moneda.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("1 · Unitarias")
@DisplayName("Dinero")
class DineroTest {

    @ParameterizedTest(name = "\"{0}\" son {1} céntimos")
    @CsvSource({"150, 15000", "150.5, 15050", "150.50, 15050", "0.01, 1", "-3.20, -320", "1000000.00, 100000000"})
    @DisplayName("Un monto escrito como texto se guarda en céntimos exactos")
    void montoDesdeTexto(String texto, long centimos) {
        assertThat(Dinero.de(texto, PEN).centimos()).isEqualTo(centimos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.001", "0.005", "abc", "", "1e400"})
    @DisplayName("Un monto con más de dos decimales o ilegible se rechaza, nunca se redondea en silencio")
    void montoInvalido(String texto) {
        assertThatThrownBy(() -> Dinero.de(texto, PEN))
                .isInstanceOfSatisfying(OperacionRechazada.class, e -> assertThat(e.motivo()).isEqualTo(MONTO_INVALIDO));
    }

    @Test
    @DisplayName("0,10 + 0,20 es exactamente 0,30 (con double sería 0,30000000000000004)")
    void sinErroresDeComaFlotante() {
        assertThat(Dinero.de("0.10", PEN).sumar(Dinero.de("0.20", PEN))).isEqualTo(Dinero.de("0.30", PEN));
    }

    @Test
    @DisplayName("Sumar soles con dólares está prohibido")
    void noMezclaMonedas() {
        assertThatThrownBy(() -> Dinero.de("1", PEN).sumar(Dinero.de("1", USD)))
                .isInstanceOfSatisfying(OperacionRechazada.class, e -> assertThat(e.motivo()).isEqualTo(MONEDA_INCOMPATIBLE));
        assertThatThrownBy(() -> Dinero.de("1", PEN).compareTo(Dinero.de("1", USD)))
                .isInstanceOf(OperacionRechazada.class);
    }

    @ParameterizedTest(name = "{0} × {1} con {2} = {3}")
    @CsvSource({
            "100.00, 0.005, HALF_UP, 0.50",
            "100.01, 0.005, HALF_UP, 0.50",
            "100.10, 0.005, HALF_UP, 0.50",
            "100.90, 0.005, HALF_UP, 0.50",
            "101.00, 0.005, HALF_UP, 0.51",
            "100.90, 0.005, DOWN, 0.50",
            "-1.00, 0.005, HALF_UP, -0.01"})
    @DisplayName("Multiplicar exige una regla de redondeo explícita")
    void multiplicarConRedondeo(String monto, String factor, RoundingMode modo, String esperado) {
        assertThat(Dinero.de(monto, PEN).multiplicar(new BigDecimal(factor), modo)).isEqualTo(Dinero.de(esperado, PEN));
    }

    @Test
    @DisplayName("Se muestra con moneda y dos decimales")
    void formato() {
        assertThat(Dinero.de("1500", PEN)).hasToString("PEN 1500.00");
        assertThat(Dinero.centimos(-5, USD)).hasToString("USD -0.05");
    }

    @Test
    @DisplayName("Un desborde aritmético falla en voz alta, no da la vuelta")
    void desborde() {
        assertThatThrownBy(() -> Dinero.centimos(Long.MAX_VALUE, PEN).sumar(Dinero.centimos(1, PEN)))
                .isInstanceOf(ArithmeticException.class);
    }
}
