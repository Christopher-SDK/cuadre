package cuadre.mutantes;

import java.util.Arrays;
import java.util.Locale;

/**
 * Defectos sembrados a propósito para medir si la suite los detecta.
 * Se activan con la variable de entorno {@code CUADRE_BUG} o la propiedad {@code -Dcuadre.bug}.
 * Sin ninguna de las dos, el sistema está limpio.
 */
public enum Bug {
    NINGUNO,
    DOBLE_CARGO,
    CARRERA,
    REDONDEO,
    REVERSO_PARCIAL,
    IMPUESTO_DOBLE,
    CRONOGRAMA,
    TIMEOUT_A_MEDIAS;

    public static Bug activo() {
        String valor = System.getProperty("cuadre.bug");
        if (valor == null || valor.isBlank()) {
            valor = System.getenv("CUADRE_BUG");
        }
        return desde(valor);
    }

    public static Bug desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return NINGUNO;
        }
        try {
            return valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("CUADRE_BUG desconocido: '" + valor + "'. Valores válidos: "
                    + Arrays.toString(values()), e);
        }
    }
}
