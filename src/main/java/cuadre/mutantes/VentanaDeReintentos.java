package cuadre.mutantes;

import java.time.Duration;

/**
 * DOBLE_CARGO. La ventana de idempotencia se configura en horas, pero aquí se convierte a milisegundos
 * olvidando el factor 1000: 24 horas se vuelven 86,4 segundos. Un cliente que reintenta pasado ese tiempo
 * (por ejemplo, tras un timeout y un backoff de dos minutos) ya no es reconocido y se le cobra de nuevo.
 */
final class VentanaDeReintentos {

    private VentanaDeReintentos() {
    }

    static Duration desdeHoras(long horas) {
        return Duration.ofMillis(horas * 60 * 60);
    }
}
