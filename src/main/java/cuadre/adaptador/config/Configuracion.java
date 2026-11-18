package cuadre.adaptador.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import cuadre.dominio.credito.ServicioCreditos;
import cuadre.dominio.cuenta.ServicioCuentas;
import cuadre.dominio.falla.PuntoDeFalla;
import cuadre.dominio.libro.Banco;
import cuadre.mutantes.Bug;
import cuadre.mutantes.Ensamble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration
class Configuracion {

    private static final Logger log = LoggerFactory.getLogger(Configuracion.class);

    @Bean
    Bug bug(CuadreProperties propiedades) {
        Bug bug = Bug.desde(propiedades.bug());
        if (bug != Bug.NINGUNO) {
            log.warn("Cuadre arrancó con el defecto sembrado {}. No usar para nada que no sea una demostración.", bug);
        }
        return bug;
    }

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    Banco banco(Bug bug, DataSource dataSource, ObjectMapper json) {
        return Ensamble.bancoPostgres(bug, dataSource, json);
    }

    @Bean
    ServicioCuentas servicioCuentas(Bug bug, Banco banco, CuadreProperties propiedades, Clock reloj) {
        return Ensamble.cuentas(bug, banco, propiedades.parametros(), reloj, PuntoDeFalla.NINGUNO);
    }

    @Bean
    ServicioCreditos servicioCreditos(Bug bug, Banco banco, CuadreProperties propiedades, Clock reloj) {
        return Ensamble.creditos(bug, banco, propiedades.parametros(), reloj);
    }
}
