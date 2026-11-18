package cuadre.api;

import cuadre.soporte.Postgres;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

/** La API vista desde afuera: códigos HTTP, validaciones y errores en formato problem+json. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiPruebas.BaseDeDatos.class)
@DisplayName("API REST")
class ApiPruebas {

    @TestConfiguration(proxyBeanMethods = false)
    static class BaseDeDatos {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return Postgres.contenedor();
        }
    }

    @LocalServerPort
    int puerto;

    @BeforeEach
    void configurar() {
        Postgres.bancoVacio();
        RestAssured.port = puerto;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    static RequestSpecification json() {
        return given().contentType(ContentType.JSON).accept(ContentType.JSON);
    }

    static String abrirCuenta(String limite) {
        return json().body("""
                        {"titular": "Cliente ficticio", "moneda": "PEN", "limiteSobregiro": "%s"}""".formatted(limite))
                .post("/api/cuentas")
                .then().statusCode(201).header("Location", startsWith("/api/cuentas/C-"))
                .extract().path("id");
    }

    static void depositar(String cuenta, String monto) {
        json().body("{\"monto\": \"" + monto + "\", \"moneda\": \"PEN\"}")
                .post("/api/cuentas/{id}/depositos", cuenta).then().statusCode(201);
    }

    static String transferencia(String origen, String destino, String monto) {
        return "{\"origen\": \"%s\", \"destino\": \"%s\", \"monto\": \"%s\", \"moneda\": \"PEN\"}".formatted(origen, destino, monto);
    }

    @Nested
    @DisplayName("Cuentas")
    class Cuentas {

        @Test
        @DisplayName("Abrir una cuenta devuelve 201, su ubicación y saldo 0.00")
        void abrir() {
            String id = abrirCuenta("0");
            given().get("/api/cuentas/{id}", id).then().statusCode(200)
                    .body("saldo", equalTo("0.00")).body("moneda", equalTo("PEN"));
        }

        @Test
        @DisplayName("Un depósito con más de dos decimales se rechaza con 400 y motivo MONTO_INVALIDO")
        void montoInvalido() {
            String id = abrirCuenta("0");
            json().body("{\"monto\": \"10.001\", \"moneda\": \"PEN\"}").post("/api/cuentas/{id}/depositos", id)
                    .then().statusCode(400).contentType("application/problem+json")
                    .body("motivo", equalTo("MONTO_INVALIDO"));
        }

        @Test
        @DisplayName("Depositar dólares en una cuenta en soles se rechaza con 422")
        void monedaIncompatible() {
            String id = abrirCuenta("0");
            json().body("{\"monto\": \"10\", \"moneda\": \"USD\"}").post("/api/cuentas/{id}/depositos", id)
                    .then().statusCode(422).body("motivo", equalTo("MONEDA_INCOMPATIBLE"));
        }

        @Test
        @DisplayName("Una moneda inexistente o un JSON roto se rechazan con 400")
        void pedidoIlegible() {
            String id = abrirCuenta("0");
            json().body("{\"monto\": \"10\", \"moneda\": \"EUR\"}").post("/api/cuentas/{id}/depositos", id).then().statusCode(400);
            json().body("{monto").post("/api/cuentas/{id}/depositos", id).then().statusCode(400);
            json().body("{\"moneda\": \"PEN\"}").post("/api/cuentas/{id}/depositos", id).then().statusCode(400)
                    .body("detail", equalTo("Falta el campo obligatorio 'monto'"));
        }

        @Test
        @DisplayName("Una cuenta inexistente responde 404; las cuentas internas del banco tampoco existen para el cliente")
        void inexistente() {
            given().get("/api/cuentas/{id}", "C-NOEXISTE").then().statusCode(404).body("motivo", equalTo("CUENTA_INEXISTENTE"));
            given().get("/api/cuentas/{id}", "ITF-PEN").then().statusCode(404);
        }

        @Test
        @DisplayName("Retirar más de lo disponible responde 422 SALDO_INSUFICIENTE y no cambia el saldo")
        void saldoInsuficiente() {
            String id = abrirCuenta("0");
            depositar(id, "100.00");
            json().body("{\"monto\": \"100.01\", \"moneda\": \"PEN\"}").post("/api/cuentas/{id}/retiros", id)
                    .then().statusCode(422).body("motivo", equalTo("SALDO_INSUFICIENTE"));
            given().get("/api/cuentas/{id}", id).then().body("saldo", equalTo("100.00"));
        }
    }

    @Nested
    @DisplayName("Transferencias")
    class Transferencias {

        @Test
        @DisplayName("Sin cabecera Idempotency-Key la transferencia se rechaza con 400")
        void sinClave() {
            String a = abrirCuenta("0"), b = abrirCuenta("0");
            json().body(transferencia(a, b, "1")).post("/api/transferencias")
                    .then().statusCode(400).body("detail", equalTo("Falta la cabecera obligatoria 'Idempotency-Key'"));
        }

        @Test
        @DisplayName("Reenviar la misma transferencia con la misma clave devuelve la misma operación y cobra una sola vez")
        void idempotente() {
            String a = abrirCuenta("0"), b = abrirCuenta("0");
            depositar(a, "5000.00");
            String clave = UUID.randomUUID().toString();
            String primera = json().header("Idempotency-Key", clave).body(transferencia(a, b, "2000.00"))
                    .post("/api/transferencias").then().statusCode(201).extract().path("id");
            json().header("Idempotency-Key", clave).body(transferencia(a, b, "2000.00"))
                    .post("/api/transferencias").then().statusCode(201).body("id", equalTo(primera));

            given().get("/api/cuentas/{id}", a).then().body("saldo", equalTo("2999.90")); // 2000 + ITF 0.10
            given().get("/api/cuentas/{id}", b).then().body("saldo", equalTo("2000.00"));
        }

        @Test
        @DisplayName("Reutilizar una clave para otra transferencia responde 409 CLAVE_REUTILIZADA")
        void claveReutilizada() {
            String a = abrirCuenta("0"), b = abrirCuenta("0");
            depositar(a, "100.00");
            json().header("Idempotency-Key", "k-1").body(transferencia(a, b, "10")).post("/api/transferencias").then().statusCode(201);
            json().header("Idempotency-Key", "k-1").body(transferencia(a, b, "11")).post("/api/transferencias")
                    .then().statusCode(409).body("motivo", equalTo("CLAVE_REUTILIZADA"));
        }

        @Test
        @DisplayName("Transferir a la misma cuenta responde 422")
        void mismaCuenta() {
            String a = abrirCuenta("0");
            json().header("Idempotency-Key", "k-2").body(transferencia(a, a, "1")).post("/api/transferencias")
                    .then().statusCode(422).body("motivo", equalTo("MISMA_CUENTA"));
        }

        @Test
        @DisplayName("Un reverso deja ambos saldos como antes; un segundo reverso responde 409")
        void reverso() {
            String a = abrirCuenta("0"), b = abrirCuenta("0");
            depositar(a, "3000.00");
            String op = json().header("Idempotency-Key", "k-3").body(transferencia(a, b, "1500.00"))
                    .post("/api/transferencias").then().statusCode(201).extract().path("id");
            given().post("/api/operaciones/{id}/reverso", op).then().statusCode(201).body("revierte", equalTo(op));
            given().get("/api/cuentas/{id}", a).then().body("saldo", equalTo("3000.00"));
            given().get("/api/cuentas/{id}", b).then().body("saldo", equalTo("0.00"));
            given().post("/api/operaciones/{id}/reverso", op).then().statusCode(409).body("motivo", equalTo("YA_REVERTIDA"));
            given().post("/api/operaciones/{id}/reverso", "no-es-uuid").then().statusCode(400);
            given().get("/api/operaciones/{id}/reverso", op).then().statusCode(405);
            given().contentType("text/plain").body("hola").post("/api/cuentas/{id}/depositos", a).then().statusCode(415);
        }

        @Test
        @DisplayName("Después de todo, la auditoría confirma que el libro cuadra")
        void auditoria() {
            String a = abrirCuenta("0"), b = abrirCuenta("500");
            depositar(a, "1000.00");
            json().header("Idempotency-Key", "k-4").body(transferencia(a, b, "999.95")).post("/api/transferencias").then().statusCode(201);
            given().get("/api/auditoria").then().statusCode(200).body("cuadra", equalTo(true)).body("hallazgos", hasSize(0));
        }
    }

    @Nested
    @DisplayName("Créditos")
    class Creditos {

        static final String CASO_DORADO = """
                {"monto": "5000.00", "moneda": "PEN", "plazoMeses": 6, "tea": "0.25",
                 "tasaDesgravamenMensual": "0.0005", "comisionMensual": "5.00", "comisionDesembolso": "50.00"%s}""";

        @Test
        @DisplayName("La simulación devuelve el cronograma del caso dorado 3 y su TCEA de 32,80 %")
        void simulacion() {
            json().body(CASO_DORADO.formatted("")).post("/api/creditos/simulacion").then().statusCode(200)
                    .body("cuotas", hasSize(6))
                    .body("cuotas[0].total", equalTo("896.43"))
                    .body("cuotas[5].saldoFinal", equalTo("0.00"))
                    .body("tceaPorcentaje", equalTo("32.8006"));
        }

        @Test
        @DisplayName("Desembolsar, pagar una cuota y prepagar el resto deja el crédito cancelado")
        void cicloDeVida() {
            String cuenta = abrirCuenta("0");
            String credito = json().body(CASO_DORADO.formatted(", \"cuenta\": \"" + cuenta + "\""))
                    .post("/api/creditos").then().statusCode(201).extract().path("id");
            given().get("/api/cuentas/{id}", cuenta).then().body("saldo", equalTo("4950.00"));
            depositar(cuenta, "200.00"); // 4 950,00 − 896,43 de la cuota no alcanzan para prepagar 4 204,92

            given().post("/api/creditos/{id}/pagos", credito).then().statusCode(201);
            json().body("{\"monto\": \"4204.92\", \"moneda\": \"PEN\"}").post("/api/creditos/{id}/prepagos", credito)
                    .then().statusCode(201);

            given().get("/api/creditos/{id}", credito).then().statusCode(200)
                    .body("cancelado", equalTo(true)).body("saldoPendiente", equalTo("0.00"));
            given().post("/api/creditos/{id}/pagos", credito).then().statusCode(409).body("motivo", equalTo("CREDITO_CANCELADO"));
            given().get("/api/auditoria").then().body("cuadra", equalTo(true));
        }

        @Test
        @DisplayName("Condiciones fuera de rango se rechazan con 422 y una tasa ilegible con 400")
        void condicionesInvalidas() {
            json().body("{\"monto\": \"1000\", \"moneda\": \"PEN\", \"plazoMeses\": 0, \"tea\": \"0.2\"}")
                    .post("/api/creditos/simulacion").then().statusCode(422).body("motivo", equalTo("CONDICIONES_INVALIDAS"));
            json().body("{\"monto\": \"1000\", \"moneda\": \"PEN\", \"plazoMeses\": 12, \"tea\": \"veinte\"}")
                    .post("/api/creditos/simulacion").then().statusCode(400);
        }

        @Test
        @DisplayName("Un crédito inexistente responde 404")
        void inexistente() {
            given().get("/api/creditos/{id}", UUID.randomUUID()).then().statusCode(404).body("motivo", equalTo("CREDITO_INEXISTENTE"));
        }
    }
}
