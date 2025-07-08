package SintaticoAnalyzer;

import LexicalAnalyzer.LexicalAnalyzer;
import LexicalAnalyzer.Token;
import LexicalAnalyzer.TokenType;

public class Parser {

    private final LexicalAnalyzer lexer;
    private Token tokenAtual;

    public Parser(LexicalAnalyzer lexer) {
        this.lexer = lexer;
        this.tokenAtual = this.lexer.obterProximoToken(); // "Puxa" o primeiro token
    }

    private void avancarToken() {
        this.tokenAtual = this.lexer.obterProximoToken();
    }

    private void consumir(TokenType tipoEsperado) {
        if (this.tokenAtual.type == tipoEsperado) {
            avancarToken();
        } else {
            throw new ErroSintaticoException("Esperado token " + tipoEsperado + ", mas foi encontrado " + this.tokenAtual.type, tokenAtual);
        }
    }

    private boolean verificar(TokenType tipo) {
        return this.tokenAtual.type == tipo;
    }

    public void parse() {
        programa();
        if (!verificar(TokenType.FIM_ARQUIVO)) {
            throw new ErroSintaticoException("Esperado fim de arquivo, mas ainda existem tokens.", tokenAtual);
        }
    }

    private void programa() {
        consumir(TokenType.PROGRAMA);
        consumir(TokenType.ID);
        consumir(TokenType.PONTO_E_VIRGULA);
        bloco();
        consumir(TokenType.PONTO);
    }

    private void bloco() {
        decl_var_opcional();
        decl_sub_opcional();
        comandos();
    }

    private void decl_var_opcional() {
        if (verificar(TokenType.VAR)) {
            consumir(TokenType.VAR);
            lista_decl_var();
            consumir(TokenType.PONTO_E_VIRGULA);
        }
    }

    private void lista_decl_var() {
        decl_var();
        lista_decl_var_rest();
    }

    private void lista_decl_var_rest() {
        while (verificar(TokenType.PONTO_E_VIRGULA)) {
            consumir(TokenType.PONTO_E_VIRGULA);
            if(verificar(TokenType.ID)) {
                decl_var();
            } else {
                break;
            }
        }
    }

    private void decl_var() {
        lista_id();
        consumir(TokenType.DOIS_PONTOS);
        tipo();
    }

    private void lista_id() {
        consumir(TokenType.ID);
        lista_id_rest();
    }

    private void lista_id_rest() {
        while (verificar(TokenType.VIRGULA)) {
            consumir(TokenType.VIRGULA);
            consumir(TokenType.ID);
        }
    }

    private void tipo() {
        if (verificar(TokenType.INTEIRO)) {
            consumir(TokenType.INTEIRO);
        } else if (verificar(TokenType.BOOLEANO)) {
            consumir(TokenType.BOOLEANO);
        } else {
            throw new ErroSintaticoException("Esperado um tipo (inteiro ou booleano)", tokenAtual);
        }
    }

    private void decl_sub_opcional() {
        if (verificar(TokenType.PROCEDIMENTO) || verificar(TokenType.FUNCAO)) {
            decl_sub();
        }
    }

    private void decl_sub() {
        decl_sub_rotina();
        decl_sub_rest();
    }

    private void decl_sub_rest() {
        while (verificar(TokenType.PONTO_E_VIRGULA)) {
            consumir(TokenType.PONTO_E_VIRGULA);
            if(verificar(TokenType.PROCEDIMENTO) || verificar(TokenType.FUNCAO)){
                decl_sub_rotina();
            } else {
                break;
            }
        }
    }

    private void decl_sub_rotina() {
        if (verificar(TokenType.PROCEDIMENTO)) {
            decl_proc();
        } else if (verificar(TokenType.FUNCAO)) {
            decl_func();
        } else {
            throw new ErroSintaticoException("Esperado declaração de procedimento ou função", tokenAtual);
        }
    }

    private void decl_proc() {
        consumir(TokenType.PROCEDIMENTO);
        consumir(TokenType.ID);
        parametros_formais_opc();
        consumir(TokenType.PONTO_E_VIRGULA);
        bloco();
    }

