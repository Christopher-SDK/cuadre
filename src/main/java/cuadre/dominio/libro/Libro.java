package cuadre.dominio.libro;

import cuadre.dominio.cuenta.Cuenta;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.credito.Credito;
import cuadre.dominio.dinero.Moneda;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia, visto desde dentro de una transacción. */
public interface Libro {

    void abrir(Cuenta cuenta);

    /** @throws cuadre.dominio.OperacionRechazada CUENTA_INEXISTENTE */
    Cuenta cuenta(String id);

    /** Bloquea las cuentas hasta el fin de la transacción, siempre en el mismo orden para no provocar abrazos mortales. */
    Map<String, Cuenta> bloquear(Collection<String> ids);

    /** Guarda el asiento y aplica su efecto al saldo de cada cuenta. */
    void registrar(Asiento asiento);

    void guardar(Operacion operacion);

    Optional<Operacion> operacion(UUID id);

    /** Operación asociada a la clave, si la clave fue usada después de {@code vigenteDesde}. */
    Optional<Operacion> operacionPorClave(String clave, Instant vigenteDesde);

    /**
     * Reserva la clave para la operación. Devuelve {@code false} si ya está tomada por una operación vigente:
     * es la última barrera contra dos pedidos simultáneos con la misma clave.
     */
    boolean reservarClave(String clave, UUID operacion, Instant fecha, Instant vigenteDesde);

    void guardar(Credito credito);

    Optional<Credito> credito(UUID id);

    // Lecturas para auditoría

    List<Cuenta> cuentas();

    List<Credito> creditos();

    /** Saldo de cada cuenta recalculado desde cero a partir de todos los movimientos del libro. */
    Map<String, Long> saldosSegunMovimientos();

    /** Suma de debe menos haber de todo el libro, por moneda. En un libro sano es cero. */
    Map<Moneda, Long> descuadrePorMoneda();
}
