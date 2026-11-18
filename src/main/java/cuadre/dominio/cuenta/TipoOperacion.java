package cuadre.dominio.cuenta;

public enum TipoOperacion {
    DEPOSITO,
    RETIRO,
    TRANSFERENCIA,
    REVERSO,
    DESEMBOLSO,
    PAGO_CUOTA,
    PREPAGO;

    public boolean esReversible() {
        return this == DEPOSITO || this == RETIRO || this == TRANSFERENCIA;
    }
}
