package cuadre.dominio.credito;

import cuadre.dominio.dinero.Dinero;

import java.util.List;

public record Cronograma(List<Cuota> cuotas) {

    public Cronograma {
        cuotas = List.copyOf(cuotas);
    }

    public Dinero totalAmortizado() {
        return cuotas.stream().map(Cuota::amortizacion).reduce(Dinero::sumar).orElseThrow();
    }

    public Dinero saldoFinal() {
        return cuotas.getLast().saldoFinal();
    }

    public List<Dinero> pagos() {
        return cuotas.stream().map(Cuota::total).toList();
    }
}
