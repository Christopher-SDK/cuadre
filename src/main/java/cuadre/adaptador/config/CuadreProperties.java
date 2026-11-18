package cuadre.adaptador.config;

import cuadre.dominio.impuesto.ReglaItf;
import cuadre.mutantes.Ensamble.Parametros;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Parámetros regulatorios de {@code application.yml}. Ninguna tasa ni regla de redondeo está escrita en el código. */
@ConfigurationProperties("cuadre")
public record CuadreProperties(String bug, Itf itf, Credito credito, Idempotencia idempotencia) {

    public record Itf(BigDecimal tasa, ReglaItf.Redondeo redondeo) {
    }

    public record Credito(RoundingMode redondeoInteres) {
    }

    public record Idempotencia(long ventanaHoras) {
    }

    public Parametros parametros() {
        return new Parametros(itf.tasa(), itf.redondeo(), credito.redondeoInteres(), idempotencia.ventanaHoras());
    }
}
