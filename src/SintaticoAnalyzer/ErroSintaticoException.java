package SintaticoAnalyzer;

import LexicalAnalyzer.Token;

public class ErroSintaticoException extends RuntimeException {

    public ErroSintaticoException(String mensagem, Token token) {
        super(formatarMensagem(mensagem, token));
    }

    private static String formatarMensagem(String mensagem, Token token) {
        if (token != null) {
            return String.format("Erro Sintático na linha %d: %s (token encontrado: '%s' do tipo %s)",
                    token.line, mensagem, token.lexeme, token.type);
        }
        return "Erro Sintático: " + mensagem;
    }
}