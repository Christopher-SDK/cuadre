package cuadre.dominio.libro;

import cuadre.dominio.dinero.Moneda;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Asiento de partida doble. No se puede construir uno que no cuadre:
 * un asiento descuadrado es un defecto del sistema, no un error del cliente.
 */
public record Asiento(UUID id, String concepto, List<Movimiento> movimientos) {

    public Asiento {
        Objects.requireNonNull(id, "id");
        movimientos = List.copyOf(movimientos);
        if (movimientos.size() < 2) {
            throw new AsientoDescuadrado("Un asiento necesita al menos dos movimientos: " + concepto);
        }
        Moneda moneda = movimientos.getFirst().monto().moneda();
        long saldo = 0;
        for (Movimiento m : movimientos) {
            if (m.monto().moneda() != moneda) {
                throw new AsientoDescuadrado("Un asiento no mezcla monedas: " + concepto);
            }
            saldo = Math.addExact(saldo, m.efectoEnSaldo());
        }
        if (saldo != 0) {
            throw new AsientoDescuadrado("Debe y haber no coinciden (" + saldo + " céntimos): " + concepto);
        }
    }

    public static Asiento nuevo(String concepto, List<Movimiento> movimientos) {
        return new Asiento(UUID.randomUUID(), concepto, movimientos);
    }

    public Moneda moneda() {
        return movimientos.getFirst().monto().moneda();
    }

    /** El asiento que deja todo como estaba: mismos movimientos con el lado invertido. */
    public List<Movimiento> contrapartidas() {
        List<Movimiento> inversos = new ArrayList<>(movimientos.size());
        for (Movimiento m : movimientos) {
            inversos.add(m.contrapartida());
        }
        return inversos;
    }

    public static class AsientoDescuadrado extends IllegalStateException {
        public AsientoDescuadrado(String mensaje) {
            super(mensaje);
        }
    }
}
