package cuadre.concurrencia;

import io.qameta.allure.Epic;
import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.cuenta.SolicitudTransferencia;
import cuadre.soporte.Postgres;
import cuadre.soporte.Sistema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.springframework.dao.TransientDataAccessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static cuadre.soporte.Sistema.centimos;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Muchos clientes a la vez sobre las mismas cuentas, contra un PostgreSQL real.
 * Todos los hilos arrancan juntos (CountDownLatch) para maximizar el choque.
 * El resultado no depende del orden en que se intercalen: sin defectos, los invariantes se cumplen siempre.
 */
@Epic("4 · Concurrencia")
@DisplayName("Concurrencia sobre PostgreSQL")
class ConcurrenciaPruebas {

    static final int HILOS = 16;
    static final int TRANSFERENCIAS_POR_HILO = 40;
    static final long FONDOS_INICIALES = 1_500_00;

    @RepeatedTest(value = 5, name = "repetición {currentRepetition} de {totalRepetitions}")
    @DisplayName("16 clientes transfiriéndose dinero entre 3 cuentas al mismo tiempo: nada se pierde, nada se duplica")
    void transferenciasCruzadas(RepetitionInfo repeticion) throws Exception {
        Sistema s = new Sistema(Postgres.bancoVacio(), new BigDecimal("0.005"));
        List<String> cuentas = List.of(s.abrir(0), s.abrir(200_00), s.abrir(0));
        cuentas.forEach(c -> s.cuentas.depositar(c, centimos(FONDOS_INICIALES)));
        long dineroAntes = s.dineroInterno();
        long semilla = 20261118L + repeticion.getCurrentRepetition();
        AtomicInteger aceptadas = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();

        List<Throwable> inesperados = enParalelo(HILOS, hilo -> {
            Random azar = new Random(semilla * 31 + hilo);
            for (int i = 0; i < TRANSFERENCIAS_POR_HILO; i++) {
                int o = azar.nextInt(3), d = (o + 1 + azar.nextInt(2)) % 3;
                long monto = 1 + azar.nextInt(300_00);
                SolicitudTransferencia pedido = new SolicitudTransferencia("h" + hilo + "-" + i, cuentas.get(o), cuentas.get(d), centimos(monto));
                try {
                    s.cuentas.transferir(pedido);
                    aceptadas.incrementAndGet();
                } catch (OperacionRechazada sinFondos) {
                    // con saldo insuficiente el banco dice que no: es un resultado correcto
                } catch (TransientDataAccessException conflicto) {
                    // abrazo mortal o bloqueo agotado: la base deshizo la transacción entera; el cliente verá un error
                    conflictos.incrementAndGet();
                }
            }
        });

        System.out.printf("Semilla %d: %d transferencias aceptadas, %d conflictos transitorios (transacciones deshechas)%n",
                semilla, aceptadas.get(), conflictos.get());
        assertThat(inesperados).as("errores inesperados en los hilos (semilla %d)", semilla).isEmpty();
        assertThat(aceptadas.get()).as("debe haber transferencias aceptadas para que la prueba signifique algo").isGreaterThan(50);
        assertThat(s.auditar()).as("hallazgos del auditor (semilla %d)", semilla).isEmpty();
        assertThat(s.dineroInterno()).as("clientes + ITF (semilla %d)", semilla).isEqualTo(dineroAntes);
        assertThat(s.cuentasDeClientes()).allSatisfy(c ->
                assertThat(c.saldo().centimos()).as("saldo de %s", c.id()).isGreaterThanOrEqualTo(-c.limiteSobregiro().centimos()));
    }

    @RepeatedTest(value = 3, name = "repetición {currentRepetition} de {totalRepetitions}")
    @DisplayName("16 reintentos simultáneos de la misma transferencia con la misma clave debitan una sola vez")
    void reintentosSimultaneos() throws Exception {
        Sistema s = new Sistema(Postgres.bancoVacio(), new BigDecimal("0.005"));
        String a = s.abrir(0), b = s.abrir(0);
        s.cuentas.depositar(a, centimos(10_000_00));
        SolicitudTransferencia pedido = new SolicitudTransferencia("cuota-colegio-abril", a, b, centimos(850_00));
        Set<Object> respuestas = ConcurrentHashMap.newKeySet();

        List<Throwable> inesperados = enParalelo(HILOS, hilo -> {
            Operacion op = s.cuentas.transferir(pedido);
            respuestas.add(op.id());
        });

        assertThat(inesperados).isEmpty();
        assertThat(respuestas).as("todos los reintentos reciben la misma operación").hasSize(1);
        assertThat(s.saldo(b)).isEqualTo(850_00);
        assertThat(s.saldo(a)).isEqualTo(10_000_00 - 850_00 - 4_25);
        assertThat(s.auditar()).isEmpty();
    }

    interface Trabajo {
        void ejecutar(int hilo) throws Exception;
    }

    /** Lanza {@code n} hilos que esperan una misma señal de salida y devuelve las excepciones no esperadas. */
    static List<Throwable> enParalelo(int n, Trabajo trabajo) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch listos = new CountDownLatch(n);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int hilo = i;
            futuros.add(pool.submit(() -> {
                listos.countDown();
                salida.await();
                trabajo.ejecutar(hilo);
                return null;
            }));
        }
        listos.await();
        salida.countDown();
        List<Throwable> errores = new ArrayList<>();
        for (Future<?> f : futuros) {
            try {
                f.get(2, TimeUnit.MINUTES);
            } catch (java.util.concurrent.ExecutionException e) {
                errores.add(e.getCause());
            }
        }
        pool.shutdown();
        return errores;
    }
}