    private void decl_func() {
        consumir(TokenType.FUNCAO);
        consumir(TokenType.ID);
        parametros_formais_opc();
        consumir(TokenType.DOIS_PONTOS);
        tipo();
        consumir(TokenType.PONTO_E_VIRGULA);
        bloco();
    }

    private void parametros_formais_opc() {
        if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            lista_parametros();
            consumir(TokenType.FECHA_PAREN);
        }
    }

    private void lista_parametros() {
        // A lista pode ser vazia, mesmo dentro dos parênteses
        if (verificar(TokenType.ID)) {
            parametro();
            lista_parametros_rest();
        }
    }

    private void lista_parametros_rest() {
        while (verificar(TokenType.PONTO_E_VIRGULA)) {
            consumir(TokenType.PONTO_E_VIRGULA);
            parametro();
        }
    }

    private void parametro() {
        lista_id();
        consumir(TokenType.DOIS_PONTOS);
        tipo();
    }

    private void comandos() {
        consumir(TokenType.INICIO);
        lista_comandos();
        consumir(TokenType.FIM);
    }

    private void lista_comandos() {
        if (!verificar(TokenType.FIM)) {
            comando();
            lista_comandos_rest();
        }
    }

    private void lista_comandos_rest() {
        while(verificar(TokenType.PONTO_E_VIRGULA)) {
            consumir(TokenType.PONTO_E_VIRGULA);
            // Evita erro com ; antes do fim
            if(!verificar(TokenType.FIM)){
                comando();
            } else {
                break;
            }
        }
    }

    private void comando() {
        if (verificar(TokenType.ID)) {
            comando_atr_chamada();
        } else if (verificar(TokenType.SE)) {
            comando_condicional();
        } else if (verificar(TokenType.ENQUANTO)) {
            comando_enquanto();
        } else if (verificar(TokenType.LEIA)) {
            comando_leitura();
        } else if (verificar(TokenType.ESCREVA)) {
            comando_escrita();
        } else if (verificar(TokenType.RETORNO)) {
            comando_retorno();
        } else if (verificar(TokenType.BREAK)) {
            consumir(TokenType.BREAK);
        } else if (verificar(TokenType.CONTINUE)) {
            consumir(TokenType.CONTINUE);
        } else {
            throw new ErroSintaticoException("Esperado um comando válido (atribuição, se, enquanto, etc.)", tokenAtual);
        }
    }

    private void comando_atr_chamada() {
        consumir(TokenType.ID);
        atr_ou_chamada_proc();
    }

    private void atr_ou_chamada_proc() {
        if (verificar(TokenType.ATRIBUICAO)) {
            consumir(TokenType.ATRIBUICAO);
            expressao();
        } else {
            argumentos_reais_opc();
        }
    }

    private void comando_condicional() {
        consumir(TokenType.SE);
        expressao();
        consumir(TokenType.ENTAO);
        comando();
        senao_opcional();
    }

    private void senao_opcional() {
        if (verificar(TokenType.SENAO)) {
            consumir(TokenType.SENAO);
            comando();
        }
    }

    private void comando_enquanto() {
        consumir(TokenType.ENQUANTO);
        expressao();
        consumir(TokenType.FACA);
        comando();
    }

    private void comando_leitura() {
        consumir(TokenType.LEIA);
        consumir(TokenType.ABRE_PAREN);
        consumir(TokenType.ID);
        consumir(TokenType.FECHA_PAREN);
    }

    private void comando_escrita() {
        consumir(TokenType.ESCREVA);
        consumir(TokenType.ABRE_PAREN);
        expressao();
        consumir(TokenType.FECHA_PAREN);
    }

    private void comando_retorno() {
        consumir(TokenType.RETORNO);
        expressao();
    }

    private void expressao() {
        expressao_simples();
        expressao_rel_opcional();
    }

    private void expressao_rel_opcional() {
        if (verificar(TokenType.IGUAL) || verificar(TokenType.DIFERENTE) || verificar(TokenType.MENOR) ||
                verificar(TokenType.MENOR_IGUAL) || verificar(TokenType.MAIOR) || verificar(TokenType.MAIOR_IGUAL)) {
            op_rel();
            expressao_simples();
        }
    }

    private void op_rel() {
        // Apenas consome o operador relacional, qualquer um dos válidos
        switch (tokenAtual.type) {
            case IGUAL: consumir(TokenType.IGUAL); break;
            case DIFERENTE: consumir(TokenType.DIFERENTE); break;
            case MENOR: consumir(TokenType.MENOR); break;
            case MENOR_IGUAL: consumir(TokenType.MENOR_IGUAL); break;
            case MAIOR: consumir(TokenType.MAIOR); break;
            case MAIOR_IGUAL: consumir(TokenType.MAIOR_IGUAL); break;
            default:
                throw new ErroSintaticoException("Esperado um operador relacional (=, <>, <, <=, >, >=)", tokenAtual);
        }
    }

    private void expressao_simples() {
        sinal_opcional();
        termo();
        expressao_simples_rest();
    }

    private void sinal_opcional() {
        if (verificar(TokenType.MAIS) || verificar(TokenType.MENOS)) {
            avancarToken();
        }
    }

    private void expressao_simples_rest() {
        while (verificar(TokenType.MAIS) || verificar(TokenType.MENOS) || verificar(TokenType.OU)) {
            op_soma();
            termo();
        }
    }

    private void op_soma() {
        if (verificar(TokenType.MAIS)) {
            consumir(TokenType.MAIS);
        } else if (verificar(TokenType.MENOS)) {
            consumir(TokenType.MENOS);
        } else if (verificar(TokenType.OU)) {
            consumir(TokenType.OU);
        } else {
            throw new ErroSintaticoException("Esperado operador de soma (+, -, ou)", tokenAtual);
        }
    }

    private void termo() {
        fator();
        termo_rest();
    }

    private void termo_rest() {
        while (verificar(TokenType.MULT) || verificar(TokenType.DIVISAO) || verificar(TokenType.E)) {
            op_mult();
            fator();
        }
    }

    private void op_mult() {
        if (verificar(TokenType.MULT)) {
            consumir(TokenType.MULT);
        } else if (verificar(TokenType.DIVISAO)) {
            consumir(TokenType.DIVISAO);
        } else if (verificar(TokenType.E)) {
            consumir(TokenType.E);
        } else {
            throw new ErroSintaticoException("Esperado operador de multiplicação (*, /, e)", tokenAtual);
        }
    }

    private void fator() {
        if (verificar(TokenType.ID)) {
            consumir(TokenType.ID);
            fator_cont();
        } else if (verificar(TokenType.NUMERO)) {
            consumir(TokenType.NUMERO);
        } else if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            expressao();
            consumir(TokenType.FECHA_PAREN);
        } else if (verificar(TokenType.VERDADEIRO)) {
            consumir(TokenType.VERDADEIRO);
        } else if (verificar(TokenType.FALSO)) {
            consumir(TokenType.FALSO);
        } else if (verificar(TokenType.NAO)) {
            consumir(TokenType.NAO);
            fator();
        } else {
            throw new ErroSintaticoException("Esperado um fator (ID, número, expressão entre parênteses, etc.)", tokenAtual);
        }
    }

    private void fator_cont() {
        if (verificar(TokenType.ABRE_PAREN)) {
            argumentos_reais_opc();
        }
    }

    private void argumentos_reais_opc() {
        if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            lista_argumentos();
            consumir(TokenType.FECHA_PAREN);
        }
    }

    private void lista_argumentos() {
        // A lista de argumentos pode ser vazia
        if (!verificar(TokenType.FECHA_PAREN)) {
            expressao();
            lista_argumentos_rest();
        }
    }

    private void lista_argumentos_rest() {
        while (verificar(TokenType.VIRGULA)) {
            consumir(TokenType.VIRGULA);
            expressao();
        }
    }
}