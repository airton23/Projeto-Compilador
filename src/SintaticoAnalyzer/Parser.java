package SintaticoAnalyzer;

import GeradorCodigo.GeradorDeCodigo;
import LexicalAnalyzer.LexicalAnalyzer;
import LexicalAnalyzer.Token;
import LexicalAnalyzer.TokenType;
import SemanticsAnalyzer.Simbolo;
import SemanticsAnalyzer.TabelaDeSimbolos;
import SemanticsAnalyzer.TipoSimbolo;

import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;

public class Parser {

    private final LexicalAnalyzer lexer;
    private Token tokenAtual;

    // ANÁLISE SEMÂNTICA: Instância da tabela de símbolos.
    private final TabelaDeSimbolos tabelaDeSimbolos;

    // GERAÇÃO DE CÓDIGO: Instância do gerador de código intermediário.
    private final GeradorDeCodigo gerador;

    // Variáveis de estado para controle semântico contextual.
    private boolean dentroDeLaco = false;
    private enum CategoriaSubRotina { NENHUMA, FUNCAO, PROCEDIMENTO }
    private CategoriaSubRotina subRotinaAtual = CategoriaSubRotina.NENHUMA;
    private Simbolo funcaoAtual = null;
    private final Deque<String> pilhaInicioLaco = new ArrayDeque<>();
    private final Deque<String> pilhaFimLaco    = new ArrayDeque<>();

    /**
     * Construtor do Parser.
     * @param lexer O analisador léxico que fornecerá os tokens.
     */
    public Parser(LexicalAnalyzer lexer) {
        this.lexer = lexer;
        this.tokenAtual = this.lexer.obterProximoToken();
        this.tabelaDeSimbolos = new TabelaDeSimbolos();
        this.gerador = new GeradorDeCodigo();
    }

    public GeradorDeCodigo getGerador() {
        return this.gerador;
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

    /**
     * Método principal que inicia a análise.
     */
    public void parse() {
        programa();
        if (!verificar(TokenType.FIM_ARQUIVO)) {
            throw new ErroSintaticoException("Esperado fim de arquivo, mas ainda existem tokens.", tokenAtual);
        }
    }

    private void programa() {
        consumir(TokenType.PROGRAMA);
        Token idPrograma = tokenAtual;
        consumir(TokenType.ID);
        // ANÁLISE SEMÂNTICA
        tabelaDeSimbolos.inserir(new Simbolo(idPrograma.lexeme, TipoSimbolo.PROGRAMA, null));
        consumir(TokenType.PONTO_E_VIRGULA);
        bloco();
        consumir(TokenType.PONTO);
        // ANÁLISE SEMÂNTICA
        tabelaDeSimbolos.fecharEscopo();
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
        }
    }

    private void lista_decl_var() {
        decl_var();
        lista_decl_var_rest();
    }

    private void lista_decl_var_rest() {
        while (verificar(TokenType.PONTO_E_VIRGULA)) {
            consumir(TokenType.PONTO_E_VIRGULA);
            if (verificar(TokenType.ID)) {
                decl_var();
            } else {
                break;
            }
        }
    }

    private void decl_var() {
        List<Token> identificadores = new ArrayList<>();
        lista_id(identificadores);
        consumir(TokenType.DOIS_PONTOS);
        TokenType tipoVariavel = tipo();
        // ANÁLISE SEMÂNTICA
        for (Token id : identificadores) {
            try {
                tabelaDeSimbolos.inserir(new Simbolo(id.lexeme, TipoSimbolo.VARIAVEL, tipoVariavel));
            } catch (RuntimeException e) {
                throw new ErroSemanticoException(e.getMessage(), id);
            }
        }
    }

    private void lista_id(List<Token> idList) {
        idList.add(tokenAtual);
        consumir(TokenType.ID);
        lista_id_rest(idList);
    }

    private void lista_id_rest(List<Token> idList) {
        while (verificar(TokenType.VIRGULA)) {
            consumir(TokenType.VIRGULA);
            idList.add(tokenAtual);
            consumir(TokenType.ID);
        }
    }

