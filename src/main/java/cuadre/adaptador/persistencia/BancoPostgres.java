package cuadre.adaptador.persistencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import cuadre.dominio.libro.Banco;
import cuadre.dominio.libro.Libro;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.function.Function;

/** Banco sobre PostgreSQL: cada operación de negocio es una transacción READ COMMITTED con bloqueos explícitos. */
public class BancoPostgres implements Banco {

    private final JdbcClient jdbc;
    private final TransactionTemplate transacciones;
    private final ObjectMapper json;

    public BancoPostgres(DataSource dataSource, ObjectMapper json) {
        this.jdbc = JdbcClient.create(dataSource);
        this.transacciones = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.transacciones.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.json = json;
    }

    @Override
    public <T> T enTransaccion(Function<Libro, T> trabajo) {
        return transacciones.execute(estado -> trabajo.apply(libro(jdbc, json)));
    }

    protected LibroPostgres libro(JdbcClient jdbc, ObjectMapper json) {
        return new LibroPostgres(jdbc, json);
    }
}
