package cuadre.mutantes;

import com.fasterxml.jackson.databind.ObjectMapper;
import cuadre.adaptador.persistencia.BancoPostgres;
import cuadre.dominio.credito.CalculadoraFrancesa;
import cuadre.dominio.credito.ServicioCreditos;
import cuadre.dominio.cuenta.ServicioCuentas;
import cuadre.dominio.falla.PuntoDeFalla;
import cuadre.dominio.impuesto.ReglaItf;
import cuadre.dominio.libro.Banco;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;

/**
 * Punto único donde se arma el sistema. Es el único lugar que sabe qué bug está activo:
 * el dominio no tiene ni un {@code if} sobre bugs.
 */
public final class Ensamble {

    /** Parámetros regulatorios y operativos; en la aplicación vienen de {@code application.yml}. */
    public record Parametros(BigDecimal tasaItf, ReglaItf.Redondeo redondeoItf, RoundingMode redondeoInteres,
                             long ventanaIdempotenciaHoras) {

        public static Parametros porDefecto() {
            return new Parametros(new BigDecimal("0.00005"), ReglaItf.Redondeo.LEY_PERUANA, RoundingMode.HALF_UP, 24);
        }

        public Parametros conTasaItf(BigDecimal tasa) {
            return new Parametros(tasa, redondeoItf, redondeoInteres, ventanaIdempotenciaHoras);
        }
    }

    private Ensamble() {
    }

    public static Banco bancoPostgres(Bug bug, DataSource dataSource, ObjectMapper json) {
        return bug == Bug.CARRERA ? new LecturaSinBloqueo(dataSource, json) : new BancoPostgres(dataSource, json);
    }

    public static ServicioCuentas cuentas(Bug bug, Banco banco, Parametros p, Clock reloj, PuntoDeFalla falla) {
        ReglaItf itf = bug == Bug.IMPUESTO_DOBLE
                ? new ItfSobreDebitosYCreditos(p.tasaItf(), p.redondeoItf())
                : new ReglaItf(p.tasaItf(), p.redondeoItf());
        Duration ventana = bug == Bug.DOBLE_CARGO
                ? VentanaDeReintentos.desdeHoras(p.ventanaIdempotenciaHoras())
                : Duration.ofHours(p.ventanaIdempotenciaHoras());
        return switch (bug) {
            case REVERSO_PARCIAL -> new ReversoSinItf(banco, itf, reloj, ventana, falla);
            case TIMEOUT_A_MEDIAS -> new TransferenciaEnDosTramos(banco, itf, reloj, ventana, falla);
            default -> new ServicioCuentas(banco, itf, reloj, ventana, falla);
        };
    }

    public static ServicioCreditos creditos(Bug bug, Banco banco, Parametros p, Clock reloj) {
        return new ServicioCreditos(banco, calculadora(bug, p), reloj);
    }

    public static CalculadoraFrancesa calculadora(Bug bug, Parametros p) {
        return switch (bug) {
            case REDONDEO -> new InteresSinCobroDeFracciones();
            case CRONOGRAMA -> new CuotaFijaHastaElFinal(p.redondeoInteres());
            default -> new CalculadoraFrancesa(p.redondeoInteres());
        };
    }
}
