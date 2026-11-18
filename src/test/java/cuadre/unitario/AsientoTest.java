package cuadre.unitario;

import io.qameta.allure.Epic;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.libro.Asiento;
import cuadre.dominio.libro.Asiento.AsientoDescuadrado;
import cuadre.dominio.libro.Movimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cuadre.dominio.dinero.Moneda.PEN;
import static cuadre.dominio.dinero.Moneda.USD;
import static cuadre.dominio.libro.Movimiento.debe;
import static cuadre.dominio.libro.Movimiento.haber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("1 · Unitarias")
@DisplayName("Partida doble")
class AsientoTest {

    static Dinero soles(String monto) {
        return Dinero.de(monto, PEN);
    }

    @Test
    @DisplayName("Un asiento cuyo debe iguala al haber se acepta")
    void asientoCuadrado() {
        Asiento a = Asiento.nuevo("transferencia", List.of(
                debe("A", soles("100")), debe("A", soles("0.05")),
                haber("B", soles("100")), haber("ITF", soles("0.05"))));
        assertThat(a.movimientos()).hasSize(4);
        assertThat(a.movimientos().stream().mapToLong(Movimiento::efectoEnSaldo).sum()).isZero();
    }

    @Test
    @DisplayName("Si falta un céntimo en el haber, el asiento no se puede crear")
    void asientoDescuadrado() {
        assertThatThrownBy(() -> Asiento.nuevo("x", List.of(debe("A", soles("100.00")), haber("B", soles("99.99")))))
                .isInstanceOf(AsientoDescuadrado.class)
                .hasMessageContaining("1 céntimos");
    }

    @Test
    @DisplayName("Un asiento no puede mezclar soles y dólares aunque los números coincidan")
    void asientoMonedasMezcladas() {
        assertThatThrownBy(() -> Asiento.nuevo("x", List.of(debe("A", soles("1")), haber("B", Dinero.de("1", USD)))))
                .isInstanceOf(AsientoDescuadrado.class);
    }

    @Test
    @DisplayName("Un asiento con un solo movimiento no existe")
    void unSoloMovimiento() {
        assertThatThrownBy(() -> Asiento.nuevo("x", List.of(debe("A", soles("1")))))
                .isInstanceOf(AsientoDescuadrado.class);
    }

    @Test
    @DisplayName("Un movimiento de cero o negativo no existe: el sentido lo da el lado")
    void movimientoNoPositivo() {
        assertThatThrownBy(() -> debe("A", soles("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> haber("A", soles("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Las contrapartidas de un asiento anulan exactamente su efecto")
    void contrapartidas() {
        Asiento a = Asiento.nuevo("x", List.of(debe("A", soles("10")), haber("B", soles("10"))));
        Asiento inverso = Asiento.nuevo("reverso", a.contrapartidas());
        assertThat(inverso.movimientos()).containsExactly(haber("A", soles("10")), debe("B", soles("10")));
    }
}
