package cuadre.adaptador.rest;

import cuadre.dominio.OperacionRechazada;
import cuadre.dominio.OperacionRechazada.Motivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Locale;

/**
 * Errores como RFC 9457 (application/problem+json), con el motivo de negocio en el campo {@code motivo}.
 * Los errores propios de Spring MVC (415, 405, 404 de ruta…) los resuelve la clase base con su código correcto.
 */
@RestControllerAdvice
class ErroresApi extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ErroresApi.class);

    static class PedidoInvalido extends RuntimeException {
        PedidoInvalido(String mensaje) {
            super(mensaje);
        }
    }

    static <T> T requerido(T valor, String campo) {
        if (valor == null || valor instanceof String s && s.isBlank()) {
            throw new PedidoInvalido("Falta el campo obligatorio '" + campo + "'");
        }
        return valor;
    }

    static HttpStatus estado(Motivo motivo) {
        return switch (motivo) {
            case MONTO_INVALIDO -> HttpStatus.BAD_REQUEST;
            case CUENTA_INEXISTENTE, OPERACION_INEXISTENTE, CREDITO_INEXISTENTE -> HttpStatus.NOT_FOUND;
            case CLAVE_REUTILIZADA, YA_REVERTIDA, CREDITO_CANCELADO -> HttpStatus.CONFLICT;
            case MONEDA_INCOMPATIBLE, MISMA_CUENTA, SALDO_INSUFICIENTE, NO_REVERSIBLE, CONDICIONES_INVALIDAS ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    @ExceptionHandler(OperacionRechazada.class)
    ProblemDetail rechazada(OperacionRechazada e) {
        ProblemDetail p = problema(estado(e.motivo()), e.getMessage(), e.motivo().name().toLowerCase(Locale.ROOT));
        p.setProperty("motivo", e.motivo().name());
        return p;
    }

    @ExceptionHandler(PedidoInvalido.class)
    ProblemDetail invalido(PedidoInvalido e) {
        return problema(HttpStatus.BAD_REQUEST, e.getMessage(), "pedido_invalido");
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException e, HttpHeaders headers,
                                                                          HttpStatusCode status, WebRequest request) {
        String detalle = e instanceof MissingRequestHeaderException falta
                ? "Falta la cabecera obligatoria '" + falta.getHeaderName() + "'"
                : "Pedido incompleto: " + e.getMessage();
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, detalle, "pedido_invalido"));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST,
                "El cuerpo no es un JSON válido o tiene un valor de tipo incorrecto", "pedido_invalido"));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        log.error("Error inesperado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno. No se aplicó ningún cambio.", "error_interno");
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle, String tipo) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setType(URI.create("urn:cuadre:error:" + tipo));
        return p;
    }
}
