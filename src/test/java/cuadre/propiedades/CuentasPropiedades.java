package cuadre.propiedades;

import io.qameta.allure.Epic;
import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.cuenta.SolicitudTransferencia;
import cuadre.dominio.impuesto.ReglaItf;
import cuadre.soporte.Pasos;
import cuadre.soporte.Pasos.Ejecutor;
import cuadre.soporte.Pasos.Paso;
import cuadre.soporte.Sistema;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static cuadre.soporte.Sistema.centimos;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariantes de las cuentas. Cada propiedad se prueba con cientos de escenarios generados;
 * si una falla, jqwik imprime la semilla y la secuencia mínima que la rompe.
 */
@Epic("2 · Invariantes")
@Label("Cuentas: invariantes")
class CuentasPropiedades {

    /** Tasa real (0,005 %) y tasas altas para que el impuesto aparezca también en montos pequeños. */
    @Provide
    Arbitrary<BigDecimal> tasaItf() {
        return Arbitraries.of(new BigDecimal("0.00005"), new BigDecimal("0.0005"), new BigDecimal("0.005"), new BigDecimal("0.01"));
    }

    @Provide
    Arbitrary<List<Paso>> internos() {
        return Pasos.internos();
    }

    @Provide
    Arbitrary<List<Paso>> cualesquiera() {
        return Pasos.cualesquiera();
    }

    /** Cuatro cuentas con fondos iniciales y límites de sobregiro variados. */
    static List<String> escenario(Sistema s, List<Long> fondos, List<Long> limites) {
        List<String> ids = IntStream.range(0, Pasos.NOMBRES.size()).mapToObj(i -> s.abrir(limites.get(i))).toList();
        for (int i = 0; i < ids.size(); i++) {
            if (fondos.get(i) > 0) {
                s.cuentas.depositar(ids.get(i), centimos(fondos.get(i)));
            }
        }
        return ids;
    }

    @Property(tries = 300)
    @Label("Conservación: entre clientes el dinero no se crea ni se destruye (lo que falta está en el ITF recaudado)")
    void conservacion(@ForAll("tasaItf") BigDecimal tasa,
                      @ForAll @Size(4) List<@LongRange(min = 0, max = 30_000_00) Long> fondos,
                      @ForAll @Size(4) List<@LongRange(min = 0, max = 1_000_00) Long> limites,
                      @ForAll("internos") List<Paso> pasos) {
        Sistema s = new Sistema(tasa);
        List<String> cuentas = escenario(s, fondos, limites);
        long antes = s.dineroInterno();

        new Ejecutor(s, cuentas).ejecutar(pasos);

        assertThat(s.dineroInterno())
                .as("clientes + ITF recaudado (en céntimos)")
                .isEqualTo(antes);
    }

    @Property(tries = 300)
    @Label("El libro mayor siempre cuadra: debe = haber, cada saldo = sus movimientos y la cuenta puente queda en cero")
    void elLibroCuadra(@ForAll("tasaItf") BigDecimal tasa,
                       @ForAll @Size(4) List<@LongRange(min = 0, max = 1_000_00) Long> limites,
                       @ForAll("cualesquiera") List<Paso> pasos) {
        Sistema s = new Sistema(tasa);
        new Ejecutor(s, escenario(s, List.of(0L, 0L, 0L, 0L), limites)).ejecutar(pasos);

        assertThat(s.auditar()).as("hallazgos del auditor").isEmpty();
    }

    @Property(tries = 300)
    @Label("Ninguna cuenta queda por debajo de su límite de sobregiro, haga lo que haga el cliente")
    void nuncaBajoElLimite(@ForAll("tasaItf") BigDecimal tasa,
                           @ForAll @Size(4) List<@LongRange(min = 0, max = 1_000_00) Long> limites,
                           @ForAll("cualesquiera") List<Paso> pasos) {
        Sistema s = new Sistema(tasa);
        new Ejecutor(s, escenario(s, List.of(0L, 0L, 0L, 0L), limites)).ejecutar(pasos);

        assertThat(s.cuentasDeClientes()).allSatisfy(c ->
                assertThat(c.saldo().centimos()).as("saldo de " + c.id()).isGreaterThanOrEqualTo(-c.limiteSobregiro().centimos()));
    }

