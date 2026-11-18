package cuadre.estado;

import io.qameta.allure.Epic;
import cuadre.dominio.credito.CondicionesCredito;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.falla.PuntoDeFalla;
import cuadre.soporte.Postgres;
import cuadre.soporte.Sistema;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;

import java.math.BigDecimal;

import static cuadre.dominio.dinero.Moneda.PEN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas basadas en modelo: jqwik genera secuencias aleatorias de comandos de clientes y, después de cada paso,
 * compara el sistema real contra el modelo de referencia. Si discrepan, jqwik reduce la secuencia
 * a la más corta que todavía falla y la imprime junto con la semilla.
 */
@Epic("3 · Modelo de referencia")
@Label("Modelo de referencia: el sistema se comporta igual que el modelo en cualquier secuencia")
class ModeloPropiedades {

    @Property(tries = 400)
    @Label("En memoria: cualquier secuencia de depósitos, retiros, transferencias, caídas, reintentos, reversos y pagos de crédito")
    void secuenciasEnMemoria(@ForAll("enMemoria") ActionChain<Mundo> cadena) {
        cadena.withInvariant("el auditor no encuentra descuadres", m -> assertThat(m.sistema.auditar()).isEmpty()).run();
    }

    /** Sin reducción: cada ejecución contra la base cuesta; la versión en memoria ya entrega la secuencia mínima. */
    @Property(tries = 40, shrinking = ShrinkingMode.OFF)
    @Label("En PostgreSQL: las mismas secuencias contra la base de datos real")
    void secuenciasEnPostgres(@ForAll("enPostgres") ActionChain<Mundo> cadena) {
        cadena.withInvariant("el auditor no encuentra descuadres", m -> assertThat(m.sistema.auditar()).isEmpty()).run();
    }

    @Provide
    Arbitrary<ActionChain<Mundo>> enMemoria() {
        return cadena(ActionChain.startWith(Mundo::enMemoria));
    }

    @Provide
    Arbitrary<ActionChain<Mundo>> enPostgres() {
        return cadena(ActionChain.startWith(() -> new Mundo(new Sistema(Postgres.bancoVacio(), Mundo.TASA_ITF))));
    }

    private static Arbitrary<ActionChain<Mundo>> cadena(net.jqwik.api.state.ActionChainArbitrary<Mundo> inicio) {
        return inicio
                .withAction(3, new Depositar())
                .withAction(2, new Retirar())
                .withAction(5, new Transferir())
                .withAction(1, new TransferirConCaida())
                .withAction(2, new Reintentar())
                .withAction(2, new Revertir())
                .withAction(1, new Desembolsar())
                .withAction(3, new PagarCuota())
                .withAction(1, new Prepagar())
                .withMaxTransformations(30);
    }

    // ---------------------------------------------------------------- generadores comunes

    static Arbitrary<Integer> cuenta() {
        return Arbitraries.integers().between(0, Mundo.NOMBRES.size() - 1);
    }

    static Arbitrary<Long> monto() {
        return Arbitraries.oneOf(Arbitraries.longs().between(1, 800_00), Arbitraries.longs().between(800_00, 5_000_00));
    }

    static String soles(long centimos) {
        return Dinero.centimos(centimos, PEN).aDecimal().toPlainString();
    }

    // ---------------------------------------------------------------- comandos

    static class Depositar implements Action.Independent<Mundo> {
        public Arbitrary<Transformer<Mundo>> transformer() {
            return Combinators.combine(cuenta(), monto()).as((c, m) ->
                    Transformer.mutate("depositar " + soles(m) + " en " + Mundo.NOMBRES.get(c), w -> w.depositar(c, m)));
        }
    }

    static class Retirar implements Action.Independent<Mundo> {
        public Arbitrary<Transformer<Mundo>> transformer() {
            return Combinators.combine(cuenta(), monto()).as((c, m) ->
                    Transformer.mutate("retirar " + soles(m) + " de " + Mundo.NOMBRES.get(c), w -> w.retirar(c, m)));
        }
    }

    static class Transferir implements Action.Independent<Mundo> {
        public Arbitrary<Transformer<Mundo>> transformer() {
            return Combinators.combine(cuenta(), cuenta(), monto(), Arbitraries.integers().between(1, 99))
                    .filter((o, d, m, k) -> !o.equals(d))
                    .as((o, d, m, k) -> Transformer.mutate(
                            "transferir " + soles(m) + " de " + Mundo.NOMBRES.get(o) + " a " + Mundo.NOMBRES.get(d) + " con clave k" + k,
                            w -> w.transferir("k" + k, o, d, m)));
        }
    }