    private TokenType tipo() {
        if (verificar(TokenType.INTEIRO)) {
            consumir(TokenType.INTEIRO);
            return TokenType.INTEIRO;
        } else if (verificar(TokenType.BOOLEANO)) {
            consumir(TokenType.BOOLEANO);
            return TokenType.BOOLEANO;
        } else {
            throw new ErroSintaticoException("Esperado um tipo (inteiro ou booleano)", tokenAtual);
        }
    }

    private void decl_sub_opcional() {
        while (verificar(TokenType.PROCEDIMENTO) || verificar(TokenType.FUNCAO)) {
            decl_sub_rotina();
            consumir(TokenType.PONTO_E_VIRGULA);
        }
    }

    private void decl_sub_rotina() {
        if (verificar(TokenType.PROCEDIMENTO)) {
            decl_proc();
        } else if (verificar(TokenType.FUNCAO)) {
            decl_func();
        }
    }

    private void decl_proc() {
        consumir(TokenType.PROCEDIMENTO);
        Token idProc = tokenAtual;
        consumir(TokenType.ID);

        // 1. Insere o nome do procedimento no escopo PAI (o que está ativo agora).
        tabelaDeSimbolos.inserir(new Simbolo(idProc.lexeme, TipoSimbolo.PROCEDIMENTO, null));
        gerador.gerar(idProc.lexeme + ":", null, null, null);

        // 2. ABRE o novo escopo para parâmetros e variáveis locais.
        tabelaDeSimbolos.abrirEscopo();

        // 3. Captura os tokens dos parâmetros em uma lista.
        //    Esta é a correção principal.
        List<Token> tokensDosParametros = new ArrayList<>();
        parametros_formais_opc(tokensDosParametros); // Passa a lista como argumento

        // 4. AGORA, processa semanticamente os tokens dos parâmetros que foram capturados.
        if (!tokensDosParametros.isEmpty()) {
            int i = 0;
            while (i < tokensDosParametros.size()) {
                List<Token> nomesParams = new ArrayList<>();
                while (i < tokensDosParametros.size() && tokensDosParametros.get(i).type == TokenType.ID) {
                    nomesParams.add(tokensDosParametros.get(i));
                    i++;
                    if (i < tokensDosParametros.size() && tokensDosParametros.get(i).type == TokenType.VIRGULA) {
                        i++; // Pula a vírgula
                    }
                }

                if (i >= tokensDosParametros.size() || tokensDosParametros.get(i).type != TokenType.DOIS_PONTOS) {
                    throw new ErroSintaticoException("Esperado ':' na declaração de parâmetros de procedimento.", idProc);
                }
                i++; // Pula os dois pontos

                if (i >= tokensDosParametros.size() || !(tokensDosParametros.get(i).type == TokenType.INTEIRO || tokensDosParametros.get(i).type == TokenType.BOOLEANO)) {
                    throw new ErroSintaticoException("Esperado tipo para os parâmetros de procedimento.", idProc);
                }
                TokenType tipoParams = tokensDosParametros.get(i).type;
                i++;

                for (Token param : nomesParams) {
                    tabelaDeSimbolos.inserir(new Simbolo(param.lexeme, TipoSimbolo.VARIAVEL, tipoParams));
                }

                if (i < tokensDosParametros.size() && tokensDosParametros.get(i).type == TokenType.PONTO_E_VIRGULA) {
                    i++; // Pula o ponto e vírgula para o próximo conjunto de parâmetros
                }
            }
        }

        consumir(TokenType.PONTO_E_VIRGULA);

        CategoriaSubRotina estadoAnterior = this.subRotinaAtual;
        this.subRotinaAtual = CategoriaSubRotina.PROCEDIMENTO;

        // 5. Analisa o bloco de comandos.
        bloco();

        this.subRotinaAtual = estadoAnterior;

        // 6. Fecha o escopo da sub-rotina.
        tabelaDeSimbolos.fecharEscopo();
    }

