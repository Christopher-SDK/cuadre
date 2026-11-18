package cuadre.mutantes;

import com.fasterxml.jackson.databind.ObjectMapper;
import cuadre.adaptador.persistencia.BancoPostgres;
import cuadre.adaptador.persistencia.LibroPostgres;
import cuadre.dominio.cuenta.Cuenta;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CARRERA. "Optimización" para reducir la contención: las cuentas se leen sin FOR UPDATE y el saldo nuevo
 * se calcula en Java y se escribe tal cual (leer → modificar → escribir), sin bloqueo ni control de versión.
 * Con un solo usuario es indistinguible del código correcto. Con dos transferencias a la vez sobre la misma
 * cuenta, la segunda escritura pisa a la primera: el saldo deja de coincidir con el libro mayor.
 */
class LecturaSinBloqueo extends BancoPostgres {

    LecturaSinBloqueo(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json);
    }

    @Override
    protected LibroPostgres libro(JdbcClient jdbc, ObjectMapper json) {
        return new LibroPostgres(jdbc, json) {

            @Override
            public Map<String, Cuenta> bloquear(Collection<String> ids) {
                Map<String, Cuenta> cuentas = new LinkedHashMap<>();
                ids.forEach(id -> cuentas.put(id, cuenta(id)));
                return cuentas;
            }

            @Override
            protected void aplicarEfecto(String id, long centimos) {
                long saldo = cuenta(id).saldo().centimos();
                jdbc.sql("update cuenta set saldo = ? where id = ?").params(saldo + centimos, id).update();
            }
        };
    }
}
