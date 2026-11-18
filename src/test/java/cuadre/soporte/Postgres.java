package cuadre.soporte;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import cuadre.dominio.libro.Banco;
import cuadre.mutantes.Bug;
import cuadre.mutantes.Ensamble;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * Un PostgreSQL real (Testcontainers) compartido por todas las pruebas que lo necesitan.
 * Se levanta una sola vez por ejecución y cada prueba empieza con el libro vacío.
 */
public final class Postgres {

    public static final String IMAGEN = "postgres:17-alpine";

    private static PostgreSQLContainer<?> contenedor;
    private static HikariDataSource dataSource;

    private Postgres() {
    }

    public static synchronized PostgreSQLContainer<?> contenedor() {
        if (contenedor == null) {
            contenedor = new PostgreSQLContainer<>(IMAGEN).withCommand("postgres", "-c", "max_connections=200", "-c", "deadlock_timeout=50ms");
            contenedor.start();
            Flyway.configure().dataSource(contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword())
                    .load().migrate();
        }
        return contenedor;
    }

    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            PostgreSQLContainer<?> c = contenedor();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(c.getJdbcUrl());
            config.setUsername(c.getUsername());
            config.setPassword(c.getPassword());
            config.setMaximumPoolSize(40);
            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    /** Banco sobre PostgreSQL con el libro vacío y el bug que indique CUADRE_BUG. */
    public static Banco bancoVacio() {
        JdbcClient jdbc = JdbcClient.create(dataSource());
        jdbc.sql("truncate idempotencia, operacion_asiento, operacion, movimiento, asiento, credito").update();
        jdbc.sql("delete from cuenta where not interna").update();
        jdbc.sql("update cuenta set saldo = 0").update();
        return Ensamble.bancoPostgres(Bug.activo(), dataSource(), new ObjectMapper().findAndRegisterModules());
    }
}
