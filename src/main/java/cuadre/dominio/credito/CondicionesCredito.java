package cuadre.dominio.credito;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.dinero.Moneda;

import java.math.BigDecimal;

/**
 * Condiciones pactadas de un crédito de cuota fija.
 *
 * @param tea                    tasa efectiva anual como fracción (0.25 = 25 %)
 * @param tasaDesgravamenMensual se aplica cada mes sobre el saldo pendiente (0.0005 = 0,05 %)
 * @param comisionMensual        monto fijo que se suma a cada cuota
 * @param comisionDesembolso     se descuenta del monto que recibe el cliente
 */
public record CondicionesCredito(Dinero monto, int plazoMeses, BigDecimal tea, BigDecimal tasaDesgravamenMensual,
                                 Dinero comisionMensual, Dinero comisionDesembolso) {

    public static final int PLAZO_MAXIMO = 360;
    public static final BigDecimal TEA_MAXIMA = new BigDecimal("2.00");
    public static final BigDecimal DESGRAVAMEN_MAXIMO = new BigDecimal("0.01");

    public CondicionesCredito {
        Moneda moneda = monto.moneda();
        if (!monto.esPositivo()) {
            invalida("El monto debe ser mayor que cero");
        }
        if (plazoMeses < 1 || plazoMeses > PLAZO_MAXIMO) {
            invalida("El plazo va de 1 a " + PLAZO_MAXIMO + " meses");
        }
        if (tea.signum() < 0 || tea.compareTo(TEA_MAXIMA) > 0) {
            invalida("La TEA va de 0 a " + TEA_MAXIMA + " (200 %)");
        }
        if (tasaDesgravamenMensual.signum() < 0 || tasaDesgravamenMensual.compareTo(DESGRAVAMEN_MAXIMO) > 0) {
            invalida("La tasa de desgravamen mensual va de 0 a " + DESGRAVAMEN_MAXIMO);
        }
        if (comisionMensual.moneda() != moneda || comisionDesembolso.moneda() != moneda) {
            throw new OperacionRechazada(Motivo.MONEDA_INCOMPATIBLE, "Las comisiones deben estar en " + moneda);
        }
        if (comisionMensual.esNegativo() || comisionDesembolso.esNegativo()) {
            invalida("Las comisiones no pueden ser negativas");
        }
        if (!comisionDesembolso.menorQue(monto)) {
            invalida("La comisión de desembolso debe ser menor que el monto");
        }
    }

    public static CondicionesCredito sinCostos(Dinero monto, int plazoMeses, BigDecimal tea) {
        Dinero cero = Dinero.cero(monto.moneda());
        return new CondicionesCredito(monto, plazoMeses, tea, BigDecimal.ZERO, cero, cero);
    }

    public boolean tieneCostos() {
        return tasaDesgravamenMensual.signum() > 0 || comisionMensual.esPositivo() || comisionDesembolso.esPositivo();
    }

    private static void invalida(String detalle) {
        throw new OperacionRechazada(Motivo.CONDICIONES_INVALIDAS, detalle);
    }
}
