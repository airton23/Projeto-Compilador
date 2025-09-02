package SemanticsAnalyzer;

import LexicalAnalyzer.TokenType;

public class Simbolo {
    public final String nome;
    public final TipoSimbolo categoria;
    public TokenType tipo; // INTEIRO, BOOLEANO, etc. Nulo para procedimento.

    public Simbolo(String nome, TipoSimbolo categoria, TokenType tipo) {
        this.nome = nome;
        this.categoria = categoria;
        this.tipo = tipo;
    }

    @Override
    public String toString() {
        return "Simbolo{" +
                "nome='" + nome + '\'' +
                ", categoria=" + categoria +
                ", tipo=" + tipo +
                '}';
    }
}