    private void decl_func() {
        consumir(TokenType.FUNCAO);
        Token idFunc = tokenAtual;
        consumir(TokenType.ID);

        // 1. Captura os tokens dos parâmetros semântica.
        List<Token> tokensDosParametros = new ArrayList<>();
        parametros_formais_opc(tokensDosParametros); // O método agora preenche a lista

        consumir(TokenType.DOIS_PONTOS);
        TokenType tipoRetorno = tipo();

        // 2. Com todas as informações da assinatura da função, insere o símbolo no escopo PAI (atual).
        Simbolo simboloFuncao = new Simbolo(idFunc.lexeme, TipoSimbolo.FUNCAO, tipoRetorno);
        tabelaDeSimbolos.inserir(simboloFuncao);
        gerador.gerar(idFunc.lexeme + ":", null, null, null); // Gera o rótulo da função

        // 3. ABRE o novo escopo para a função.
        tabelaDeSimbolos.abrirEscopo();

        // 4. AGORA, processa semanticamente os tokens dos parâmetros que capturamos antes.
        if (!tokensDosParametros.isEmpty()) {
            // Simula um mini-parser para os parâmetros, usando a lista de tokens.
            int i = 0;
            while (i < tokensDosParametros.size()) {
                List<Token> nomesParams = new ArrayList<>();
                while (i < tokensDosParametros.size() && tokensDosParametros.get(i).type == TokenType.ID) {
                    nomesParams.add(tokensDosParametros.get(i));
                    i++;
                    if (i < tokensDosParametros.size() && tokensDosParametros.get(i).type == TokenType.VIRGULA) {
                        i++; // Pula a vírgula
                    }
                }

                if (i >= tokensDosParametros.size() || tokensDosParametros.get(i).type != TokenType.DOIS_PONTOS) {
                    throw new ErroSintaticoException("Esperado ':' na declaração de parâmetros.", idFunc);
                }
                i++; // Pula os dois pontos

                if (i >= tokensDosParametros.size() || !(tokensDosParametros.get(i).type == TokenType.INTEIRO || tokensDosParametros.get(i).type == TokenType.BOOLEANO)) {
                    throw new ErroSintaticoException("Esperado tipo para os parâmetros.", idFunc);
                }
                TokenType tipoParams = tokensDosParametros.get(i).type;
                i++;

                for (Token param : nomesParams) {
                    tabelaDeSimbolos.inserir(new Simbolo(param.lexeme, TipoSimbolo.VARIAVEL, tipoParams));
                }

                if (i < tokensDosParametros.size() && tokensDosParametros.get(i).type == TokenType.PONTO_E_VIRGULA) {
                    i++; // Pula o ponto e vírgula para o próximo conjunto de parâmetros
                }
            }
        }

        consumir(TokenType.PONTO_E_VIRGULA);

        // Define o estado atual para análise do bloco
        CategoriaSubRotina estadoAnteriorSub = this.subRotinaAtual;
        Simbolo estadoAnteriorFunc = this.funcaoAtual;
        this.subRotinaAtual = CategoriaSubRotina.FUNCAO;
        this.funcaoAtual = simboloFuncao;

        bloco();

        this.subRotinaAtual = estadoAnteriorSub;
        this.funcaoAtual = estadoAnteriorFunc;

        tabelaDeSimbolos.fecharEscopo();
    }

