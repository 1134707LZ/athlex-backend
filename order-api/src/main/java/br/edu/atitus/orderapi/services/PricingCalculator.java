package br.edu.atitus.orderapi.services;

/** Regra de preço pura (sem dependências): acréscimos de personalização. Testável isoladamente. */
public final class PricingCalculator {
    public static final double PRECO_NOME = 10.0;
    public static final double PRECO_NUMERO = 10.0;

    private PricingCalculator() {}

    public static double acrescimo(String nome, String numero) {
        return (nome != null && !nome.isBlank() ? PRECO_NOME : 0) + (numero != null && !numero.isBlank() ? PRECO_NUMERO : 0);
    }

    public static double unitPrice(double base, String nome, String numero) {
        return base + acrescimo(nome, numero);
    }

    public static double round2(double v) { return Math.round(v * 100) / 100.0; }
}
