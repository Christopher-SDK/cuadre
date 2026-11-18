package cuadre.adaptador.rest;

import cuadre.adaptador.rest.Vistas.CreditoVista;
import cuadre.adaptador.rest.Vistas.OperacionVista;
import cuadre.adaptador.rest.Vistas.SimulacionVista;
import cuadre.dominio.credito.CondicionesCredito;
import cuadre.dominio.credito.Credito;
import cuadre.dominio.credito.ServicioCreditos;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.dinero.Moneda;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import static cuadre.adaptador.rest.ErroresApi.requerido;

@RestController
@RequestMapping("/api/creditos")
class CreditosApi {

    /** Tasas como fracción en texto: "0.25" es 25 %. */
    record SolicitudCredito(String cuenta, String monto, Moneda moneda, Integer plazoMeses, String tea,
                            String tasaDesgravamenMensual, String comisionMensual, String comisionDesembolso) {

        CondicionesCredito condiciones() {
            Moneda m = requerido(moneda, "moneda");
            return new CondicionesCredito(Dinero.de(requerido(monto, "monto"), m), requerido(plazoMeses, "plazoMeses"),
                    tasa(requerido(tea, "tea"), "tea"), tasa(opcional(tasaDesgravamenMensual), "tasaDesgravamenMensual"),
                    Dinero.de(opcional(comisionMensual), m), Dinero.de(opcional(comisionDesembolso), m));
        }

        private static String opcional(String valor) {
            return valor == null ? "0" : valor;
        }

        private static BigDecimal tasa(String valor, String campo) {
            try {
                return new BigDecimal(valor.strip());
            } catch (NumberFormatException e) {
                throw new ErroresApi.PedidoInvalido("El campo '" + campo + "' debe ser un número decimal, por ejemplo \"0.25\"");
            }
        }
    }

    record Monto(String monto, Moneda moneda) {
    }

    private final ServicioCreditos creditos;

    CreditosApi(ServicioCreditos creditos) {
        this.creditos = creditos;
    }

    @PostMapping("/simulacion")
    SimulacionVista simular(@RequestBody SolicitudCredito pedido) {
        return SimulacionVista.de(creditos.simular(pedido.condiciones()));
    }

    @PostMapping
    ResponseEntity<CreditoVista> desembolsar(@RequestBody SolicitudCredito pedido) {
        Credito credito = creditos.desembolsar(requerido(pedido.cuenta(), "cuenta"), pedido.condiciones());
        return ResponseEntity.created(URI.create("/api/creditos/" + credito.id())).body(CreditoVista.de(credito));
    }

    @GetMapping("/{id}")
    CreditoVista consultar(@PathVariable UUID id) {
        return CreditoVista.de(creditos.credito(id));
    }

    @PostMapping("/{id}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    OperacionVista pagarCuota(@PathVariable UUID id) {
        return OperacionVista.de(creditos.pagarCuota(id));
    }

    @PostMapping("/{id}/prepagos")
    @ResponseStatus(HttpStatus.CREATED)
    OperacionVista prepagar(@PathVariable UUID id, @RequestBody Monto pedido) {
        return OperacionVista.de(creditos.prepagar(id,
                Dinero.de(requerido(pedido.monto(), "monto"), requerido(pedido.moneda(), "moneda"))));
    }
}
