package cuadre.soporte;

import cuadre.adaptador.memoria.BancoEnMemoria;
import cuadre.dominio.credito.ServicioCreditos;
import cuadre.dominio.cuenta.Cuenta;
import cuadre.dominio.cuenta.ServicioCuentas;
import cuadre.dominio.dinero.Dinero;
import cuadre.dominio.libro.Auditor;
import cuadre.dominio.libro.Banco;
import cuadre.mutantes.Bug;
import cuadre.mutantes.Ensamble;
import cuadre.mutantes.Ensamble.Parametros;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static cuadre.dominio.cuenta.CuentasInternas.itf;
import static cuadre.dominio.dinero.Moneda.PEN;

/** El sistema real armado para una prueba, con el bug que indique CUADRE_BUG (o ninguno). */
public final class Sistema {

    public final Banco banco;
    public final RelojDePrueba reloj = new RelojDePrueba();
    public final FallaProgramada falla = new FallaProgramada();
    public final Parametros parametros;
    public final ServicioCuentas cuentas;
    public final ServicioCreditos creditos;

    public Sistema(BigDecimal tasaItf) {
        this(new BancoEnMemoria(), tasaItf);
    }

    public Sistema(Banco banco, BigDecimal tasaItf) {
        this.banco = banco;
        this.parametros = Parametros.porDefecto().conTasaItf(tasaItf);
        Bug bug = Bug.activo();
        this.cuentas = Ensamble.cuentas(bug, banco, parametros, reloj, falla);
        this.creditos = Ensamble.creditos(bug, banco, parametros, reloj);
    }

    public static Dinero soles(String monto) {
        return Dinero.de(monto, PEN);
    }

    public static Dinero centimos(long centimos) {
        return Dinero.centimos(centimos, PEN);
    }

    public String abrir(long limiteCentimos) {
        return cuentas.abrir("Cliente ficticio", PEN, centimos(limiteCentimos)).id();
    }

    public long saldo(String cuenta) {
        return banco.leer(libro -> libro.cuenta(cuenta).saldo().centimos());
    }

    public long itfRecaudado() {
        return saldo(itf(PEN));
    }

    /** Saldo de todas las cuentas, internas incluidas. */
    public Map<String, Long> foto() {
        Map<String, Long> foto = new TreeMap<>();
        banco.leer(libro -> libro.cuentas()).forEach(c -> foto.put(c.id(), c.saldo().centimos()));
        return foto;
    }

    /** Dinero de clientes más el impuesto recaudado: no cambia con operaciones internas. */
    public long dineroInterno() {
        return banco.leer(libro -> libro.cuentas()).stream()
                .filter(c -> !c.interna() || c.id().equals(itf(PEN)))
                .mapToLong(c -> c.saldo().centimos()).sum();
    }

    public List<Cuenta> cuentasDeClientes() {
        return banco.leer(libro -> libro.cuentas()).stream().filter(c -> !c.interna()).toList();
    }

    public List<String> auditar() {
        return banco.leer(Auditor::verificar);
    }
}
