package br.edu.atitus.orderapi;

import br.edu.atitus.orderapi.services.PricingCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingCalculatorTest {
    @Test void semPersonalizacaoNaoHaAcrescimo() { assertEquals(100.0, PricingCalculator.unitPrice(100.0, null, null)); }
    @Test void nomeAcrescenta10() { assertEquals(110.0, PricingCalculator.unitPrice(100.0, "SILVA", null)); }
    @Test void nomeENumeroAcrescentam20() { assertEquals(120.0, PricingCalculator.unitPrice(100.0, "SILVA", "10")); }
    @Test void camposEmBrancoSaoIgnorados() { assertEquals(100.0, PricingCalculator.unitPrice(100.0, "  ", "")); }
    @Test void arredondaPara2Casas() { assertEquals(33.33, PricingCalculator.round2(33.333333)); }
}
