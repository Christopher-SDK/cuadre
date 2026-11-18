package cuadre.dominio.libro;

import java.util.function.Function;

/** Puerto de persistencia: todo lo que ocurre dentro de {@code trabajo} se confirma junto o no ocurre. */
public interface Banco {

    <T> T enTransaccion(Function<Libro, T> trabajo);

    /** Lectura fuera de una operación de negocio (auditoría, consultas). */
    default <T> T leer(Function<Libro, T> lectura) {
        return enTransaccion(lectura);
    }
}
