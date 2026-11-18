package cuadre.adaptador.persistencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;
import cuadre.dominio.credito.Credito;
import cuadre.dominio.cuenta.Cuenta;
import cuadre.dominio.cuenta.Operacion;
import cuadre.dominio.cuenta.TipoOperacion;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.dinero.Moneda;
import cuadre.dominio.libro.Asiento;
import cuadre.dominio.libro.Lado;
import cuadre.dominio.libro.Libro;
import cuadre.dominio.libro.Movimiento;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** El libro mayor dentro de una transacción de PostgreSQL. */
public class LibroPostgres implements Libro {

    protected final JdbcClient jdbc;
    private final ObjectMapper json;

    public LibroPostgres(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void abrir(Cuenta c) {
        jdbc.sql("insert into cuenta (id, titular, moneda, limite_sobregiro, saldo, interna) values (?, ?, ?, ?, ?, ?)")
                .params(c.id(), c.titular(), c.moneda().name(), c.limiteSobregiro().centimos(), c.saldo().centimos(), c.interna())
                .update();
    }

    @Override
    public Cuenta cuenta(String id) {
        return jdbc.sql("select * from cuenta where id = ?").param(id).query(LibroPostgres::cuenta).optional()
                .orElseThrow(() -> inexistente(id));
    }

    /** SELECT … FOR UPDATE en orden de id: dos transferencias cruzadas A→B y B→A nunca se bloquean mutuamente. */
    @Override
    public Map<String, Cuenta> bloquear(Collection<String> ids) {
        Map<String, Cuenta> encontradas = new TreeMap<>();
        jdbc.sql("select * from cuenta where id in (:ids) order by id for update")
                .param("ids", List.copyOf(ids))
                .query(LibroPostgres::cuenta).list()
                .forEach(c -> encontradas.put(c.id(), c));
        Map<String, Cuenta> resultado = new LinkedHashMap<>();
        for (String id : ids) {
            Cuenta c = encontradas.get(id);
            if (c == null) {
                throw inexistente(id);
            }
            resultado.put(id, c);
        }
        return resultado;
    }

    @Override
    public void registrar(Asiento asiento) {
        jdbc.sql("insert into asiento (id, concepto) values (?, ?)").params(asiento.id(), asiento.concepto()).update();
        Map<String, Long> efecto = new TreeMap<>();
        for (Movimiento m : asiento.movimientos()) {
            jdbc.sql("insert into movimiento (asiento_id, cuenta_id, lado, centimos, moneda) values (?, ?, ?, ?, ?)")
                    .params(asiento.id(), m.cuenta(), m.lado().name(), m.monto().centimos(), m.monto().moneda().name())
                    .update();
            efecto.merge(m.cuenta(), m.efectoEnSaldo(), Long::sum);
        }
        // TreeMap: las filas se actualizan siempre en el mismo orden
        efecto.forEach(this::aplicarEfecto);
    }

    protected void aplicarEfecto(String cuenta, long centimos) {
        int filas = jdbc.sql("update cuenta set saldo = saldo + ? where id = ?").params(centimos, cuenta).update();
        if (filas != 1) {
            throw inexistente(cuenta);
        }
    }

    @Override
    public void guardar(Operacion op) {
        int nuevas = jdbc.sql("""
                        insert into operacion (id, tipo, clave, huella, fecha, revierte, revertida)
                        values (?, ?, ?, ?, ?, ?, ?)
                        on conflict (id) do update set revertida = excluded.revertida""")
                .params(op.id(), op.tipo().name(), op.clave(), op.huella(), utc(op.fecha()), op.revierte(), op.revertida())
                .update();
        boolean asientosGuardados = jdbc.sql("select count(*) from operacion_asiento where operacion_id = ?")
                .param(op.id()).query(Long.class).single() > 0;
        if (nuevas == 1 && !asientosGuardados) {
            for (int i = 0; i < op.asientos().size(); i++) {
                jdbc.sql("insert into operacion_asiento (operacion_id, orden, asiento_id) values (?, ?, ?)")
                        .params(op.id(), i, op.asientos().get(i).id()).update();
            }
        }
    }

    @Override
    public Optional<Operacion> operacion(UUID id) {
        return jdbc.sql("select * from operacion where id = ?").param(id)
                .query((rs, n) -> new Operacion(
                        rs.getObject("id", UUID.class),
                        TipoOperacion.valueOf(rs.getString("tipo")),
                        rs.getString("clave"),
                        rs.getString("huella"),
                        rs.getObject("fecha", OffsetDateTime.class).toInstant(),
                        List.of(),
                        rs.getObject("revierte", UUID.class),
                        rs.getBoolean("revertida")))
                .optional()
                .map(op -> new Operacion(op.id(), op.tipo(), op.clave(), op.huella(), op.fecha(), asientosDe(op.id()),
                        op.revierte(), op.revertida()));
    }

    private List<Asiento> asientosDe(UUID operacion) {
        record Fila(UUID asiento, String concepto, String cuenta, String lado, long centimos, String moneda) {
        }
        List<Fila> filas = jdbc.sql("""
                        select a.id, a.concepto, m.cuenta_id, m.lado, m.centimos, m.moneda
                        from operacion_asiento oa
                        join asiento a on a.id = oa.asiento_id
                        join movimiento m on m.asiento_id = a.id
                        where oa.operacion_id = ?
                        order by oa.orden, m.id""")
                .param(operacion)
                .query((rs, n) -> new Fila(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5), rs.getString(6)))
                .list();
        Map<UUID, List<Fila>> porAsiento = new LinkedHashMap<>();
        filas.forEach(f -> porAsiento.computeIfAbsent(f.asiento(), k -> new ArrayList<>()).add(f));
        List<Asiento> asientos = new ArrayList<>();
        porAsiento.forEach((id, lineas) -> asientos.add(new Asiento(id, lineas.getFirst().concepto(), lineas.stream()
                .map(f -> new Movimiento(f.cuenta(), Lado.valueOf(f.lado()), Dinero.centimos(f.centimos(), Moneda.valueOf(f.moneda()))))
                .toList())));
        return asientos;
    }

