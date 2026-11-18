package cuadre.dominio.cuenta;

import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.dinero.Moneda;

/**
 * Cuenta del libro mayor. {@code saldo} es el saldo vigente (haber − debe);
 * el auditor verifica que siempre coincida con la suma de sus movimientos.
 */
public record Cuenta(String id, String titular, Moneda moneda, Dinero limiteSobregiro, Dinero saldo, boolean interna) {

    public static Cuenta deCliente(String id, String titular, Moneda moneda, Dinero limiteSobregiro) {
        return new Cuenta(id, titular, moneda, limiteSobregiro, Dinero.cero(moneda), false);
    }

    public static Cuenta interna(String id, Moneda moneda) {
        return new Cuenta(id, "Cuadre", moneda, Dinero.cero(moneda), Dinero.cero(moneda), true);
    }

    /** Las cuentas internas (caja, impuesto, cartera…) no tienen límite; las de clientes, sí. */
    public boolean admiteSaldo(Dinero nuevoSaldo) {
        return interna || !nuevoSaldo.menorQue(limiteSobregiro.negar());
    }

    public Cuenta conSaldo(Dinero nuevoSaldo) {
        return new Cuenta(id, titular, moneda, limiteSobregiro, nuevoSaldo, interna);
    }
}
