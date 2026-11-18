package cuadre.adaptador.memoria;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;
import cuadre.dominio.credito.Credito;
import cuadre.dominio.cuenta.Cuenta;
import cuadre.dominio.cuenta.CuentasInternas;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.dinero.Moneda;
import cuadre.dominio.libro.Asiento;
import cuadre.dominio.libro.Banco;
import cuadre.dominio.libro.Libro;
import cuadre.dominio.libro.Movimiento;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Banco en memoria para las pruebas rápidas de propiedades.
 * Cada transacción trabaja sobre una copia del estado y solo la publica si termina bien,
 * así una excepción a mitad de camino no deja nada aplicado (igual que un ROLLBACK).
 */
public class BancoEnMemoria implements Banco {

    // ponytail: copia completa por transacción y un único candado; suficiente para miles de operaciones en pruebas
    private Estado estado = new Estado();

    public BancoEnMemoria() {
        CuentasInternas.todas().forEach(c -> estado.cuentas.put(c.id(), c));
    }

    @Override
    public synchronized <T> T enTransaccion(Function<Libro, T> trabajo) {
        Estado copia = estado.copia();
        T resultado = trabajo.apply(new LibroEnMemoria(copia));
        estado = copia;
        return resultado;
    }

    private record Clave(UUID operacion, Instant fecha) {
    }

    private static final class Estado {
        final Map<String, Cuenta> cuentas = new TreeMap<>();
        final List<Asiento> asientos = new ArrayList<>();
        final Map<UUID, Operacion> operaciones = new LinkedHashMap<>();
        final Map<String, Clave> claves = new HashMap<>();
        final Map<UUID, Credito> creditos = new LinkedHashMap<>();

        Estado copia() {
            Estado c = new Estado();
            c.cuentas.putAll(cuentas);
            c.asientos.addAll(asientos);
            c.operaciones.putAll(operaciones);
            c.claves.putAll(claves);
            c.creditos.putAll(creditos);
            return c;
        }
    }

    private record LibroEnMemoria(Estado e) implements Libro {

        @Override
        public void abrir(Cuenta cuenta) {
            if (e.cuentas.putIfAbsent(cuenta.id(), cuenta) != null) {
                throw new IllegalStateException("La cuenta ya existe: " + cuenta.id());
            }
        }

        @Override
        public Cuenta cuenta(String id) {
            Cuenta c = e.cuentas.get(id);
            if (c == null) {
                throw new OperacionRechazada(Motivo.CUENTA_INEXISTENTE, "No existe la cuenta " + id);
            }
            return c;
        }

        @Override
        public Map<String, Cuenta> bloquear(Collection<String> ids) {
            Map<String, Cuenta> resultado = new LinkedHashMap<>();
            ids.stream().sorted().forEach(id -> resultado.put(id, cuenta(id)));
            return resultado;
        }

        @Override
        public void registrar(Asiento asiento) {
            for (Movimiento m : asiento.movimientos()) {
                Cuenta c = cuenta(m.cuenta());
                e.cuentas.put(c.id(), c.conSaldo(c.saldo().sumar(Dinero.centimos(m.efectoEnSaldo(), c.moneda()))));
            }
            e.asientos.add(asiento);
        }

        @Override
        public void guardar(Operacion operacion) {
            e.operaciones.put(operacion.id(), operacion);
        }

        @Override
        public Optional<Operacion> operacion(UUID id) {
            return Optional.ofNullable(e.operaciones.get(id));
        }

        @Override
        public Optional<Operacion> operacionPorClave(String clave, Instant vigenteDesde) {
            return Optional.ofNullable(e.claves.get(clave))
                    .filter(c -> c.fecha().isAfter(vigenteDesde))
                    .map(c -> e.operaciones.get(c.operacion()));
        }

        @Override
        public boolean reservarClave(String clave, UUID operacion, Instant fecha, Instant vigenteDesde) {
            Clave actual = e.claves.get(clave);
            if (actual != null && actual.fecha().isAfter(vigenteDesde)) {
                return false;
            }
            e.claves.put(clave, new Clave(operacion, fecha));
            return true;
        }

        @Override
        public void guardar(Credito credito) {
            e.creditos.put(credito.id(), credito);
        }

        @Override
        public Optional<Credito> credito(UUID id) {
            return Optional.ofNullable(e.creditos.get(id));
        }

        @Override
        public List<Cuenta> cuentas() {
            return List.copyOf(e.cuentas.values());
        }

        @Override
        public List<Credito> creditos() {
            return List.copyOf(e.creditos.values());
        }

        @Override
        public Map<String, Long> saldosSegunMovimientos() {
            Map<String, Long> saldos = new TreeMap<>();
            e.cuentas.keySet().forEach(id -> saldos.put(id, 0L));
            for (Asiento a : e.asientos) {
                for (Movimiento m : a.movimientos()) {
                    saldos.merge(m.cuenta(), m.efectoEnSaldo(), Long::sum);
                }
            }
            return saldos;
        }

        @Override
        public Map<Moneda, Long> descuadrePorMoneda() {
            Map<Moneda, Long> suma = new EnumMap<>(Moneda.class);
            for (Moneda m : Moneda.values()) {
                suma.put(m, 0L);
            }
            for (Asiento a : e.asientos) {
                for (Movimiento m : a.movimientos()) {
                    suma.merge(m.monto().moneda(), m.efectoEnSaldo(), Long::sum);
                }
            }
            return suma;
        }
    }
}
