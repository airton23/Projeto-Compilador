package LexicalAnalyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LexicalAnalyzer {
    private final String fonte;
    private final List<Token> tokens = new ArrayList<>();
    private int inicio = 0;
    private int atual = 0;
    private int linha = 1;
    private boolean hasErrors = false;

    private static final Map<String, TokenType> palavrasReservadas;
    static {
        palavrasReservadas = new HashMap<>();
        palavrasReservadas.put("programa", TokenType.PROGRAMA);
        palavrasReservadas.put("var", TokenType.VAR);
        palavrasReservadas.put("inteiro", TokenType.INTEIRO);
        palavrasReservadas.put("booleano", TokenType.BOOLEANO);
        palavrasReservadas.put("procedimento", TokenType.PROCEDIMENTO);
        palavrasReservadas.put("funcao", TokenType.FUNCAO);
        palavrasReservadas.put("inicio", TokenType.INICIO);
        palavrasReservadas.put("fim", TokenType.FIM);
        palavrasReservadas.put("se", TokenType.SE);
        palavrasReservadas.put("entao", TokenType.ENTAO);
        palavrasReservadas.put("senao", TokenType.SENAO);
        palavrasReservadas.put("enquanto", TokenType.ENQUANTO);
        palavrasReservadas.put("faca", TokenType.FACA);
        palavrasReservadas.put("escreva", TokenType.ESCREVA);
        palavrasReservadas.put("retorno", TokenType.RETORNO);
        palavrasReservadas.put("break", TokenType.BREAK);
        palavrasReservadas.put("continue", TokenType.CONTINUE);
        palavrasReservadas.put("verdadeiro", TokenType.VERDADEIRO);
        palavrasReservadas.put("falso", TokenType.FALSO);
        palavrasReservadas.put("nao", TokenType.NAO);
        palavrasReservadas.put("ou", TokenType.OU);
        palavrasReservadas.put("e", TokenType.E);
    }

    public LexicalAnalyzer(String fonte) {
        this.fonte = fonte;
    }

    public List<Token> escanearTokens() {
        while (!isAtEnd()) {
            inicio = atual;
            escanearToken();
        }
        tokens.add(new Token(TokenType.FIM_ARQUIVO, "", linha));
        return tokens;
    }

    private int indiceAtualToken = 0;

    public Token obterProximoToken() {
        if (tokens.isEmpty()) {
            escanearTokens();
        }
        if (indiceAtualToken < tokens.size()) {
            return tokens.get(indiceAtualToken++);
        }
        return tokens.get(tokens.size() - 1);
    }

    private boolean isAtEnd() {
        return atual >= fonte.length();
    }

    private void escanearToken() {
        char c = avancar();
        switch (c) {
            case '(': adicionarToken(TokenType.ABRE_PAREN); break;
            case ')': adicionarToken(TokenType.FECHA_PAREN); break;
            case ',': adicionarToken(TokenType.VIRGULA); break;
            case ';': adicionarToken(TokenType.PONTO_E_VIRGULA); break;
            case '.': adicionarToken(TokenType.PONTO); break;
            case '+': adicionarToken(TokenType.MAIS); break;
            case '-': adicionarToken(TokenType.MENOS); break;
            case '*': adicionarToken(TokenType.MULT); break;
            case ':':
                adicionarToken(nextToken('=') ? TokenType.ATRIBUICAO : TokenType.DOIS_PONTOS);
                break;
            case '<':
                if (nextToken('>')) adicionarToken(TokenType.DIFERENTE);
                else if (nextToken('=')) adicionarToken(TokenType.MENOR_IGUAL);
                else adicionarToken(TokenType.MENOR);
                break;
            case '>':
                adicionarToken(nextToken('=') ? TokenType.MAIOR_IGUAL : TokenType.MAIOR);
                break;
            case '=': adicionarToken(TokenType.IGUAL); break;
            case ' ':
            case '\r':
            case '\t':
                break; // ignorar espaços
            case '\n':
                linha++;
                break;
            case '/': adicionarToken(TokenType.DIVISAO); break;
            default:
                if (Character.isDigit(c)) {
                    numero();
                } else if (Character.isLetter(c)) {
                    identificador();
                } else {
                    adicionarToken(TokenType.ERRO);
                }
        }
    }

    private void numero() {
        while (Character.isDigit(peek())) avancar();

        if (Character.isLetter(peek()) || peek() == '_') {
            while (Character.isLetterOrDigit(peek()) || peek() == '_') {
                avancar();
            }
            adicionarToken(TokenType.ERRO);
        } else {
            adicionarToken(TokenType.NUMERO);
        }
    }

    private void identificador() {
        while (Character.isLetterOrDigit(peek()) || peek() == '_') avancar();
        String texto = fonte.substring(inicio, atual);
        TokenType tipo = palavrasReservadas.getOrDefault(texto, TokenType.ID);
        adicionarToken(tipo);
    }

    private char avancar() {
        return fonte.charAt(atual++);
    }

    private boolean nextToken(char esperado) {
        if (isAtEnd()) return false;
        if (fonte.charAt(atual) != esperado) return false;
        atual++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return fonte.charAt(atual);
    }

    private void adicionarToken(TokenType tipo) {
        if (tipo == null || tipo == TokenType.ERRO) {
            System.err.println("Erro léxico na linha " + linha);
            hasErrors = true;
            return;
        }
        String texto = fonte.substring(inicio, atual);
        tokens.add(new Token(tipo, texto, linha));
    }

    public boolean getHasErrors() {
        return hasErrors;
    }
}