    @Override
    public Optional<Operacion> operacionPorClave(String clave, Instant vigenteDesde) {
        return jdbc.sql("select operacion_id from idempotencia where clave = ? and fecha > ?")
                .params(clave, utc(vigenteDesde))
                .query(UUID.class).optional()
                .flatMap(this::operacion);
    }

    /** Inserta la clave, o la reutiliza solo si la anterior ya venció. Si otra transacción la tomó, devuelve false. */
    @Override
    public boolean reservarClave(String clave, UUID operacion, Instant fecha, Instant vigenteDesde) {
        return jdbc.sql("""
                        insert into idempotencia (clave, operacion_id, fecha) values (?, ?, ?)
                        on conflict (clave) do update set operacion_id = excluded.operacion_id, fecha = excluded.fecha
                        where idempotencia.fecha <= ?""")
                .params(clave, operacion, utc(fecha), utc(vigenteDesde))
                .update() == 1;
    }

    @Override
    public void guardar(Credito credito) {
        jdbc.sql("""
                        insert into credito (id, cuenta_id, datos) values (?, ?, cast(? as jsonb))
                        on conflict (id) do update set datos = excluded.datos""")
                .params(credito.id(), credito.cuenta(), aJson(credito))
                .update();
    }

    @Override
    public Optional<Credito> credito(UUID id) {
        return jdbc.sql("select datos from credito where id = ? for update").param(id)
                .query(String.class).optional().map(this::desdeJson);
    }

    @Override
    public List<Cuenta> cuentas() {
        return jdbc.sql("select * from cuenta order by id").query(LibroPostgres::cuenta).list();
    }

    @Override
    public List<Credito> creditos() {
        return jdbc.sql("select datos from credito order by id").query(String.class).list().stream().map(this::desdeJson).toList();
    }

    @Override
    public Map<String, Long> saldosSegunMovimientos() {
        Map<String, Long> saldos = new TreeMap<>();
        jdbc.sql("""
                        select c.id, coalesce(sum(case m.lado when 'HABER' then m.centimos else -m.centimos end), 0)
                        from cuenta c left join movimiento m on m.cuenta_id = c.id
                        group by c.id""")
                .query((ResultSet rs) -> {
                    saldos.put(rs.getString(1), rs.getLong(2));
                });
        return saldos;
    }

    @Override
    public Map<Moneda, Long> descuadrePorMoneda() {
        Map<Moneda, Long> descuadre = new EnumMap<>(Moneda.class);
        for (Moneda m : Moneda.values()) {
            descuadre.put(m, 0L);
        }
        jdbc.sql("select moneda, sum(case lado when 'HABER' then centimos else -centimos end) from movimiento group by moneda")
                .query((ResultSet rs) -> {
                    descuadre.put(Moneda.valueOf(rs.getString(1).strip()), rs.getLong(2));
                });
        return descuadre;
    }

    // ---------------------------------------------------------------- utilidades

    protected static Cuenta cuenta(ResultSet rs, int fila) throws SQLException {
        Moneda moneda = Moneda.valueOf(rs.getString("moneda").strip());
        return new Cuenta(rs.getString("id"), rs.getString("titular"), moneda,
                Dinero.centimos(rs.getLong("limite_sobregiro"), moneda),
                Dinero.centimos(rs.getLong("saldo"), moneda),
                rs.getBoolean("interna"));
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }

    private static OperacionRechazada inexistente(String id) {
        return new OperacionRechazada(Motivo.CUENTA_INEXISTENTE, "No existe la cuenta " + id);
    }

    private String aJson(Credito credito) {
        try {
            return json.writeValueAsString(credito);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Credito desdeJson(String texto) {
        try {
            return json.readValue(texto, Credito.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
