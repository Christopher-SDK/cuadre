package cuadre.mutantes;

import cuadre.dominio.cuenta.CuentasInternas;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.cuenta.ServicioCuentas;
import cuadre.dominio.falla.PuntoDeFalla;
import cuadre.dominio.impuesto.ReglaItf;
import cuadre.dominio.libro.Banco;
import cuadre.dominio.libro.Lado;
import cuadre.dominio.libro.Movimiento;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * REVERSO_PARCIAL. Alguien leyó que "el ITF no es reembolsable" y excluyó del reverso las líneas del impuesto
 * (con su contrapartida en la cuenta del cliente, para que el asiento siga cuadrando).
 * Qué le pasa al cliente: le devuelven el monto, pero el impuesto de una operación anulada se queda en el banco.
 */
class ReversoSinItf extends ServicioCuentas {

    ReversoSinItf(Banco banco, ReglaItf itf, Clock reloj, Duration ventana, PuntoDeFalla falla) {
        super(banco, itf, reloj, ventana, falla);
    }

    @Override
    protected List<Movimiento> movimientosDeReverso(Operacion original) {
        List<Movimiento> lineas = new ArrayList<>(super.movimientosDeReverso(original));
        String cuentaItf = CuentasInternas.itf(original.asientos().getFirst().moneda());
        for (Movimiento impuesto : List.copyOf(lineas)) {
            if (impuesto.cuenta().equals(cuentaItf) && impuesto.lado() == Lado.DEBE) {
                lineas.remove(impuesto);
                lineas.remove(new Movimiento(clienteQueLoPago(lineas, impuesto), Lado.HABER, impuesto.monto()));
            }
        }
        return lineas;
    }

    private static String clienteQueLoPago(List<Movimiento> lineas, Movimiento impuesto) {
        return lineas.stream()
                .filter(m -> m.lado() == Lado.HABER && m.monto().equals(impuesto.monto()) && m.cuenta().startsWith("C-"))
                .map(Movimiento::cuenta).findFirst().orElseThrow();
    }
}
