package cuadre.dominio.libro;

import cuadre.dominio.credito.Credito;
import cuadre.dominio.cuenta.Cuenta;
import cuadre.dominio.dinero.Moneda;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static cuadre.dominio.cuenta.CuentasInternas.cartera;
import static cuadre.dominio.cuenta.CuentasInternas.transito;

/** Invariantes contables que deben cumplirse siempre, después de cualquier secuencia de operaciones. */
public final class Auditor {

    private Auditor() {
    }

    /** Lista de hallazgos en lenguaje de negocio. Vacía = el banco cuadra. */
    public static List<String> verificar(Libro libro) {
        List<String> hallazgos = new ArrayList<>();
        libro.descuadrePorMoneda().forEach((moneda, descuadre) -> {
            if (descuadre != 0) {
                hallazgos.add("El libro mayor en " + moneda + " no cuadra: debe − haber = " + descuadre + " céntimos");
            }
        });
        Map<String, Long> segunMovimientos = libro.saldosSegunMovimientos();
        Map<String, Cuenta> cuentas = new TreeMap<>();
        for (Cuenta c : libro.cuentas()) {
            cuentas.put(c.id(), c);
            long esperado = segunMovimientos.getOrDefault(c.id(), 0L);
            if (c.saldo().centimos() != esperado) {
                hallazgos.add("La cuenta " + c.id() + " muestra " + c.saldo() + " pero sus movimientos suman "
                        + esperado + " céntimos");
            }
            if (!c.admiteSaldo(c.saldo())) {
                hallazgos.add("La cuenta " + c.id() + " quedó en " + c.saldo() + ", por debajo de su límite de "
                        + c.limiteSobregiro());
            }
        }
        Map<Moneda, Long> carteraEsperada = new EnumMap<>(Moneda.class);
        for (Credito credito : libro.creditos()) {
            carteraEsperada.merge(credito.saldoPendiente().moneda(), credito.saldoPendiente().centimos(), Long::sum);
        }
        for (Moneda m : Moneda.values()) {
            long enTransito = cuentas.get(transito(m)).saldo().centimos();
            if (enTransito != 0) {
                hallazgos.add("Quedaron " + enTransito + " céntimos en la cuenta puente " + transito(m)
                        + ": dinero que salió de una cuenta y no llegó a ninguna");
            }
            long cartera = -cuentas.get(cartera(m)).saldo().centimos();
            long esperada = carteraEsperada.getOrDefault(m, 0L);
            if (cartera != esperada) {
                hallazgos.add("La cartera de créditos en " + m + " es " + cartera + " céntimos pero los saldos pendientes suman " + esperada);
            }
        }
        return hallazgos;
    }
}
