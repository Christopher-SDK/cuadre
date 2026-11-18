package cuadre.unitario;

import io.qameta.allure.Epic;
import cuadre.dominio.dinero.Dinero;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

import java.math.BigDecimal;

import static cuadre.dominio.dinero.Moneda.PEN;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("1 · Unitarias")
@Label("Dinero: propiedades aritméticas")
class DineroPropiedades {

    static final long LIMITE = 1_000_000_000_000L;

    @Property
    @Label("El orden de las sumas no cambia el resultado")
    void sumaConmutativaYAsociativa(@ForAll @LongRange(min = -LIMITE, max = LIMITE) long a,
                                    @ForAll @LongRange(min = -LIMITE, max = LIMITE) long b,
                                    @ForAll @LongRange(min = -LIMITE, max = LIMITE) long c) {
        Dinero x = Dinero.centimos(a, PEN), y = Dinero.centimos(b, PEN), z = Dinero.centimos(c, PEN);
        assertThat(x.sumar(y)).isEqualTo(y.sumar(x));
        assertThat(x.sumar(y).sumar(z)).isEqualTo(x.sumar(y.sumar(z)));
    }

    @Property
    @Label("Sumar y luego restar lo mismo deja el monto intacto")
    void restarDeshaceSumar(@ForAll @LongRange(min = -LIMITE, max = LIMITE) long a,
                            @ForAll @LongRange(min = -LIMITE, max = LIMITE) long b) {
        Dinero x = Dinero.centimos(a, PEN), y = Dinero.centimos(b, PEN);
        assertThat(x.sumar(y).restar(y)).isEqualTo(x);
    }

    @Property
    @Label("Texto → Dinero → texto no pierde ni un céntimo")
    void idaYVueltaPorTexto(@ForAll @LongRange(min = -LIMITE, max = LIMITE) long centimos) {
        Dinero d = Dinero.centimos(centimos, PEN);
        assertThat(Dinero.de(d.aDecimal().toPlainString(), PEN)).isEqualTo(d);
        assertThat(d.aDecimal()).isEqualTo(BigDecimal.valueOf(centimos, 2));
    }
}
