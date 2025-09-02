package SemanticsAnalyzer;

import LexicalAnalyzer.Token;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class TabelaDeSimbolos {
    private final Stack<Map<String, Simbolo>> tabela;

    public TabelaDeSimbolos() {
        this.tabela = new Stack<>();
        abrirEscopo(); // Abre o escopo global
    }

    public void abrirEscopo() {
        tabela.push(new HashMap<>());
    }

    public void fecharEscopo() {
        if (!tabela.isEmpty()) {
            tabela.pop();
        }
    }

    public void inserir(Simbolo simbolo) {
        Map<String, Simbolo> escopoAtual = tabela.peek();
        if (escopoAtual.containsKey(simbolo.nome)) {
            // Lançaria um erro de dupla declaração
            throw new RuntimeException("Erro Semântico: Identificador '" + simbolo.nome + "' já declarado neste escopo.");
        }
        escopoAtual.put(simbolo.nome, simbolo);
    }

    public Simbolo buscar(String nome) {
        for (int i = tabela.size() - 1; i >= 0; i--) {
            if (tabela.get(i).containsKey(nome)) {
                return tabela.get(i).get(nome);
            }
        }
        return null; // Não encontrado
    }
}