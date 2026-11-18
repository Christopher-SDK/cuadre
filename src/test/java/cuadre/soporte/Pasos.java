package cuadre.soporte;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.cuenta.SolicitudTransferencia;
import cuadre.dominio.falla.PuntoDeFalla;
import cuadre.dominio.falla.PuntoDeFalla.TiempoAgotado;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Operaciones de clientes como datos, para que jqwik las genere, las combine y, si algo falla,
 * reduzca la lista a la secuencia mínima. Las cuentas se nombran A, B, C, D.
 */
public final class Pasos {

    public static final List<String> NOMBRES = List.of("A", "B", "C", "D");

    public sealed interface Paso {
    }

    public record Depositar(int cuenta, long centimos) implements Paso {
        public String toString() {
            return "depositar " + soles(centimos) + " en " + NOMBRES.get(cuenta);
        }
    }

    public record Retirar(int cuenta, long centimos) implements Paso {
        public String toString() {
            return "retirar " + soles(centimos) + " de " + NOMBRES.get(cuenta);
        }
    }

    public record Transferir(int origen, int destino, long centimos, int clave, boolean caidaEntreDebitoYCredito) implements Paso {
        public String toString() {
            return "transferir " + soles(centimos) + " de " + NOMBRES.get(origen) + " a " + NOMBRES.get(destino)
                    + " (clave k" + clave + ")" + (caidaEntreDebitoYCredito ? " y el servidor cae entre débito y crédito" : "");
        }
    }

    /** El cliente no recibió respuesta y reenvía la última transferencia con la misma clave tras una espera. */
    public record Reintentar(long segundosDeEspera) implements Paso {
        public String toString() {
            return "reintentar la última transferencia tras " + segundosDeEspera + " s";
        }
    }

    /** Revierte la operación número {@code indice} (módulo las que existan). */
    public record Revertir(int indice) implements Paso {
        public String toString() {
            return "revertir la operación #" + indice;
        }
    }

    static String soles(long centimos) {
        return String.format("%d.%02d", centimos / 100, Math.abs(centimos % 100));
    }

    // ---------------------------------------------------------------- generadores

    public static Arbitrary<Long> montos() {
        return Arbitraries.oneOf(
                Arbitraries.longs().between(1, 500_00),
                Arbitraries.longs().between(1_000_00, 25_000_00),
                Arbitraries.of(1_000_00L, 1_999_00L, 2_000_00L, 99_99L));
    }

    public static Arbitrary<Integer> cuenta() {
        return Arbitraries.integers().between(0, NOMBRES.size() - 1);
    }

    public static Arbitrary<Paso> deposito() {
        return Combinators.combine(cuenta(), montos()).as(Depositar::new);
    }

    public static Arbitrary<Paso> retiro() {
        return Combinators.combine(cuenta(), montos()).as(Retirar::new);
    }

    public static Arbitrary<Paso> transferencia(boolean conCaidas) {
        return Combinators.combine(cuenta(), cuenta(), montos(), Arbitraries.integers().between(0, 999),
                        conCaidas ? Arbitraries.of(false, false, false, true) : Arbitraries.just(false))
                .filter((o, d, m, k, c) -> !o.equals(d))
                .as(Transferir::new);
    }

    public static Arbitrary<Paso> reintento() {
        return Arbitraries.longs().between(1, 900).map(Reintentar::new);
    }

    public static Arbitrary<Paso> reverso() {
        return Arbitraries.integers().between(0, 30).map(Revertir::new);
    }

    /** Solo mueven dinero entre clientes y el impuesto: no entra ni sale efectivo. */
    public static Arbitrary<List<Paso>> internos() {
        return Arbitraries.frequencyOf(
                        Tuple(6, transferencia(true)), Tuple(2, reintento()), Tuple(3, reverso()))
                .list().ofMaxSize(40);
    }

    public static Arbitrary<List<Paso>> cualesquiera() {
        return Arbitraries.frequencyOf(
                        Tuple(3, deposito()), Tuple(3, retiro()), Tuple(6, transferencia(true)),
                        Tuple(2, reintento()), Tuple(3, reverso()))
                .list().ofMaxSize(40);
    }

    private static net.jqwik.api.Tuple.Tuple2<Integer, Arbitrary<? extends Paso>> Tuple(int peso, Arbitrary<? extends Paso> a) {
        return net.jqwik.api.Tuple.of(peso, a);
    }

    // ---------------------------------------------------------------- ejecución

    /** Ejecuta los pasos sobre el sistema. Los rechazos de negocio y los timeouts son resultados válidos. */
    public static final class Ejecutor {
        private final Sistema sistema;
        private final List<String> cuentas;
        private final List<Operacion> operaciones = new ArrayList<>();
        private SolicitudTransferencia ultima;

        public Ejecutor(Sistema sistema, List<String> cuentas) {
            this.sistema = sistema;
            this.cuentas = cuentas;
        }

        public void ejecutar(List<Paso> pasos) {
            pasos.forEach(this::ejecutar);
        }

        public void ejecutar(Paso paso) {
            sistema.reloj.avanzar(Duration.ofSeconds(1));
            try {
                switch (paso) {
                    case Depositar d -> operaciones.add(sistema.cuentas.depositar(cuentas.get(d.cuenta()), Sistema.centimos(d.centimos())));
                    case Retirar r -> operaciones.add(sistema.cuentas.retirar(cuentas.get(r.cuenta()), Sistema.centimos(r.centimos())));
                    case Transferir t -> {
                        ultima = new SolicitudTransferencia("k" + t.clave(), cuentas.get(t.origen()), cuentas.get(t.destino()),
                                Sistema.centimos(t.centimos()));
                        if (t.caidaEntreDebitoYCredito()) {
                            sistema.falla.fallarUnaVezEn(PuntoDeFalla.ENTRE_DEBITO_Y_CREDITO);
                        }
                        operaciones.add(sistema.cuentas.transferir(ultima));
                    }
                    case Reintentar r -> {
                        if (ultima != null) {
                            sistema.reloj.avanzar(Duration.ofSeconds(r.segundosDeEspera()));
                            operaciones.add(sistema.cuentas.transferir(ultima));
                        }
                    }
                    case Revertir r -> {
                        if (!operaciones.isEmpty()) {
                            operaciones.add(sistema.cuentas.revertir(operaciones.get(r.indice() % operaciones.size()).id()));
                        }
                    }
                }
            } catch (OperacionRechazada | TiempoAgotado esperado) {
                // el sistema dijo que no o el servidor cayó: el estado debe seguir siendo coherente
            } finally {
                sistema.falla.desarmar();
            }
        }
    }

    private Pasos() {
    }
}
