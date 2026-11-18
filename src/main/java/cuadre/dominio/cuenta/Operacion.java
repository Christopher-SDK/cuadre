package cuadre.dominio.cuenta;

import cuadre.dominio.libro.Asiento;
import cuadre.dominio.libro.Movimiento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Hecho de negocio y sus asientos contables.
 *
 * @param clave    clave de idempotencia (solo transferencias)
 * @param huella   contenido de negocio de la solicitud: misma clave con otra huella es un conflicto
 * @param revierte operación que este reverso anula
 */
public record Operacion(UUID id, TipoOperacion tipo, String clave, String huella, Instant fecha,
                        List<Asiento> asientos, UUID revierte, boolean revertida) {

    public Operacion {
        asientos = List.copyOf(asientos);
    }

    public static Operacion nueva(TipoOperacion tipo, Instant fecha, List<Asiento> asientos) {
        return new Operacion(UUID.randomUUID(), tipo, null, null, fecha, asientos, null, false);
    }

    public List<Movimiento> movimientos() {
        return asientos.stream().flatMap(a -> a.movimientos().stream()).toList();
    }

    public Operacion marcadaRevertida() {
        return new Operacion(id, tipo, clave, huella, fecha, asientos, revierte, true);
    }
}
