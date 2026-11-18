package cuadre.mutantes;

import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.cuenta.ServicioCuentas;
import cuadre.dominio.cuenta.SolicitudTransferencia;
import cuadre.dominio.falla.PuntoDeFalla;
import cuadre.dominio.impuesto.ReglaItf;
import cuadre.dominio.libro.Banco;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * TIMEOUT_A_MEDIAS. Para no retener el bloqueo de la cuenta origen mientras se acredita al destino,
 * la transferencia se partió en dos transacciones: primero el débito (origen → cuenta puente), luego el crédito.
 * Si el proceso cae entre ambas, el débito ya está confirmado y la clave de idempotencia aún no:
 * el dinero queda en la cuenta puente y el reintento del cliente vuelve a debitar.
 */
class TransferenciaEnDosTramos extends ServicioCuentas {

    TransferenciaEnDosTramos(Banco banco, ReglaItf itf, Clock reloj, Duration ventana, PuntoDeFalla falla) {
        super(banco, itf, reloj, ventana, falla);
    }

    @Override
    public Operacion transferir(SolicitudTransferencia solicitud) {
        validar(solicitud);
        Tramo tramo = banco.enTransaccion(libro -> {
            Optional<Operacion> previa = reintento(libro, solicitud);
            if (previa.isPresent()) {
                return new Tramo(previa.get(), null);
            }
            PlanTransferencia plan = planificar(libro, solicitud);
            libro.registrar(plan.debito());
            return new Tramo(null, plan);
        });
        if (tramo.previa() != null) {
            return tramo.previa();
        }
        falla.alcanzado(PuntoDeFalla.ENTRE_DEBITO_Y_CREDITO);
        return banco.enTransaccion(libro -> {
            libro.registrar(tramo.plan().credito());
            falla.alcanzado(PuntoDeFalla.ANTES_DE_CONFIRMAR);
            return cerrar(libro, tramo.plan());
        });
    }

    private record Tramo(Operacion previa, PlanTransferencia plan) {
    }
}
