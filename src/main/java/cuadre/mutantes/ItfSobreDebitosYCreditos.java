package cuadre.mutantes;

import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.impuesto.ReglaItf;

import java.math.BigDecimal;

/**
 * IMPUESTO_DOBLE. La ley del ITF grava "débitos y créditos en cuentas", y alguien lo implementó al pie de la letra
 * en una transferencia interna: se cobra al que envía y también al que recibe.
 * Qué le pasa al cliente: recibe menos de lo que le enviaron y el banco recauda el doble.
 */
class ItfSobreDebitosYCreditos extends ReglaItf {

    ItfSobreDebitosYCreditos(BigDecimal tasa, Redondeo redondeo) {
        super(tasa, redondeo);
    }

    @Override
    public Cargos deTransferencia(Dinero monto) {
        Dinero impuesto = sobre(monto);
        return new Cargos(impuesto, impuesto);
    }
}
