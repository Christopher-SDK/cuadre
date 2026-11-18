package cuadre.estado;

import cuadre.oraculo.OraculoFinanciero;
import cuadre.oraculo.OraculoFinanciero.Fila;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que el banco DEBERÍA hacer, escrito de la forma más simple posible: arreglos de céntimos, sin libro mayor,
 * sin transacciones, sin persistencia. No reutiliza ninguna clase del dominio; el cronograma sale del oráculo.
 * Cada método devuelve {@code false} si la operación debe rechazarse, y en ese caso no cambia nada.
 */
final class ModeloDeReferencia {

    final long[] saldo;
    final long[] limite;
    final BigDecimal tasaItf;
    long itfRecaudado;

    /** Efecto de cada operación sobre los clientes y el ITF, para poder revertirla. */
    record Op(boolean reversible, long[] efecto, long itf) {
    }

    final List<Op> operaciones = new ArrayList<>();
    private final List<Boolean> revertidas = new ArrayList<>();
    private final Map<String, String> claves = new HashMap<>();
    private final Map<String, Integer> operacionDeClave = new HashMap<>();

    static final class Credito {
        final int cuenta;
        final BigDecimal tea, desgravamen, comision;
        List<Fila> pendientes;

        Credito(int cuenta, BigDecimal tea, BigDecimal desgravamen, BigDecimal comision, List<Fila> cuotas) {
            this.cuenta = cuenta;
            this.tea = tea;
            this.desgravamen = desgravamen;
            this.comision = comision;
            this.pendientes = cuotas;
        }

        long saldoPendiente() {
            return pendientes.isEmpty() ? 0 : centimos(pendientes.getFirst().saldoInicial());
        }
    }

    final List<Credito> creditos = new ArrayList<>();

    ModeloDeReferencia(long[] limite, BigDecimal tasaItf) {
        this.saldo = new long[limite.length];
        this.limite = limite.clone();
        this.tasaItf = tasaItf;
    }

    /** ITF: monto × tasa, sin el tercer decimal, y el segundo bajado a 0 o 5. */
    long itf(long centimos) {
        long truncado = BigDecimal.valueOf(centimos).multiply(tasaItf).setScale(0, RoundingMode.FLOOR).longValueExact();
        return truncado / 5 * 5;
    }

    private boolean alcanza(int cuenta, long cargo) {
        return saldo[cuenta] - cargo >= -limite[cuenta];
    }

    private void registrar(boolean reversible, long[] efecto, long itf) {
        for (int i = 0; i < saldo.length; i++) {
            saldo[i] += efecto[i];
        }
        itfRecaudado += itf;
        operaciones.add(new Op(reversible, efecto, itf));
        revertidas.add(false);
    }

    private long[] efecto(int cuenta, long monto) {
        long[] e = new long[saldo.length];
        e[cuenta] = monto;
        return e;
    }

    boolean depositar(int cuenta, long monto) {
        registrar(true, efecto(cuenta, monto), 0);
        return true;
    }

    boolean retirar(int cuenta, long monto) {
        long impuesto = itf(monto);
        if (!alcanza(cuenta, monto + impuesto)) {
            return false;
        }
        registrar(true, efecto(cuenta, -(monto + impuesto)), impuesto);
        return true;
    }

    /** Una clave ya usada con el mismo pedido no hace nada; con otro pedido, se rechaza. */
    boolean transferir(String clave, int origen, int destino, long monto) {
        String pedido = origen + ">" + destino + ":" + monto;
        if (claves.containsKey(clave)) {
            return claves.get(clave).equals(pedido);
        }
        long impuesto = itf(monto);
        if (!alcanza(origen, monto + impuesto)) {
            return false;
        }
        long[] e = new long[saldo.length];
        e[origen] = -(monto + impuesto);
        e[destino] = monto;
        registrar(true, e, impuesto);
        claves.put(clave, pedido);
        operacionDeClave.put(clave, operaciones.size() - 1);
        return true;
    }

    boolean revertir(int indice) {
        Op op = operaciones.get(indice);
        if (!op.reversible() || revertidas.get(indice)) {
            return false;
        }
        for (int i = 0; i < saldo.length; i++) {
            if (saldo[i] - op.efecto()[i] < -limite[i]) {
                return false;
            }
        }
        long[] inverso = new long[saldo.length];
        for (int i = 0; i < saldo.length; i++) {
            inverso[i] = -op.efecto()[i];
        }
        revertidas.set(indice, true);
        registrar(false, inverso, -op.itf());
        return true;
    }

    boolean desembolsar(int cuenta, long monto, int plazo, BigDecimal tea, BigDecimal desgravamen, long comisionMensual,
                        long comisionDesembolso) {
        BigDecimal comision = BigDecimal.valueOf(comisionMensual, 2);
        List<Fila> cuotas = OraculoFinanciero.cronograma(BigDecimal.valueOf(monto, 2), plazo, tea, desgravamen, comision);
        creditos.add(new Credito(cuenta, tea, desgravamen, comision, new ArrayList<>(cuotas)));
        registrar(false, efecto(cuenta, monto - comisionDesembolso), 0);
        return true;
    }

    boolean pagarCuota(int credito) {
        Credito c = creditos.get(credito);
        if (c.pendientes.isEmpty()) {
            return false;
        }
        long total = centimos(c.pendientes.getFirst().total());
        if (!alcanza(c.cuenta, total)) {
            return false;
        }
        c.pendientes.removeFirst();
        registrar(false, efecto(c.cuenta, -total), 0);
        return true;
    }

    boolean prepagar(int credito, long monto) {
        Credito c = creditos.get(credito);
        long pendiente = c.saldoPendiente();
        if (c.pendientes.isEmpty() || monto > pendiente || !alcanza(c.cuenta, monto)) {
            return false;
        }
        long nuevo = pendiente - monto;
        c.pendientes = nuevo == 0 ? new ArrayList<>() : new ArrayList<>(OraculoFinanciero.cronograma(
                BigDecimal.valueOf(nuevo, 2), c.pendientes.size(), c.tea, c.desgravamen, c.comision));
        registrar(false, efecto(c.cuenta, -monto), 0);
        return true;
    }

    static long centimos(BigDecimal soles) {
        return soles.movePointRight(2).longValueExact();
    }
}
