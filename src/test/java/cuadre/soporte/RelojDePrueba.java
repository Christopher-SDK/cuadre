package cuadre.soporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que solo avanza cuando la prueba lo indica: el tiempo es parte del escenario, no ruido. */
public final class RelojDePrueba extends Clock {

    private Instant ahora = Instant.parse("2026-01-05T14:00:00Z");

    public void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
