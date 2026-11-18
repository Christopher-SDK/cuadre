package cuadre.soporte;

import cuadre.dominio.falla.PuntoDeFalla;

/** Provoca un timeout la próxima vez que el sistema pase por el punto indicado, y solo esa vez. */
public final class FallaProgramada implements PuntoDeFalla {

    private volatile String pendiente;

    public void fallarUnaVezEn(String punto) {
        pendiente = punto;
    }

    /** Si el sistema rechazó la operación antes de llegar al punto, la falla no debe quedar armada para la siguiente. */
    public void desarmar() {
        pendiente = null;
    }

    @Override
    public void alcanzado(String punto) {
        if (punto.equals(pendiente)) {
            pendiente = null;
            throw new TiempoAgotado(punto);
        }
    }
}
