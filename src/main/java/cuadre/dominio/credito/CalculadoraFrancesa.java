package cuadre.dominio.credito;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;
import cuadre.dominio.dinero.Dinero;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cronograma de cuota fija (método francés).
 * <ul>
 *   <li>TEM = (1 + TEA)^(1/12) − 1, resuelta con Newton-Raphson en 34 dígitos.</li>
 *   <li>Cuota base (amortización + interés) = S·i / (1 − (1 + i)^−n), redondeada al céntimo.</li>
 *   <li>Interés y desgravamen de cada mes se calculan sobre el saldo y se redondean al céntimo.</li>
 *   <li>La última cuota amortiza exactamente el saldo restante: el crédito termina en 0,00.</li>
 * </ul>
 */
public class CalculadoraFrancesa {

    static final MathContext MC = MathContext.DECIMAL128;

    protected final RoundingMode redondeoInteres;

    public CalculadoraFrancesa(RoundingMode redondeoInteres) {
        this.redondeoInteres = redondeoInteres;
    }

    public static BigDecimal tem(BigDecimal tea) {
        BigDecimal a = BigDecimal.ONE.add(tea);
        BigDecimal doce = BigDecimal.valueOf(12);
        BigDecimal x = BigDecimal.ONE.add(tea.divide(doce, MC));
        BigDecimal tolerancia = new BigDecimal("1e-32");
        for (int i = 0; i < 200; i++) {
            BigDecimal x11 = x.pow(11, MC);
            BigDecimal paso = x11.multiply(x, MC).subtract(a, MC).divide(doce.multiply(x11, MC), MC);
            x = x.subtract(paso, MC);
            if (paso.abs().compareTo(tolerancia) < 0) {
                break;
            }
        }
        return x.subtract(BigDecimal.ONE, MC);
    }

    public Cronograma calcular(CondicionesCredito c) {
        return calcular(c.monto(), c.plazoMeses(), 1, tem(c.tea()), c.tasaDesgravamenMensual(), c.comisionMensual());
    }

    /** Cronograma desde {@code saldo} con {@code cuotas} pendientes; se usa también al recalcular tras un prepago. */
    public Cronograma calcular(Dinero saldo, int cuotas, int primerNumero, BigDecimal tem, BigDecimal desgravamen, Dinero comision) {
        Dinero cuotaBase = cuotaBase(saldo, cuotas, tem);
        List<Cuota> lista = new ArrayList<>(cuotas);
        for (int k = 0; k < cuotas; k++) {
            int numero = primerNumero + k;
            Dinero interes = saldo.multiplicar(tem, redondeoInteres);
            Dinero seguro = saldo.multiplicar(desgravamen, RoundingMode.HALF_UP);
            Cuota cuota = k == cuotas - 1
                    ? cuotaFinal(numero, saldo, cuotaBase, interes, seguro, comision)
                    : cuotaRegular(numero, saldo, cuotaBase, interes, seguro, comision);
            if (cuota.amortizacion().esNegativo() || cuota.saldoFinal().esNegativo()) {
                throw new OperacionRechazada(Motivo.CONDICIONES_INVALIDAS,
                        "Condiciones no viables: el monto es muy pequeño para ese plazo y tasa (cuota " + numero + ")");
            }
            lista.add(cuota);
            saldo = cuota.saldoFinal();
        }
        return new Cronograma(lista);
    }

    Dinero cuotaBase(Dinero saldo, int n, BigDecimal tem) {
        if (tem.signum() == 0) {
            return Dinero.de(saldo.aDecimal().divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP), saldo.moneda());
        }
        BigDecimal factor = BigDecimal.ONE.add(tem).pow(n, MC);
        BigDecimal cuota = saldo.aDecimal().multiply(tem, MC).multiply(factor, MC).divide(factor.subtract(BigDecimal.ONE), MC);
        return Dinero.de(cuota.setScale(2, RoundingMode.HALF_UP), saldo.moneda());
    }

    protected Cuota cuotaRegular(int numero, Dinero saldo, Dinero cuotaBase, Dinero interes, Dinero seguro, Dinero comision) {
        Dinero amortizacion = cuotaBase.restar(interes);
        return new Cuota(numero, saldo, amortizacion, interes, seguro, comision, saldo.restar(amortizacion));
    }

    /** La última cuota absorbe los céntimos que dejó el redondeo: amortiza todo lo que queda. */
    protected Cuota cuotaFinal(int numero, Dinero saldo, Dinero cuotaBase, Dinero interes, Dinero seguro, Dinero comision) {
        return new Cuota(numero, saldo, saldo, interes, seguro, comision, Dinero.cero(saldo.moneda()));
    }
}