    private void parametros_formais_opc(List<Token> listaDeParametros) {
        if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            if (verificar(TokenType.ID)) {
                while (!verificar(TokenType.FECHA_PAREN)) {
                    listaDeParametros.add(tokenAtual);
                    avancarToken();
                }
            }
            consumir(TokenType.FECHA_PAREN);
        }
    }

    private void lista_parametros() {
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
        decl_var();
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
        while (verificar(TokenType.PONTO_E_VIRGULA)) {
            consumir(TokenType.PONTO_E_VIRGULA);
            if (!verificar(TokenType.FIM)) {
                comando();
            } else {
                break;
            }
        }
    }

    private void comando() {
        if (verificar(TokenType.INICIO)) {
            comandos();
        } else if (verificar(TokenType.ID)) {
            comando_atr_chamada();
        } else if (verificar(TokenType.SE)) {
            comando_condicional();
        } else if (verificar(TokenType.ENQUANTO)) {
            comando_enquanto();
        } else if (verificar(TokenType.ESCREVA)) {
            comando_escrita();
        } else if (verificar(TokenType.RETORNO)) {
            comando_retorno();
        } else if (verificar(TokenType.BREAK)) {
            if (pilhaFimLaco.isEmpty()) {
                throw new ErroSemanticoException("'break' fora de um laço 'enquanto'", tokenAtual);
            }
            consumir(TokenType.BREAK);
            gerador.gerar("goto", null, null, pilhaFimLaco.peek());
        }
        else if (verificar(TokenType.CONTINUE)) {
            if (pilhaInicioLaco.isEmpty()) {
                throw new ErroSemanticoException("'continue' fora de um laço 'enquanto'", tokenAtual);
            }
            consumir(TokenType.CONTINUE);
            gerador.gerar("goto", null, null, pilhaInicioLaco.peek());
        } else if (!verificar(TokenType.FIM)) {
            throw new ErroSintaticoException("Esperado um comando válido", tokenAtual);
        }
    }

    private void comando_atr_chamada() {
        Token id = tokenAtual;
        consumir(TokenType.ID);
        // ANÁLISE SEMÂNTICA
        Simbolo simbolo = tabelaDeSimbolos.buscar(id.lexeme);
        if (simbolo == null) {
            throw new ErroSemanticoException("Identificador '" + id.lexeme + "' não foi declarado.", id);
        }

        if (verificar(TokenType.ATRIBUICAO)) { // Atribuição
            consumir(TokenType.ATRIBUICAO);
            ResultadoExpressao resExpr = expressao();
            // ANÁLISE SEMÂNTICA
            if (simbolo.tipo != resExpr.tipo) {
                throw new ErroSemanticoException("Tipos incompatíveis para atribuição. Esperado '" + simbolo.tipo + "' mas encontrado '" + resExpr.tipo + "'.", id);
            }
            // GERAÇÃO DE CÓDIGO
            gerador.gerar(":=", resExpr.nome, null, id.lexeme);
        } else { // Chamada de procedimento ou função
            argumentos_reais_opc();
            if(simbolo.categoria == TipoSimbolo.PROCEDIMENTO) {
                // GERAÇÃO DE CÓDIGO
                gerador.gerar("call", id.lexeme, null, null);
            }
            // Se for uma função chamada como um procedimento, o valor de retorno é descartado.
        }
    }

    private void comando_condicional() {
        consumir(TokenType.SE);
        // GERAÇÃO DE CÓDIGO
        String rotuloSenao = gerador.novoRotulo();
        String rotuloFimSe = gerador.novoRotulo();

        ResultadoExpressao resCond = expressao();
        // ANÁLISE SEMÂNTICA
        if (resCond.tipo != TokenType.BOOLEANO) {
            throw new ErroSemanticoException("Condição do 'se' deve ser booleana.", tokenAtual);
        }
        // GERAÇÃO DE CÓDIGO
        gerador.gerar("if_false", resCond.nome, "goto", rotuloSenao);

        consumir(TokenType.ENTAO);
        comando();

        if (verificar(TokenType.SENAO)) {
            // GERAÇÃO DE CÓDIGO
            gerador.gerar("goto", null, null, rotuloFimSe);
            gerador.gerar(rotuloSenao + ":", null, null, null);
            consumir(TokenType.SENAO);
            comando();
            gerador.gerar(rotuloFimSe + ":", null, null, null);
        } else {
            gerador.gerar(rotuloSenao + ":", null, null, null);
        }
    }

    private void comando_enquanto() {
        consumir(TokenType.ENQUANTO);

        // Rótulos do laço
        String rotuloInicio = gerador.novoRotulo();
        String rotuloFim    = gerador.novoRotulo();

        // label de teste
        gerador.gerar(rotuloInicio + ":", null, null, null);

        // condição
        ResultadoExpressao resCond = expressao();
        if (resCond.tipo != TokenType.BOOLEANO) {
            throw new ErroSemanticoException("Condição do 'enquanto' deve ser booleana.", tokenAtual);
        }
        // if_false cond goto Lfim
        gerador.gerar("if_false", resCond.nome, null, rotuloFim);

        consumir(TokenType.FACA);

        // Entramos no corpo: habilita break/continue para este laço
        pilhaInicioLaco.push(rotuloInicio);
        pilhaFimLaco.push(rotuloFim);
        boolean estadoAnterior = this.dentroDeLaco;
        this.dentroDeLaco = true;

        comando(); // seu "bloco" ou "comando" conforme a gramática

        // Voltamos ao estado anterior
        this.dentroDeLaco = estadoAnterior;
        pilhaInicioLaco.pop();
        pilhaFimLaco.pop();

        // volta para testar de novo
        gerador.gerar("goto", null, null, rotuloInicio);
        // saída do laço
        gerador.gerar(rotuloFim + ":", null, null, null);
    }

    private void comando_escrita() {
        consumir(TokenType.ESCREVA);
        consumir(TokenType.ABRE_PAREN);
        ResultadoExpressao resExpr = expressao();
        // GERAÇÃO DE CÓDIGO
        gerador.gerar("escreva", resExpr.nome, null, null);
        consumir(TokenType.FECHA_PAREN);
    }

    private void comando_retorno() {
        // ANÁLISE SEMÂNTICA
        if (this.subRotinaAtual != CategoriaSubRotina.FUNCAO) {
            throw new ErroSemanticoException("Comando 'retorno' só pode ser usado dentro de uma função.", tokenAtual);
        }
        consumir(TokenType.RETORNO);
        ResultadoExpressao resExpr = expressao();
        // ANÁLISE SEMÂNTICA
        if (funcaoAtual.tipo != resExpr.tipo) {
            throw new ErroSemanticoException("Tipo de retorno incompatível.", tokenAtual);
        }
        // GERAÇÃO DE CÓDIGO
        gerador.gerar("retorno", resExpr.nome, null, null);
    }

    // Classe auxiliar para retornar TIPO (semântica) e NOME (geração de código)
    private static class ResultadoExpressao {
        final String nome;
        final TokenType tipo;
        ResultadoExpressao(String nome, TokenType tipo) { this.nome = nome; this.tipo = tipo; }
    }

    private ResultadoExpressao expressao() {
        ResultadoExpressao resEsq = expressao_simples();
        if (verificar(TokenType.IGUAL) || verificar(TokenType.DIFERENTE) || verificar(TokenType.MENOR) ||
                verificar(TokenType.MENOR_IGUAL) || verificar(TokenType.MAIOR) || verificar(TokenType.MAIOR_IGUAL)) {
            Token op = tokenAtual;
            avancarToken();
            ResultadoExpressao resDir = expressao_simples();

            // ANÁLISE SEMÂNTICA
            if (resEsq.tipo != TokenType.INTEIRO || resDir.tipo != TokenType.INTEIRO) {
                throw new ErroSemanticoException("Operadores relacionais exigem operandos inteiros.", op);
            }

            // GERAÇÃO DE CÓDIGO
            String temp = gerador.novoTemp();
            gerador.gerar(op.lexeme, resEsq.nome, resDir.nome, temp);
            return new ResultadoExpressao(temp, TokenType.BOOLEANO);
        }
        return resEsq;
    }

    private void op_rel() { /* Deprecated by advancing token directly */ }

    private ResultadoExpressao expressao_simples() {
        sinal_opcional(); // Lógica de sinal unário precisa ser implementada na geração de código
        ResultadoExpressao resAcumulado = termo();
        while (verificar(TokenType.MAIS) || verificar(TokenType.MENOS) || verificar(TokenType.OU)) {
            Token op = tokenAtual;
            avancarToken();
            ResultadoExpressao resDir = termo();

            // ANÁLISE SEMÂNTICA
            TokenType tipoResultante;
            if (op.type == TokenType.OU) {
                if (resAcumulado.tipo != TokenType.BOOLEANO || resDir.tipo != TokenType.BOOLEANO) throw new ErroSemanticoException("Operador 'ou' exige operandos booleanos.", op);
                tipoResultante = TokenType.BOOLEANO;
            } else {
                if (resAcumulado.tipo != TokenType.INTEIRO || resDir.tipo != TokenType.INTEIRO) throw new ErroSemanticoException("Operadores '+' e '-' exigem operandos inteiros.", op);
                tipoResultante = TokenType.INTEIRO;
            }

            // GERAÇÃO DE CÓDIGO
            String temp = gerador.novoTemp();
            gerador.gerar(op.lexeme, resAcumulado.nome, resDir.nome, temp);
            resAcumulado = new ResultadoExpressao(temp, tipoResultante);
        }
        return resAcumulado;
    }

    private void sinal_opcional() {
        if (verificar(TokenType.MAIS) || verificar(TokenType.MENOS)) {
            avancarToken();
        }
    }

    private void op_soma() { /* Deprecated by advancing token directly */ }

    private ResultadoExpressao termo() {
        ResultadoExpressao resAcumulado = fator();
        while (verificar(TokenType.MULT) || verificar(TokenType.DIVISAO) || verificar(TokenType.E)) {
            Token op = tokenAtual;
            avancarToken();
            ResultadoExpressao resDir = fator();

            // ANÁLISE SEMÂNTICA
            TokenType tipoResultante;
            if (op.type == TokenType.E) {
                if (resAcumulado.tipo != TokenType.BOOLEANO || resDir.tipo != TokenType.BOOLEANO) throw new ErroSemanticoException("Operador 'e' exige operandos booleanos.", op);
                tipoResultante = TokenType.BOOLEANO;
            } else {
                if (resAcumulado.tipo != TokenType.INTEIRO || resDir.tipo != TokenType.INTEIRO) throw new ErroSemanticoException("Operadores '*' e '/' exigem operandos inteiros.", op);
                tipoResultante = TokenType.INTEIRO;
            }

            // GERAÇÃO DE CÓDIGO
            String temp = gerador.novoTemp();
            gerador.gerar(op.lexeme, resAcumulado.nome, resDir.nome, temp);
            resAcumulado = new ResultadoExpressao(temp, tipoResultante);
        }
        return resAcumulado;
    }

    private void op_mult() { /* Deprecated by advancing token directly */ }

    private ResultadoExpressao fator() {
        Token token = tokenAtual;
        if (verificar(TokenType.ID)) {
            avancarToken();
            Simbolo s = tabelaDeSimbolos.buscar(token.lexeme);
            if (s == null) throw new ErroSemanticoException("Identificador não declarado: " + token.lexeme, token);

            if (verificar(TokenType.ABRE_PAREN)) { // É uma chamada de função
                if (s.categoria != TipoSimbolo.FUNCAO) {
                    throw new ErroSemanticoException("'" + s.nome + "' não é uma função.", token);
                }
                argumentos_reais_opc();
                String temp = gerador.novoTemp();
                // GERAÇÃO DE CÓDIGO
                gerador.gerar("call", s.nome, null, temp);
                return new ResultadoExpressao(temp, s.tipo);
            }

            return new ResultadoExpressao(token.lexeme, s.tipo);

        } else if (verificar(TokenType.NUMERO)) {
            avancarToken();
            return new ResultadoExpressao(token.lexeme, TokenType.INTEIRO);
        } else if (verificar(TokenType.VERDADEIRO) || verificar(TokenType.FALSO)) {
            avancarToken();
            return new ResultadoExpressao(token.lexeme, TokenType.BOOLEANO);
        } else if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            ResultadoExpressao resultado = expressao();
            consumir(TokenType.FECHA_PAREN);
            return resultado;
        } else if (verificar(TokenType.NAO)) {
            avancarToken();
            ResultadoExpressao resFator = fator();
            if (resFator.tipo != TokenType.BOOLEANO) {
                throw new ErroSemanticoException("Operador 'nao' só pode ser aplicado a booleanos.", token);
            }
            String temp = gerador.novoTemp();
            gerador.gerar("nao", resFator.nome, null, temp);
            return new ResultadoExpressao(temp, TokenType.BOOLEANO);
        } else {
            throw new ErroSintaticoException("Fator inesperado", token);
        }
    }

    private void fator_cont() { /* Merged into fator() */ }

    private void argumentos_reais_opc() {
        if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            lista_argumentos();
            consumir(TokenType.FECHA_PAREN);
        }
    }

    private void lista_argumentos() {
        if (!verificar(TokenType.FECHA_PAREN)) {
            ResultadoExpressao resExpr = expressao();
            // GERAÇÃO DE CÓDIGO (para 'param')
            gerador.gerar("param", resExpr.nome, null, null);
            lista_argumentos_rest();
        }
    }



    private void lista_argumentos_rest() {
        while (verificar(TokenType.VIRGULA)) {
            consumir(TokenType.VIRGULA);
            ResultadoExpressao resExpr = expressao();
            // GERAÇÃO DE CÓDIGO (para 'param')
            gerador.gerar("param", resExpr.nome, null, null);
        }
    }
}