package cuadre.mutantes;

import cuadre.dominio.credito.CalculadoraFrancesa;
import cuadre.dominio.credito.Cuota;
import cuadre.dominio.dinero.Dinero;

import java.math.RoundingMode;

/**
 * CRONOGRAMA. "La cuota es fija": la última cuota se calcula igual que las demás y no absorbe
 * los céntimos que dejó el redondeo. El crédito termina con un saldo de unos céntimos a favor o en contra.
 * Qué le pasa al cliente: cree haber pagado todo y el sistema sigue mostrando una deuda (o un saldo a favor).
 */
class CuotaFijaHastaElFinal extends CalculadoraFrancesa {

    CuotaFijaHastaElFinal(RoundingMode redondeoInteres) {
        super(redondeoInteres);
    }

    @Override
    protected Cuota cuotaFinal(int numero, Dinero saldo, Dinero cuotaBase, Dinero interes, Dinero seguro, Dinero comision) {
        return cuotaRegular(numero, saldo, cuotaBase, interes, seguro, comision);
    }
}
