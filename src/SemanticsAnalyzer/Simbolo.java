package SemanticsAnalyzer;

import LexicalAnalyzer.TokenType;
import java.util.ArrayList;
import java.util.List;

public class Simbolo {
    public final String nome;
    public final TipoSimbolo categoria;
    public TokenType tipo;

    public final List<Simbolo> parametros = new ArrayList<>();

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
                ", parametros=" + parametros.size() +
                '}';
    }
}