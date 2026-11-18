package cuadre.dominio.cuenta;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;
import cuadre.dominio.dinero.Dinero;

public record SolicitudTransferencia(String clave, String origen, String destino, Dinero monto) {

    public SolicitudTransferencia {
        if (clave == null || clave.isBlank() || clave.length() > 100) {
            throw new OperacionRechazada(Motivo.CONDICIONES_INVALIDAS, "La clave de idempotencia es obligatoria (1 a 100 caracteres)");
        }
    }

    /** Lo que identifica el pedido para el negocio; si un reintento trae otra huella, no es un reintento. */
    public String huella() {
        return origen + "→" + destino + ":" + monto;
    }
}
