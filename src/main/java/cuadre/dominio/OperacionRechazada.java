package cuadre.dominio;

/** Regla de negocio que impide la operación. El sistema queda exactamente como estaba. */
public class OperacionRechazada extends RuntimeException {

    public enum Motivo {
        MONTO_INVALIDO,
        MONEDA_INCOMPATIBLE,
        CUENTA_INEXISTENTE,
        MISMA_CUENTA,
        SALDO_INSUFICIENTE,
        CLAVE_REUTILIZADA,
        OPERACION_INEXISTENTE,
        NO_REVERSIBLE,
        YA_REVERTIDA,
        CREDITO_INEXISTENTE,
        CREDITO_CANCELADO,
        CONDICIONES_INVALIDAS
    }

    private final Motivo motivo;

    public OperacionRechazada(Motivo motivo, String detalle) {
        super(detalle);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
