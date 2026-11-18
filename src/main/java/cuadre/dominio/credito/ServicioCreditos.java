package cuadre.dominio.credito;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;
import cuadre.dominio.cuenta.Cuenta;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.cuenta.TipoOperacion;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.dinero.Moneda;
import cuadre.dominio.libro.Asiento;
import cuadre.dominio.libro.Banco;
import cuadre.dominio.libro.Libro;
import cuadre.dominio.libro.Movimiento;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cuadre.dominio.cuenta.CuentasInternas.cartera;
import static cuadre.dominio.cuenta.CuentasInternas.comisiones;
import static cuadre.dominio.cuenta.CuentasInternas.desgravamen;
import static cuadre.dominio.cuenta.CuentasInternas.intereses;
import static cuadre.dominio.libro.Movimiento.debe;
import static cuadre.dominio.libro.Movimiento.haber;

/** Desembolso, pago de cuota y prepago. Los pagos se cargan a la cuenta del cliente. */
public class ServicioCreditos {

    public record Simulacion(CondicionesCredito condiciones, BigDecimal tem, Cronograma cronograma, BigDecimal tcea) {
    }

    private final Banco banco;
    private final CalculadoraFrancesa calculadora;
    private final Clock reloj;

    public ServicioCreditos(Banco banco, CalculadoraFrancesa calculadora, Clock reloj) {
        this.banco = banco;
        this.calculadora = calculadora;
        this.reloj = reloj;
    }

    public Simulacion simular(CondicionesCredito c) {
        Cronograma cronograma = calculadora.calcular(c);
        return new Simulacion(c, CalculadoraFrancesa.tem(c.tea()), cronograma, CalculadoraTcea.tcea(c, cronograma));
    }

    public Credito desembolsar(String cuentaId, CondicionesCredito c) {
        Cronograma cronograma = calculadora.calcular(c);
        return banco.enTransaccion(libro -> {
            Cuenta cuenta = cuentaCliente(libro, cuentaId, c.monto().moneda());
            Moneda m = c.monto().moneda();
            List<Movimiento> lineas = new ArrayList<>();
            lineas.add(debe(cartera(m), c.monto()));
            lineas.add(haber(cuenta.id(), c.monto()));
            if (c.comisionDesembolso().esPositivo()) {
                lineas.add(debe(cuenta.id(), c.comisionDesembolso()));
                lineas.add(haber(comisiones(m), c.comisionDesembolso()));
            }
            Credito credito = new Credito(UUID.randomUUID(), cuenta.id(), c, CalculadoraFrancesa.tem(c.tea()), cronograma.cuotas(), 0);
            registrar(libro, TipoOperacion.DESEMBOLSO, Asiento.nuevo("Desembolso del crédito " + credito.id(), lineas));
            libro.guardar(credito);
            return credito;
        });
    }

    public Operacion pagarCuota(UUID creditoId) {
        return banco.enTransaccion(libro -> {
            Credito credito = vigente(libro, creditoId);
            Cuota cuota = credito.proxima();
            Moneda m = cuota.total().moneda();
            Cuenta cuenta = cuentaCliente(libro, credito.cuenta(), m);
            exigirFondos(cuenta, cuota.total());
            List<Movimiento> lineas = new ArrayList<>();
            lineas.add(debe(cuenta.id(), cuota.total()));
            agregar(lineas, cartera(m), cuota.amortizacion());
            agregar(lineas, intereses(m), cuota.interes());
            agregar(lineas, desgravamen(m), cuota.seguro());
            agregar(lineas, comisiones(m), cuota.comision());
            Operacion op = registrar(libro, TipoOperacion.PAGO_CUOTA,
                    Asiento.nuevo("Cuota " + cuota.numero() + " del crédito " + creditoId, lineas));
            libro.guardar(credito.conCuotaPagada());
            return op;
        });
    }

    /** Prepago a capital en la fecha de una cuota: se mantiene el plazo y baja la cuota. */
    public Operacion prepagar(UUID creditoId, Dinero monto) {
        if (!monto.esPositivo()) {
            throw new OperacionRechazada(Motivo.MONTO_INVALIDO, "El prepago debe ser mayor que cero");
        }
        return banco.enTransaccion(libro -> {
            Credito credito = vigente(libro, creditoId);
            Dinero saldo = credito.saldoPendiente();
            if (monto.moneda() != saldo.moneda()) {
                throw new OperacionRechazada(Motivo.MONEDA_INCOMPATIBLE, "El crédito es en " + saldo.moneda());
            }
            if (saldo.menorQue(monto)) {
                throw new OperacionRechazada(Motivo.MONTO_INVALIDO, "El prepago " + monto + " supera el saldo pendiente " + saldo);
            }
            Cuenta cuenta = cuentaCliente(libro, credito.cuenta(), monto.moneda());
            exigirFondos(cuenta, monto);
            Operacion op = registrar(libro, TipoOperacion.PREPAGO, Asiento.nuevo("Prepago del crédito " + creditoId, List.of(
                    debe(cuenta.id(), monto), haber(cartera(monto.moneda()), monto))));
            Dinero nuevoSaldo = saldo.restar(monto);
            List<Cuota> pendientes = nuevoSaldo.esCero() ? List.of() : calculadora.calcular(nuevoSaldo,
                    credito.pendientes().size(), credito.proxima().numero(), credito.tem(),
                    credito.condiciones().tasaDesgravamenMensual(), credito.condiciones().comisionMensual()).cuotas();
            libro.guardar(credito.conPendientes(pendientes));
            return op;
        });
    }

    public Credito credito(UUID id) {
        return banco.leer(libro -> libro.credito(id)).orElseThrow(() ->
                new OperacionRechazada(Motivo.CREDITO_INEXISTENTE, "No existe el crédito " + id));
    }

    private static Credito vigente(Libro libro, UUID id) {
        Credito credito = libro.credito(id).orElseThrow(() ->
                new OperacionRechazada(Motivo.CREDITO_INEXISTENTE, "No existe el crédito " + id));
        if (credito.cancelado()) {
            throw new OperacionRechazada(Motivo.CREDITO_CANCELADO, "El crédito " + id + " ya está cancelado");
        }
        return credito;
    }

    private static Cuenta cuentaCliente(Libro libro, String id, Moneda moneda) {
        Cuenta cuenta = libro.bloquear(List.of(id)).get(id);
        if (cuenta.interna()) {
            throw new OperacionRechazada(Motivo.CUENTA_INEXISTENTE, "No existe la cuenta " + id);
        }
        if (cuenta.moneda() != moneda) {
            throw new OperacionRechazada(Motivo.MONEDA_INCOMPATIBLE, "La cuenta " + id + " es en " + cuenta.moneda());
        }
        return cuenta;
    }

    private static void exigirFondos(Cuenta cuenta, Dinero cargo) {
        if (!cuenta.admiteSaldo(cuenta.saldo().restar(cargo))) {
            throw new OperacionRechazada(Motivo.SALDO_INSUFICIENTE,
                    "Saldo insuficiente en " + cuenta.id() + ": tiene " + cuenta.saldo() + " y el cargo es " + cargo);
        }
    }

    private static void agregar(List<Movimiento> lineas, String cuenta, Dinero monto) {
        if (monto.esPositivo()) {
            lineas.add(haber(cuenta, monto));
        }
    }

    private Operacion registrar(Libro libro, TipoOperacion tipo, Asiento asiento) {
        libro.registrar(asiento);
        Operacion op = Operacion.nueva(tipo, reloj.instant(), List.of(asiento));
        libro.guardar(op);
        return op;
    }
}
