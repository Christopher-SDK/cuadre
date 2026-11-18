package cuadre.estado;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.credito.CondicionesCredito;
import cuadre.dominio.credito.Credito;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.cuenta.SolicitudTransferencia;
import cuadre.dominio.falla.PuntoDeFalla.TiempoAgotado;
import cuadre.soporte.Sistema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static cuadre.soporte.Sistema.centimos;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El sistema real y el modelo de referencia, lado a lado. Cada comando se aplica a los dos;
 * después de cada paso deben coincidir en si aceptaron la operación y en cada saldo.
 */
final class Mundo {

    static final List<String> NOMBRES = List.of("A", "B", "C");
    static final long[] LIMITES = {0, 500_00, 0};
    static final BigDecimal TASA_ITF = new BigDecimal("0.005");

    final Sistema sistema;
    final ModeloDeReferencia modelo;
    final List<String> cuentas = new ArrayList<>();
    final List<UUID> operaciones = new ArrayList<>();
    final List<UUID> creditos = new ArrayList<>();
    SolicitudTransferencia ultimaTransferencia;
    private int pasos;

    Mundo(Sistema sistema) {
        this.sistema = sistema;
        this.modelo = new ModeloDeReferencia(LIMITES, TASA_ITF);
        for (long limite : LIMITES) {
            cuentas.add(sistema.abrir(limite));
        }
    }

    static Mundo enMemoria() {
        return new Mundo(new Sistema(TASA_ITF));
    }

    // ---------------------------------------------------------------- comandos

    void depositar(int c, long monto) {
        aplicar(() -> sistema.cuentas.depositar(cuentas.get(c), centimos(monto)), () -> modelo.depositar(c, monto));
    }

    void retirar(int c, long monto) {
        aplicar(() -> sistema.cuentas.retirar(cuentas.get(c), centimos(monto)), () -> modelo.retirar(c, monto));
    }

    void transferir(String clave, int o, int d, long monto) {
        ultimaTransferencia = new SolicitudTransferencia(clave, cuentas.get(o), cuentas.get(d), centimos(monto));
        SolicitudTransferencia pedido = ultimaTransferencia;
        aplicar(() -> sistema.cuentas.transferir(pedido), () -> modelo.transferir(clave, o, d, monto));
    }

    /** El servidor cae a mitad de la transferencia: el cliente recibe un timeout y nada debe haber cambiado. */
    void transferirConCaida(String clave, int o, int d, long monto, String punto) {
        ultimaTransferencia = new SolicitudTransferencia(clave, cuentas.get(o), cuentas.get(d), centimos(monto));
        sistema.reloj.avanzar(java.time.Duration.ofSeconds(1));
        pasos++;
        sistema.falla.fallarUnaVezEn(punto);
        try {
            sistema.cuentas.transferir(ultimaTransferencia);
        } catch (TiempoAgotado | OperacionRechazada esperado) {
            // el cliente no sabe si se hizo; lo sabrá al reintentar
        } finally {
            sistema.falla.desarmar();
        }
        verificar();
    }

    void reintentar(long segundosDeEspera) {
        sistema.reloj.avanzar(java.time.Duration.ofSeconds(segundosDeEspera));
        SolicitudTransferencia s = ultimaTransferencia;
        int o = cuentas.indexOf(s.origen()), d = cuentas.indexOf(s.destino());
        aplicar(() -> sistema.cuentas.transferir(s), () -> modelo.transferir(s.clave(), o, d, s.monto().centimos()));
    }

    void revertir(int indice) {
        UUID id = operaciones.get(indice);
        aplicar(() -> sistema.cuentas.revertir(id), () -> modelo.revertir(indice));
    }

    void desembolsar(int c, CondicionesCredito k) {
        aplicar(() -> {
                    Credito credito = sistema.creditos.desembolsar(cuentas.get(c), k);
                    creditos.add(credito.id());
                    return null;
                },
                () -> modelo.desembolsar(c, k.monto().centimos(), k.plazoMeses(), k.tea(), k.tasaDesgravamenMensual(),
                        k.comisionMensual().centimos(), k.comisionDesembolso().centimos()));
    }

    void pagarCuota(int credito) {
        aplicar(() -> sistema.creditos.pagarCuota(creditos.get(credito)), () -> modelo.pagarCuota(credito));
    }

    void prepagar(int credito, long monto) {
        aplicar(() -> sistema.creditos.prepagar(creditos.get(credito), centimos(monto)), () -> modelo.prepagar(credito, monto));
    }

    // ---------------------------------------------------------------- comparación

    private void aplicar(Supplier<Object> real, BooleanSupplier esperado) {
        sistema.reloj.avanzar(java.time.Duration.ofSeconds(1));
        pasos++;
        boolean aceptaModelo = esperado.getAsBoolean();
        String rechazo = null;
        Object resultado = null;
        try {
            resultado = real.get();
        } catch (OperacionRechazada e) {
            rechazo = e.motivo() + ": " + e.getMessage();
        }
        assertThat(rechazo == null)
                .as("paso %d: el modelo %s la operación y el sistema %s", pasos,
                        aceptaModelo ? "acepta" : "rechaza", rechazo == null ? "la aceptó" : "la rechazó (" + rechazo + ")")
                .isEqualTo(aceptaModelo);
        if (resultado instanceof Operacion op && !operaciones.contains(op.id())) {
            operaciones.add(op.id());
        } else if (resultado == null && rechazo == null && modelo.operaciones.size() > operaciones.size()) {
            operaciones.add(null); // desembolso: no se revierte, pero ocupa su lugar en la historia
        }
        verificar();
    }

    void verificar() {
        for (int i = 0; i < cuentas.size(); i++) {
            assertThat(sistema.saldo(cuentas.get(i))).as("paso %d: saldo de %s (céntimos)", pasos, NOMBRES.get(i))
                    .isEqualTo(modelo.saldo[i]);
        }
        assertThat(sistema.itfRecaudado()).as("paso %d: ITF recaudado (céntimos)", pasos).isEqualTo(modelo.itfRecaudado);
        for (int k = 0; k < creditos.size(); k++) {
            Credito real = sistema.creditos.credito(creditos.get(k));
            ModeloDeReferencia.Credito esperado = modelo.creditos.get(k);
            assertThat(real.saldoPendiente().centimos()).as("paso %d: saldo pendiente del crédito %d", pasos, k)
                    .isEqualTo(esperado.saldoPendiente());
            assertThat(real.pendientes().stream().map(q -> q.total().centimos()).toList())
                    .as("paso %d: cuotas pendientes del crédito %d", pasos, k)
                    .isEqualTo(esperado.pendientes.stream().map(f -> ModeloDeReferencia.centimos(f.total())).toList());
        }
    }

    @Override
    public String toString() {
        return "Mundo{cuentas=" + NOMBRES + ", pasos=" + pasos + "}";
    }
}
