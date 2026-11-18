package cuadre.dominio.cuenta;

import cuadre.dominio.dinero.Moneda;

import java.util.ArrayList;
import java.util.List;

/** Cuentas propias del banco, una por moneda. */
public final class CuentasInternas {

    private CuentasInternas() {
    }

    /** Contrapartida del efectivo que entra y sale del sistema (ventanilla, cajero). */
    public static String caja(Moneda m) {
        return "CAJA-" + m;
    }

    /** ITF retenido a clientes, pendiente de pagar al fisco. */
    public static String itf(Moneda m) {
        return "ITF-" + m;
    }

    /** Cuenta puente de transferencias: debe volver a cero al terminar cada operación. */
    public static String transito(Moneda m) {
        return "TRANSITO-" + m;
    }

    public static String cartera(Moneda m) {
        return "CARTERA-" + m;
    }

    public static String intereses(Moneda m) {
        return "INTERESES-" + m;
    }

    public static String comisiones(Moneda m) {
        return "COMISIONES-" + m;
    }

    public static String desgravamen(Moneda m) {
        return "DESGRAVAMEN-" + m;
    }

    public static List<Cuenta> todas() {
        List<Cuenta> cuentas = new ArrayList<>();
        for (Moneda m : Moneda.values()) {
            for (String id : List.of(caja(m), itf(m), transito(m), cartera(m), intereses(m), comisiones(m), desgravamen(m))) {
                cuentas.add(Cuenta.interna(id, m));
            }
        }
        return cuentas;
    }
}
