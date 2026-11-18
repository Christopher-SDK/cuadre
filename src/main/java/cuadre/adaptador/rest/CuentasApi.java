package cuadre.adaptador.rest;

import cuadre.adaptador.rest.Vistas.AuditoriaVista;
import cuadre.adaptador.rest.Vistas.CuentaVista;
import cuadre.adaptador.rest.Vistas.OperacionVista;
import cuadre.dominio.cuenta.Cuenta;
import cuadre.dominio.cuenta.ServicioCuentas;
import cuadre.dominio.cuenta.SolicitudTransferencia;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.dinero.Moneda;
import cuadre.dominio.libro.Auditor;
import cuadre.dominio.libro.Banco;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static cuadre.adaptador.rest.ErroresApi.requerido;

@RestController
@RequestMapping("/api")
class CuentasApi {

    record AbrirCuenta(String titular, Moneda moneda, String limiteSobregiro) {
    }

    record Monto(String monto, Moneda moneda) {
        Dinero aDinero() {
            return Dinero.de(requerido(monto, "monto"), requerido(moneda, "moneda"));
        }
    }

    record Transferencia(String origen, String destino, String monto, Moneda moneda) {
    }

    private final ServicioCuentas cuentas;
    private final Banco banco;

    CuentasApi(ServicioCuentas cuentas, Banco banco) {
        this.cuentas = cuentas;
        this.banco = banco;
    }

    @PostMapping("/cuentas")
    ResponseEntity<CuentaVista> abrir(@RequestBody AbrirCuenta pedido) {
        Moneda moneda = requerido(pedido.moneda(), "moneda");
        Dinero limite = pedido.limiteSobregiro() == null ? Dinero.cero(moneda) : Dinero.de(pedido.limiteSobregiro(), moneda);
        Cuenta cuenta = cuentas.abrir(requerido(pedido.titular(), "titular"), moneda, limite);
        return ResponseEntity.created(URI.create("/api/cuentas/" + cuenta.id())).body(CuentaVista.de(cuenta));
    }

    @GetMapping("/cuentas/{id}")
    CuentaVista consultar(@PathVariable String id) {
        return CuentaVista.de(cuentas.consultar(id));
    }

    @PostMapping("/cuentas/{id}/depositos")
    @ResponseStatus(HttpStatus.CREATED)
    OperacionVista depositar(@PathVariable String id, @RequestBody Monto pedido) {
        return OperacionVista.de(cuentas.depositar(id, pedido.aDinero()));
    }

    @PostMapping("/cuentas/{id}/retiros")
    @ResponseStatus(HttpStatus.CREATED)
    OperacionVista retirar(@PathVariable String id, @RequestBody Monto pedido) {
        return OperacionVista.de(cuentas.retirar(id, pedido.aDinero()));
    }

    /** Un reintento con la misma Idempotency-Key devuelve la misma operación, sin mover dinero otra vez. */
    @PostMapping("/transferencias")
    @ResponseStatus(HttpStatus.CREATED)
    OperacionVista transferir(@RequestHeader("Idempotency-Key") String clave, @RequestBody Transferencia pedido) {
        Dinero monto = Dinero.de(requerido(pedido.monto(), "monto"), requerido(pedido.moneda(), "moneda"));
        return OperacionVista.de(cuentas.transferir(new SolicitudTransferencia(clave,
                requerido(pedido.origen(), "origen"), requerido(pedido.destino(), "destino"), monto)));
    }

    @PostMapping("/operaciones/{id}/reverso")
    @ResponseStatus(HttpStatus.CREATED)
    OperacionVista revertir(@PathVariable UUID id) {
        return OperacionVista.de(cuentas.revertir(id));
    }

    @GetMapping("/auditoria")
    AuditoriaVista auditoria() {
        List<String> hallazgos = banco.leer(Auditor::verificar);
        return new AuditoriaVista(hallazgos.isEmpty(), hallazgos);
    }
}
