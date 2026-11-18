package cuadre.adaptador.rest;

import cuadre.dominio.credito.Credito;
import cuadre.dominio.credito.Cuota;
import cuadre.dominio.credito.ServicioCreditos.Simulacion;
import cuadre.dominio.cuenta.Cuenta;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.dinero.Dinero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lo que devuelve la API. Los montos viajan como texto con dos decimales: nunca como número de coma flotante. */
final class Vistas {

    private Vistas() {
    }

    static String texto(Dinero d) {
        return d.aDecimal().toPlainString();
    }

    static String porcentaje(BigDecimal fraccion) {
        return fraccion.movePointRight(2).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    record CuentaVista(String id, String titular, String moneda, String saldo, String limiteSobregiro) {
        static CuentaVista de(Cuenta c) {
            return new CuentaVista(c.id(), c.titular(), c.moneda().name(), texto(c.saldo()), texto(c.limiteSobregiro()));
        }
    }

    record MovimientoVista(String cuenta, String lado, String monto) {
    }

    record OperacionVista(UUID id, String tipo, Instant fecha, boolean revertida, UUID revierte, String moneda,
                          List<MovimientoVista> movimientos) {
        static OperacionVista de(Operacion op) {
            return new OperacionVista(op.id(), op.tipo().name(), op.fecha(), op.revertida(), op.revierte(),
                    op.asientos().getFirst().moneda().name(),
                    op.movimientos().stream().map(m -> new MovimientoVista(m.cuenta(), m.lado().name(), texto(m.monto()))).toList());
        }
    }

    record CuotaVista(int numero, String saldoInicial, String amortizacion, String interes, String desgravamen,
                      String comision, String total, String saldoFinal) {
        static CuotaVista de(Cuota q) {
            return new CuotaVista(q.numero(), texto(q.saldoInicial()), texto(q.amortizacion()), texto(q.interes()),
                    texto(q.seguro()), texto(q.comision()), texto(q.total()), texto(q.saldoFinal()));
        }
    }

    record SimulacionVista(String moneda, String monto, int plazoMeses, String teaPorcentaje, String temPorcentaje,
                           String tceaPorcentaje, String totalAPagar, List<CuotaVista> cuotas) {
        static SimulacionVista de(Simulacion s) {
            return new SimulacionVista(s.condiciones().monto().moneda().name(), texto(s.condiciones().monto()),
                    s.condiciones().plazoMeses(), porcentaje(s.condiciones().tea()), porcentaje(s.tem()), porcentaje(s.tcea()),
                    texto(s.cronograma().pagos().stream().reduce(Dinero::sumar).orElseThrow()),
                    s.cronograma().cuotas().stream().map(CuotaVista::de).toList());
        }
    }

    record CreditoVista(UUID id, String cuenta, String moneda, String saldoPendiente, int cuotasPagadas, boolean cancelado,
                        String temPorcentaje, List<CuotaVista> cuotas) {
        static CreditoVista de(Credito c) {
            return new CreditoVista(c.id(), c.cuenta(), c.condiciones().monto().moneda().name(), texto(c.saldoPendiente()),
                    c.pagadas(), c.cancelado(), porcentaje(c.tem()), c.cuotas().stream().map(CuotaVista::de).toList());
        }
    }

    record AuditoriaVista(boolean cuadra, List<String> hallazgos) {
    }
}
