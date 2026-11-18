package cuadre.dominio.credito;

import cuadre.dominio.dinero.Dinero;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Crédito vigente. {@code cuotas} es el cronograma actual: las {@code pagadas} primeras ya se cobraron;
 * un prepago reemplaza las pendientes por un cronograma recalculado.
 */
public record Credito(UUID id, String cuenta, CondicionesCredito condiciones, BigDecimal tem, List<Cuota> cuotas, int pagadas) {

    public Credito {
        cuotas = List.copyOf(cuotas);
    }

    public boolean cancelado() {
        return pagadas >= cuotas.size();
    }

    public Cuota proxima() {
        return cuotas.get(pagadas);
    }

    public Dinero saldoPendiente() {
        return cancelado() ? Dinero.cero(condiciones.monto().moneda()) : proxima().saldoInicial();
    }

    public List<Cuota> pendientes() {
        return cuotas.subList(pagadas, cuotas.size());
    }

    public Credito conCuotaPagada() {
        return new Credito(id, cuenta, condiciones, tem, cuotas, pagadas + 1);
    }

    public Credito conPendientes(List<Cuota> nuevas) {
        List<Cuota> todas = new ArrayList<>(cuotas.subList(0, pagadas));
        todas.addAll(nuevas);
        return new Credito(id, cuenta, condiciones, tem, todas, pagadas);
    }
}
