package cuadre.unitario;

import io.qameta.allure.Epic;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.impuesto.ReglaItf;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static cuadre.dominio.dinero.Moneda.PEN;
import static cuadre.dominio.impuesto.ReglaItf.Redondeo.LEY_PERUANA;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("1 · Unitarias")
@DisplayName("ITF: regla de redondeo")
class ReglaItfTest {

    static final ReglaItf REAL = new ReglaItf(new BigDecimal("0.00005"), LEY_PERUANA);

    @ParameterizedTest(name = "S/ {0} paga S/ {1} de ITF")
    @CsvSource({
            "999.99, 0.00",    // 0,0499995 → el segundo decimal 4 se ajusta a 0
            "1000.00, 0.05",   // 0,05
            "1999.00, 0.05",   // 0,09995 → 0,09 → 0,05
            "2000.00, 0.10",
            "3500.00, 0.15",   // 0,175 → 0,17 → 0,15
            "19999.99, 0.95",  // 0,9999995 → 0,99 → 0,95
            "100000.00, 5.00"})
    @DisplayName("Ejemplos calculados a mano con el art. 13 del TUO de la Ley 28194")
    void ejemplosDeLey(String monto, String esperado) {
        assertThat(REAL.sobre(Dinero.de(monto, PEN))).isEqualTo(Dinero.de(esperado, PEN));
    }

    @Property
    @Label("El ITF con la regla peruana es múltiplo de 5 céntimos y nunca supera el impuesto exacto en más de 0")
    void multiploDeCincoYNuncaDeMas(@ForAll @LongRange(min = 1, max = 10_000_000_00L) long centimos) {
        Dinero monto = Dinero.centimos(centimos, PEN);
        long itf = REAL.sobre(monto).centimos();
        BigDecimal exacto = monto.aDecimal().multiply(REAL.tasa()).movePointRight(2);

        assertThat(itf % 5).isZero();
        assertThat(BigDecimal.valueOf(itf)).isLessThanOrEqualTo(exacto);
        assertThat(exacto.subtract(BigDecimal.valueOf(itf))).isLessThan(BigDecimal.valueOf(5));
    }
}
