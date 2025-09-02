import LexicalAnalyzer.LexicalAnalyzer;
import SintaticoAnalyzer.ErroSemanticoException; // Importar a nova exceção
import SintaticoAnalyzer.ErroSintaticoException;
import SintaticoAnalyzer.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        String caminhoArquivo = "testes/teste_dupla_declaracao.txt";

        try {
            String codigoFonte = new String(Files.readAllBytes(Paths.get(caminhoArquivo)));

            LexicalAnalyzer lexer = new LexicalAnalyzer(codigoFonte);
            Parser parser = new Parser(lexer);
            parser.parse();

            System.out.println("Análise sintática e semântica concluídas com sucesso!");

        } catch (IOException e) {
            System.err.println("Erro ao ler o arquivo: " + e.getMessage());
        } catch (ErroSintaticoException | ErroSemanticoException e) { // Capturar ambos os erros
            System.err.println(e.getMessage());
        } catch (Exception e) {
            System.err.println("Ocorreu um erro inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
}