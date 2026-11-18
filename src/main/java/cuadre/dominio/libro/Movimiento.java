package cuadre.dominio.libro;

import cuadre.dominio.dinero.Dinero;

import java.util.Objects;

/** Una línea del asiento. El monto siempre es positivo; el sentido lo da el lado. */
public record Movimiento(String cuenta, Lado lado, Dinero monto) {

    public Movimiento {
        Objects.requireNonNull(cuenta, "cuenta");
        Objects.requireNonNull(lado, "lado");
        if (!monto.esPositivo()) {
            throw new IllegalArgumentException("Un movimiento siempre es positivo: " + monto);
        }
    }

    public static Movimiento debe(String cuenta, Dinero monto) {
        return new Movimiento(cuenta, Lado.DEBE, monto);
    }

    public static Movimiento haber(String cuenta, Dinero monto) {
        return new Movimiento(cuenta, Lado.HABER, monto);
    }

    /** Efecto sobre el saldo de la cuenta. Convención: saldo = haber − debe. */
    public long efectoEnSaldo() {
        return lado == Lado.HABER ? monto.centimos() : -monto.centimos();
    }

    public Movimiento contrapartida() {
        return new Movimiento(cuenta, lado == Lado.DEBE ? Lado.HABER : Lado.DEBE, monto);
    }
}
