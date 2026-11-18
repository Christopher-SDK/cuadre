package cuadre.dominio.falla;

/**
 * Lugar donde, en producción, puede ocurrir un timeout o una caída (red, base de datos, proceso).
 * Las pruebas lo usan para provocar la falla exactamente ahí.
 */
@FunctionalInterface
public interface PuntoDeFalla {

    String ENTRE_DEBITO_Y_CREDITO = "transferencia: entre débito y crédito";
    String ANTES_DE_CONFIRMAR = "transferencia: antes de confirmar";

    PuntoDeFalla NINGUNO = punto -> {
    };

    void alcanzado(String punto);

    class TiempoAgotado extends RuntimeException {
        public TiempoAgotado(String punto) {
            super("Tiempo agotado en " + punto);
        }
    }
}