    @Property(tries = 300)
    @Label("Idempotencia: reenviar N veces la misma transferencia con la misma clave equivale a enviarla una vez")
    void idempotencia(@ForAll("tasaItf") BigDecimal tasa,
                      @ForAll @LongRange(min = 1, max = 20_000_00) long monto,
                      @ForAll @Size(min = 1, max = 6) List<@LongRange(min = 1, max = 3_600) Long> esperasEnSegundos) {
        Sistema s = new Sistema(tasa);
        String a = s.abrir(0), b = s.abrir(0);
        s.cuentas.depositar(a, centimos(40_000_00));
        SolicitudTransferencia pedido = new SolicitudTransferencia("pago-alquiler-marzo", a, b, centimos(monto));

        Operacion primera = s.cuentas.transferir(pedido);
        Map<String, Long> trasUnaVez = s.foto();
        for (long espera : esperasEnSegundos) {
            s.reloj.avanzar(Duration.ofSeconds(espera));
            Operacion reintento = s.cuentas.transferir(pedido);
            assertThat(reintento.id()).as("el reintento devuelve la operación original").isEqualTo(primera.id());
        }

        assertThat(s.foto()).as("saldos tras %d reintentos", esperasEnSegundos.size()).isEqualTo(trasUnaVez);
    }

    @Property(tries = 300)
    @Label("Reverso: una operación seguida de su reverso deja todo exactamente como estaba, impuesto incluido")
    void reversoDejaTodoComoEstaba(@ForAll("tasaItf") BigDecimal tasa,
                                   @ForAll @Size(4) List<@LongRange(min = 0, max = 30_000_00) Long> fondos,
                                   @ForAll("cualesquiera") List<Paso> historia,
                                   @ForAll @IntRange(min = 0, max = 2) int tipo,
                                   @ForAll @LongRange(min = 1, max = 25_000_00) long monto) {
        Sistema s = new Sistema(tasa);
        List<String> c = escenario(s, fondos, List.of(0L, 0L, 0L, 0L));
        new Ejecutor(s, c).ejecutar(historia);
        Map<String, Long> antes = s.foto();

        Operacion op;
        try {
            op = switch (tipo) {
                case 0 -> s.cuentas.depositar(c.get(0), centimos(monto));
                case 1 -> s.cuentas.retirar(c.get(0), centimos(monto));
                default -> s.cuentas.transferir(new SolicitudTransferencia("rev", c.get(0), c.get(1), centimos(monto)));
            };
        } catch (OperacionRechazada sinFondos) {
            return;
        }
        s.cuentas.revertir(op.id());

        assertThat(s.foto()).isEqualTo(antes);
    }

    @Property(tries = 500)
    @Label("Una transferencia mueve exactamente el monto y cobra el ITF una sola vez, al que envía")
    void itfUnaSolaVez(@ForAll("tasaItf") BigDecimal tasa,
                       @ForAll @LongRange(min = 1, max = 25_000_00) long monto) {
        Sistema s = new Sistema(tasa);
        String a = s.abrir(0), b = s.abrir(0);
        s.cuentas.depositar(a, centimos(30_000_00));
        long antesA = s.saldo(a), antesB = s.saldo(b), antesItf = s.itfRecaudado();
        long esperado = new ReglaItf(tasa, s.parametros.redondeoItf()).sobre(centimos(monto)).centimos();

        s.cuentas.transferir(new SolicitudTransferencia("t", a, b, centimos(monto)));

        assertThat(s.saldo(b) - antesB).as("lo que recibe el destino").isEqualTo(monto);
        assertThat(s.itfRecaudado() - antesItf).as("ITF recaudado").isEqualTo(esperado);
        assertThat(antesA - s.saldo(a)).as("lo que paga el origen").isEqualTo(monto + esperado);
    }
}
