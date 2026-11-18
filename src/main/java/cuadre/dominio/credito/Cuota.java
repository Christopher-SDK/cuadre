package cuadre.dominio.credito;

import cuadre.dominio.dinero.Dinero;

public record Cuota(int numero, Dinero saldoInicial, Dinero amortizacion, Dinero interes, Dinero seguro,
                    Dinero comision, Dinero saldoFinal) {

    /** Lo que el cliente paga ese mes. */
    public Dinero total() {
        return amortizacion.sumar(interes).sumar(seguro).sumar(comision);
    }
}
