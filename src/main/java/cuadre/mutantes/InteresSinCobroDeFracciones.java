package cuadre.mutantes;

import cuadre.dominio.credito.CalculadoraFrancesa;

import java.math.RoundingMode;

/**
 * REDONDEO. "Nunca cobres al cliente una fracción de céntimo que no devengó": el interés se trunca
 * en vez de redondearse. La última cuota igual cierra en cero, así que el cronograma "se ve bien";
 * pero cada mes con fracción ≥ 0,005 el interés queda un céntimo por debajo y el banco pierde ingresos
 * que su propio contrato pacta. Solo un cálculo independiente lo nota.
 */
class InteresSinCobroDeFracciones extends CalculadoraFrancesa {

    InteresSinCobroDeFracciones() {
        super(RoundingMode.DOWN);
    }
}
