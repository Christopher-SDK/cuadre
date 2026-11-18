package cuadre.dominio.cuenta;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.dinero.Moneda;
import cuadre.dominio.falla.PuntoDeFalla;
import cuadre.dominio.impuesto.ReglaItf;
import cuadre.dominio.libro.Asiento;
import cuadre.dominio.libro.Banco;
import cuadre.dominio.libro.Libro;
import cuadre.dominio.libro.Movimiento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static cuadre.dominio.cuenta.CuentasInternas.caja;
import static cuadre.dominio.cuenta.CuentasInternas.itf;
import static cuadre.dominio.cuenta.CuentasInternas.transito;
import static cuadre.dominio.libro.Movimiento.debe;
import static cuadre.dominio.libro.Movimiento.haber;

/** Casos de uso de cuentas: depósito, retiro, transferencia idempotente y reverso. */
public class ServicioCuentas {

    protected final Banco banco;
    protected final ReglaItf itf;
    protected final Clock reloj;
    protected final Duration ventanaIdempotencia;
    protected final PuntoDeFalla falla;

    public ServicioCuentas(Banco banco, ReglaItf itf, Clock reloj, Duration ventanaIdempotencia, PuntoDeFalla falla) {
        this.banco = banco;
        this.itf = itf;
        this.reloj = reloj;
        this.ventanaIdempotencia = ventanaIdempotencia;
        this.falla = falla;
    }