    static class TransferirConCaida implements Action.Independent<Mundo> {
        public Arbitrary<Transformer<Mundo>> transformer() {
            return Combinators.combine(cuenta(), cuenta(), monto(), Arbitraries.integers().between(100, 199),
                            Arbitraries.of(PuntoDeFalla.ENTRE_DEBITO_Y_CREDITO, PuntoDeFalla.ANTES_DE_CONFIRMAR))
                    .filter((o, d, m, k, p) -> !o.equals(d))
                    .as((o, d, m, k, p) -> Transformer.mutate(
                            "transferir " + soles(m) + " de " + Mundo.NOMBRES.get(o) + " a " + Mundo.NOMBRES.get(d)
                                    + " con clave k" + k + ", pero el servidor cae (" + p + ")",
                            w -> w.transferirConCaida("k" + k, o, d, m, p)));
        }
    }

    static class Reintentar implements Action.Dependent<Mundo> {
        public boolean precondition(Mundo w) {
            return w.ultimaTransferencia != null;
        }

        public Arbitrary<Transformer<Mundo>> transformer(Mundo w) {
            return Arbitraries.longs().between(1, 600).map(s ->
                    Transformer.mutate("reintentar la última transferencia tras " + s + " s", x -> x.reintentar(s)));
        }
    }

    static class Revertir implements Action.Dependent<Mundo> {
        public boolean precondition(Mundo w) {
            return !w.operaciones.isEmpty();
        }

        public Arbitrary<Transformer<Mundo>> transformer(Mundo w) {
            return Arbitraries.integers().between(0, w.operaciones.size() - 1).map(i ->
                    Transformer.mutate("revertir la operación #" + (i + 1), x -> x.revertir(i)));
        }
    }

    static class Desembolsar implements Action.Independent<Mundo> {
        public Arbitrary<Transformer<Mundo>> transformer() {
            Arbitrary<Long> monto = Arbitraries.longs().between(1_000_00, 20_000_00);
            Arbitrary<Integer> plazo = Arbitraries.integers().between(1, 6);
            Arbitrary<BigDecimal> tea = Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("0.80")).ofScale(4);
            Arbitrary<BigDecimal> desgravamen = Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("0.0008")).ofScale(5);
            Arbitrary<Long> comision = Arbitraries.longs().between(0, 10_00);
            Arbitrary<Long> comisionDesembolso = Arbitraries.longs().between(0, 100_00);
            return Combinators.combine(cuenta(), monto, plazo, tea, desgravamen, comision, comisionDesembolso)
                    .as((c, m, n, t, d, cm, cd) -> {
                        CondicionesCredito k = new CondicionesCredito(Dinero.centimos(m, PEN), n, t, d,
                                Dinero.centimos(cm, PEN), Dinero.centimos(cd, PEN));
                        return Transformer.mutate("desembolsar a " + Mundo.NOMBRES.get(c) + " un crédito de " + soles(m) + " a "
                                + n + " meses, TEA " + t + ", desgravamen " + d + ", comisión " + soles(cm) + "/mes y "
                                + soles(cd) + " al desembolso", w -> w.desembolsar(c, k));
                    });
        }
    }

    static class PagarCuota implements Action.Dependent<Mundo> {
        public boolean precondition(Mundo w) {
            return !w.creditos.isEmpty();
        }

        public Arbitrary<Transformer<Mundo>> transformer(Mundo w) {
            return Arbitraries.integers().between(0, w.creditos.size() - 1).map(k ->
                    Transformer.mutate("pagar la cuota del crédito #" + (k + 1), x -> x.pagarCuota(k)));
        }
    }

    static class Prepagar implements Action.Dependent<Mundo> {
        public boolean precondition(Mundo w) {
            return !w.creditos.isEmpty();
        }

        public Arbitrary<Transformer<Mundo>> transformer(Mundo w) {
            return Combinators.combine(Arbitraries.integers().between(0, w.creditos.size() - 1), Arbitraries.longs().between(1, 8_000_00))
                    .as((k, m) -> Transformer.mutate("prepagar " + soles(m) + " del crédito #" + (k + 1), x -> x.prepagar(k, m)));
        }
    }
}
