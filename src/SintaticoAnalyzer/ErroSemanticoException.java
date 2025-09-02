package SintaticoAnalyzer;

import LexicalAnalyzer.Token;

public class ErroSemanticoException extends RuntimeException {

  public ErroSemanticoException(String mensagem, Token token) {
    super(formatarMensagem(mensagem, token));
  }

  private static String formatarMensagem(String mensagem, Token token) {
    if (token != null) {
      return String.format("Erro Semântico na linha %d: %s (referente ao token: '%s')",
              token.line, mensagem, token.lexeme);
    }
    return "Erro Semântico: " + mensagem;
  }
}