    public Cuenta abrir(String titular, Moneda moneda, Dinero limiteSobregiro) {
        if (titular == null || titular.isBlank()) {
            throw new OperacionRechazada(Motivo.CONDICIONES_INVALIDAS, "El titular es obligatorio");
        }
        if (limiteSobregiro.moneda() != moneda) {
            throw new OperacionRechazada(Motivo.MONEDA_INCOMPATIBLE, "El límite de sobregiro debe estar en " + moneda);
        }
        if (limiteSobregiro.esNegativo()) {
            throw new OperacionRechazada(Motivo.MONTO_INVALIDO, "El límite de sobregiro no puede ser negativo");
        }
        Cuenta cuenta = Cuenta.deCliente("C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                titular.strip(), moneda, limiteSobregiro);
        return banco.enTransaccion(libro -> {
            libro.abrir(cuenta);
            return cuenta;
        });
    }

    public Operacion depositar(String cuentaId, Dinero monto) {
        exigirPositivo(monto);
        return banco.enTransaccion(libro -> {
            Cuenta cuenta = exigirCliente(libro.bloquear(List.of(cuentaId)).get(cuentaId));
            exigirMoneda(cuenta, monto);
            Asiento asiento = Asiento.nuevo("Depósito en " + cuentaId, List.of(
                    debe(caja(monto.moneda()), monto),
                    haber(cuentaId, monto)));
            return confirmar(libro, Operacion.nueva(TipoOperacion.DEPOSITO, ahora(), List.of(asiento)));
        });
    }

    public Operacion retirar(String cuentaId, Dinero monto) {
        exigirPositivo(monto);
        return banco.enTransaccion(libro -> {
            Cuenta cuenta = exigirCliente(libro.bloquear(List.of(cuentaId)).get(cuentaId));
            exigirMoneda(cuenta, monto);
            Dinero impuesto = itf.sobre(monto);
            exigirFondos(cuenta, monto.sumar(impuesto));
            List<Movimiento> lineas = new ArrayList<>();
            lineas.add(debe(cuentaId, monto));
            lineas.add(haber(caja(monto.moneda()), monto));
            agregarItf(lineas, cuentaId, impuesto);
            Asiento asiento = Asiento.nuevo("Retiro de " + cuentaId, lineas);
            return confirmar(libro, Operacion.nueva(TipoOperacion.RETIRO, ahora(), List.of(asiento)));
        });
    }

    /**
     * Transferencia en dos asientos a través de la cuenta puente: débito (origen → tránsito) y
     * crédito (tránsito → destino). Ambos van en la misma transacción.
     * Un reintento con la misma clave devuelve la operación original sin mover dinero.
     */
    public Operacion transferir(SolicitudTransferencia solicitud) {
        validar(solicitud);
        return banco.enTransaccion(libro -> {
            Optional<Operacion> previa = reintento(libro, solicitud);
            if (previa.isPresent()) {
                return previa.get();
            }
            PlanTransferencia plan = planificar(libro, solicitud);
            libro.registrar(plan.debito());
            falla.alcanzado(PuntoDeFalla.ENTRE_DEBITO_Y_CREDITO);
            libro.registrar(plan.credito());
            falla.alcanzado(PuntoDeFalla.ANTES_DE_CONFIRMAR);
            return cerrar(libro, plan);
        });
    }

    public Operacion revertir(UUID operacionId) {
        return banco.enTransaccion(libro -> {
            Operacion original = libro.operacion(operacionId).orElseThrow(() ->
                    new OperacionRechazada(Motivo.OPERACION_INEXISTENTE, "No existe la operación " + operacionId));
            if (!original.tipo().esReversible()) {
                throw new OperacionRechazada(Motivo.NO_REVERSIBLE, "Una operación " + original.tipo() + " no se revierte");
            }
            if (original.revertida()) {
                throw new OperacionRechazada(Motivo.YA_REVERTIDA, "La operación " + operacionId + " ya fue revertida");
            }
            List<Movimiento> lineas = movimientosDeReverso(original);
            Map<String, Long> efecto = efectoPorCuenta(lineas);
            Map<String, Cuenta> cuentas = libro.bloquear(efecto.keySet());
            for (var e : efecto.entrySet()) {
                Cuenta c = cuentas.get(e.getKey());
                Dinero nuevo = c.saldo().sumar(Dinero.centimos(e.getValue(), c.moneda()));
                if (!c.admiteSaldo(nuevo)) {
                    throw new OperacionRechazada(Motivo.SALDO_INSUFICIENTE,
                            "El reverso dejaría la cuenta " + c.id() + " en " + nuevo + ", por debajo de su límite");
                }
            }
            Asiento asiento = Asiento.nuevo("Reverso de " + operacionId, lineas);
            libro.registrar(asiento);
            libro.guardar(original.marcadaRevertida());
            Operacion reverso = new Operacion(UUID.randomUUID(), TipoOperacion.REVERSO, null, null, ahora(),
                    List.of(asiento), original.id(), false);
            libro.guardar(reverso);
            return reverso;
        });
    }

    // ---------------------------------------------------------------- piezas de la transferencia

    protected record PlanTransferencia(SolicitudTransferencia solicitud, Asiento debito, Asiento credito, Instant fecha) {
    }

    protected void validar(SolicitudTransferencia s) {
        exigirPositivo(s.monto());
        if (s.origen().equals(s.destino())) {
            throw new OperacionRechazada(Motivo.MISMA_CUENTA, "Origen y destino son la misma cuenta");
        }
    }

    protected Optional<Operacion> reintento(Libro libro, SolicitudTransferencia s) {
        Optional<Operacion> previa = libro.operacionPorClave(s.clave(), ahora().minus(ventanaIdempotencia));
        previa.filter(op -> !op.huella().equals(s.huella())).ifPresent(op -> {
            throw new OperacionRechazada(Motivo.CLAVE_REUTILIZADA,
                    "La clave " + s.clave() + " ya se usó para otra transferencia");
        });
        return previa;
    }

    protected PlanTransferencia planificar(Libro libro, SolicitudTransferencia s) {
        Map<String, Cuenta> cuentas = libro.bloquear(List.of(s.origen(), s.destino()));
        Cuenta origen = exigirCliente(cuentas.get(s.origen()));
        Cuenta destino = exigirCliente(cuentas.get(s.destino()));
        exigirMoneda(origen, s.monto());
        exigirMoneda(destino, s.monto());
        Moneda moneda = s.monto().moneda();
        ReglaItf.Cargos cargos = itf.deTransferencia(s.monto());
        exigirFondos(origen, s.monto().sumar(cargos.ordenante()));

        List<Movimiento> debito = new ArrayList<>();
        debito.add(debe(origen.id(), s.monto()));
        debito.add(haber(transito(moneda), s.monto()));
        agregarItf(debito, origen.id(), cargos.ordenante());

        List<Movimiento> credito = new ArrayList<>();
        credito.add(debe(transito(moneda), s.monto()));
        credito.add(haber(destino.id(), s.monto()));
        agregarItf(credito, destino.id(), cargos.beneficiario());

        return new PlanTransferencia(s,
                Asiento.nuevo("Transferencia " + s.clave() + ": débito", debito),
                Asiento.nuevo("Transferencia " + s.clave() + ": crédito", credito),
                ahora());
    }

    protected Operacion cerrar(Libro libro, PlanTransferencia plan) {
        SolicitudTransferencia s = plan.solicitud();
        Operacion op = new Operacion(UUID.randomUUID(), TipoOperacion.TRANSFERENCIA, s.clave(), s.huella(), plan.fecha(),
                List.of(plan.debito(), plan.credito()), null, false);
        if (!libro.reservarClave(s.clave(), op.id(), op.fecha(), op.fecha().minus(ventanaIdempotencia))) {
            throw new OperacionRechazada(Motivo.CLAVE_REUTILIZADA, "La clave " + s.clave() + " está en uso por otra transferencia");
        }
        libro.guardar(op);
        return op;
    }

    /** Asiento que deja todo exactamente como estaba, impuesto incluido. */
    protected List<Movimiento> movimientosDeReverso(Operacion original) {
        List<Movimiento> lineas = new ArrayList<>();
        for (Asiento a : original.asientos()) {
            lineas.addAll(a.contrapartidas());
        }
        return lineas;
    }

    // ---------------------------------------------------------------- utilidades

    private Operacion confirmar(Libro libro, Operacion op) {
        op.asientos().forEach(libro::registrar);
        libro.guardar(op);
        return op;
    }

    protected Instant ahora() {
        return reloj.instant();
    }

    private void agregarItf(List<Movimiento> lineas, String cuenta, Dinero impuesto) {
        if (impuesto.esPositivo()) {
            lineas.add(debe(cuenta, impuesto));
            lineas.add(haber(itf(impuesto.moneda()), impuesto));
        }
    }

    private static Map<String, Long> efectoPorCuenta(List<Movimiento> lineas) {
        Map<String, Long> efecto = new HashMap<>();
        for (Movimiento m : lineas) {
            efecto.merge(m.cuenta(), m.efectoEnSaldo(), Long::sum);
        }
        return efecto;
    }

    protected static void exigirPositivo(Dinero monto) {
        if (!monto.esPositivo()) {
            throw new OperacionRechazada(Motivo.MONTO_INVALIDO, "El monto debe ser mayor que cero: " + monto);
        }
    }

    protected static void exigirMoneda(Cuenta cuenta, Dinero monto) {
        if (cuenta.moneda() != monto.moneda()) {
            throw new OperacionRechazada(Motivo.MONEDA_INCOMPATIBLE,
                    "La cuenta " + cuenta.id() + " es en " + cuenta.moneda() + " y el monto en " + monto.moneda());
        }
    }

    protected static void exigirFondos(Cuenta cuenta, Dinero cargo) {
        Dinero nuevo = cuenta.saldo().restar(cargo);
        if (!cuenta.admiteSaldo(nuevo)) {
            throw new OperacionRechazada(Motivo.SALDO_INSUFICIENTE,
                    "Saldo insuficiente en " + cuenta.id() + ": tiene " + cuenta.saldo() + ", necesita " + cargo
                            + " y su límite de sobregiro es " + cuenta.limiteSobregiro());
        }
    }

    /** Las cuentas internas del banco no se operan desde afuera: para un cliente, no existen. */
    protected static Cuenta exigirCliente(Cuenta cuenta) {
        if (cuenta.interna()) {
            throw new OperacionRechazada(Motivo.CUENTA_INEXISTENTE, "No existe la cuenta " + cuenta.id());
        }
        return cuenta;
    }
}
