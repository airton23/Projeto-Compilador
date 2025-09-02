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

        // 1. Cria o símbolo do procedimento.
        Simbolo simboloProcedimento = new Simbolo(idProc.lexeme, TipoSimbolo.PROCEDIMENTO, null);

        // 2. Analisa os parâmetros e preenche a lista dentro do símbolo.
        parametros_formais_opc(simboloProcedimento.parametros);

        // 3. Insere o símbolo COMPLETO na tabela do escopo PAI.
        try {
            tabelaDeSimbolos.inserir(simboloProcedimento);
        } catch (RuntimeException e) {
            throw new ErroSemanticoException(e.getMessage(), idProc);
        }
        gerador.gerar(idProc.lexeme + ":", null, null, null);

        // 4. ABRE o novo escopo para o corpo do procedimento.
        tabelaDeSimbolos.abrirEscopo();

        // 5. INSERE os parâmetros como variáveis locais no NOVO escopo.
        for (Simbolo param : simboloProcedimento.parametros) {
            try {
                tabelaDeSimbolos.inserir(param);
            } catch (RuntimeException e) {
                throw new ErroSemanticoException("Parâmetro '" + param.nome + "' já declarado neste procedimento.", idProc);
            }
        }

        consumir(TokenType.PONTO_E_VIRGULA);

        CategoriaSubRotina estadoAnterior = this.subRotinaAtual;
        this.subRotinaAtual = CategoriaSubRotina.PROCEDIMENTO;

        bloco();

        this.subRotinaAtual = estadoAnterior;

        tabelaDeSimbolos.fecharEscopo();
    }

    private void decl_func() {
        consumir(TokenType.FUNCAO);
        Token idFunc = tokenAtual;
        consumir(TokenType.ID);

        // 1. Cria o símbolo da função.
        Simbolo simboloFuncao = new Simbolo(idFunc.lexeme, TipoSimbolo.FUNCAO, null);

        // 2. Analisa os parâmetros e preenche a lista dentro do símbolo da função.
        parametros_formais_opc(simboloFuncao.parametros);

        consumir(TokenType.DOIS_PONTOS);
        TokenType tipoRetorno = tipo();
        simboloFuncao.tipo = tipoRetorno; // Define o tipo de retorno no símbolo.

        // 3. Insere o símbolo COMPLETO na tabela do escopo PAI.
        try {
            tabelaDeSimbolos.inserir(simboloFuncao);
        } catch (RuntimeException e) {
            throw new ErroSemanticoException(e.getMessage(), idFunc);
        }
        gerador.gerar(idFunc.lexeme + ":", null, null, null);

        // 4. ABRE o novo escopo para o corpo da função.
        tabelaDeSimbolos.abrirEscopo();

        // 5. INSERE os parâmetros como variáveis locais no NOVO escopo.
        for (Simbolo param : simboloFuncao.parametros) {
            try {
                tabelaDeSimbolos.inserir(param);
            } catch (RuntimeException e) {
                throw new ErroSemanticoException("Parâmetro '" + param.nome + "' já declarado nesta função.", idFunc);
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

    // *** MÉTODOS REESTRUTURADOS PARA ANÁLISE DE PARÂMETROS ***
    private void parametros_formais_opc(List<Simbolo> listaDeParametros) {
        if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            if (verificar(TokenType.ID)) {
                lista_parametros(listaDeParametros);
            }
            consumir(TokenType.FECHA_PAREN);
        }
    }

    private void lista_parametros(List<Simbolo> listaDeParametros) {
        parametro(listaDeParametros);
        while (verificar(TokenType.PONTO_E_VIRGULA)) {
            consumir(TokenType.PONTO_E_VIRGULA);
            parametro(listaDeParametros);
        }
    }

    private void parametro(List<Simbolo> listaDeParametros) {
        List<Token> identificadores = new ArrayList<>();
        lista_id(identificadores);
        consumir(TokenType.DOIS_PONTOS);
        TokenType tipoVariavel = tipo();

        for (Token id : identificadores) {
            // Cria um símbolo para cada parâmetro e o adiciona à lista
            listaDeParametros.add(new Simbolo(id.lexeme, TipoSimbolo.VARIAVEL, tipoVariavel));
        }
    }
    // *** FIM DOS MÉTODOS REESTRUTURADOS ***

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
        } else { // Chamada de procedimento ou função (valor de retorno descartado)
            if (simbolo.categoria != TipoSimbolo.PROCEDIMENTO && simbolo.categoria != TipoSimbolo.FUNCAO) {
                throw new ErroSemanticoException("'" + simbolo.nome + "' não é uma sub-rotina e não pode ser chamado.", id);
            }
            argumentos_reais_opc(simbolo); // Passa o símbolo para validação
            // GERAÇÃO DE CÓDIGO
            if(simbolo.categoria == TipoSimbolo.PROCEDIMENTO) {
                gerador.gerar("call", id.lexeme, null, null);
            } else { // Chamada de função como procedimento
                String temp = gerador.novoTemp(); // Descarta o retorno em um temporário
                gerador.gerar("call", simbolo.nome, null, temp);
            }
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
        gerador.gerar("if_false", resCond.nome, null, rotuloSenao);

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

        String rotuloInicio = gerador.novoRotulo();
        String rotuloFim    = gerador.novoRotulo();

        gerador.gerar(rotuloInicio + ":", null, null, null);

        ResultadoExpressao resCond = expressao();
        if (resCond.tipo != TokenType.BOOLEANO) {
            throw new ErroSemanticoException("Condição do 'enquanto' deve ser booleana.", tokenAtual);
        }
        gerador.gerar("if_false", resCond.nome, null, rotuloFim);

        consumir(TokenType.FACA);

        pilhaInicioLaco.push(rotuloInicio);
        pilhaFimLaco.push(rotuloFim);
        boolean estadoAnterior = this.dentroDeLaco;
        this.dentroDeLaco = true;

        comando();

        this.dentroDeLaco = estadoAnterior;
        pilhaInicioLaco.pop();
        pilhaFimLaco.pop();

        gerador.gerar("goto", null, null, rotuloInicio);
        gerador.gerar(rotuloFim + ":", null, null, null);
    }

    private void comando_escrita() {
        consumir(TokenType.ESCREVA);
        consumir(TokenType.ABRE_PAREN);
        ResultadoExpressao resExpr = expressao();
        gerador.gerar("escreva", resExpr.nome, null, null);
        consumir(TokenType.FECHA_PAREN);
    }

    private void comando_retorno() {
        if (this.subRotinaAtual != CategoriaSubRotina.FUNCAO) {
            throw new ErroSemanticoException("Comando 'retorno' só pode ser usado dentro de uma função.", tokenAtual);
        }
        consumir(TokenType.RETORNO);
        ResultadoExpressao resExpr = expressao();
        if (funcaoAtual.tipo != resExpr.tipo) {
            throw new ErroSemanticoException("Tipo de retorno incompatível. Esperado " + funcaoAtual.tipo + " mas encontrado " + resExpr.tipo + ".", tokenAtual);
        }
        gerador.gerar("retorno", resExpr.nome, null, null);
    }

    private static class ResultadoExpressao {
        final String nome;
        final TokenType tipo;
        ResultadoExpressao(String nome, TokenType tipo) { this.nome = nome; this.tipo = tipo; }
    }

    private boolean ehRelacional(TokenType t) {
        return t == TokenType.IGUAL || t == TokenType.DIFERENTE ||
                t == TokenType.MENOR || t == TokenType.MENOR_IGUAL ||
                t == TokenType.MAIOR || t == TokenType.MAIOR_IGUAL;
    }

    private ResultadoExpressao expressao() {
        ResultadoExpressao resEsq = expressao_simples();

        if (ehRelacional(tokenAtual.type)) {
            Token op = tokenAtual;           // <-- guarda o token do operador
            TokenType operador = tokenAtual.type;
            avancarToken();

            ResultadoExpressao resDir = expressao_simples();

            switch (operador) {
                case IGUAL:
                case DIFERENTE:
                    if (resEsq.tipo != resDir.tipo) {
                        throw new ErroSemanticoException(
                                "Os operadores '=' e '!=' exigem operandos do mesmo tipo ("
                                        + resEsq.tipo + " vs " + resDir.tipo + ").", op);
                    }
                    break;

                case MENOR:
                case MENOR_IGUAL:
                case MAIOR:
                case MAIOR_IGUAL:
                    if (resEsq.tipo != TokenType.INTEIRO || resDir.tipo != TokenType.INTEIRO) {
                        throw new ErroSemanticoException(
                                "O operador '" + op.lexeme + "' exige operandos inteiros.", op);
                    }
                    break;
            }

            String temp = gerador.novoTemp();
            gerador.gerar(op.lexeme, resEsq.nome, resDir.nome, temp);
            return new ResultadoExpressao(temp, TokenType.BOOLEANO);
        }

        return resEsq;
    }


    private ResultadoExpressao expressao_simples() {
        sinal_opcional();
        ResultadoExpressao resAcumulado = termo();
        while (verificar(TokenType.MAIS) || verificar(TokenType.MENOS) || verificar(TokenType.OU)) {
            Token op = tokenAtual;
            avancarToken();
            ResultadoExpressao resDir = termo();

            TokenType tipoResultante;
            if (op.type == TokenType.OU) {
                if (resAcumulado.tipo != TokenType.BOOLEANO || resDir.tipo != TokenType.BOOLEANO) throw new ErroSemanticoException("Operador 'ou' exige operandos booleanos.", op);
                tipoResultante = TokenType.BOOLEANO;
            } else {
                if (resAcumulado.tipo != TokenType.INTEIRO || resDir.tipo != TokenType.INTEIRO) throw new ErroSemanticoException("Operadores '+' e '-' exigem operandos inteiros.", op);
                tipoResultante = TokenType.INTEIRO;
            }

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

    private ResultadoExpressao termo() {
        ResultadoExpressao resAcumulado = fator();
        while (verificar(TokenType.MULT) || verificar(TokenType.DIVISAO) || verificar(TokenType.E)) {
            Token op = tokenAtual;
            avancarToken();
            ResultadoExpressao resDir = fator();

            TokenType tipoResultante;
            if (op.type == TokenType.E) {
                if (resAcumulado.tipo != TokenType.BOOLEANO || resDir.tipo != TokenType.BOOLEANO) throw new ErroSemanticoException("Operador 'e' exige operandos booleanos.", op);
                tipoResultante = TokenType.BOOLEANO;
            } else {
                if (resAcumulado.tipo != TokenType.INTEIRO || resDir.tipo != TokenType.INTEIRO) throw new ErroSemanticoException("Operadores '*' e '/' exigem operandos inteiros.", op);
                tipoResultante = TokenType.INTEIRO;
            }

            String temp = gerador.novoTemp();
            gerador.gerar(op.lexeme, resAcumulado.nome, resDir.nome, temp);
            resAcumulado = new ResultadoExpressao(temp, tipoResultante);
        }
        return resAcumulado;
    }

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
                argumentos_reais_opc(s); // Passa o símbolo da função para validação
                String temp = gerador.novoTemp();
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

    // *** MÉTODOS REESTRUTURADOS PARA VALIDAÇÃO DE ARGUMENTOS ***
    private void argumentos_reais_opc(Simbolo subRotina) {
        if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            lista_argumentos(subRotina);
            consumir(TokenType.FECHA_PAREN);
        } else { // Chamada sem parênteses (ex: "meu_proc;")
            if (!subRotina.parametros.isEmpty()) {
                throw new ErroSemanticoException("Esperado " + subRotina.parametros.size() + " argumentos para '" + subRotina.nome + "', mas 0 foi fornecido.", tokenAtual);
            }
        }
    }

    private void lista_argumentos(Simbolo subRotina) {
        int indiceArgumento = 0;
        List<Simbolo> parametros = subRotina.parametros;

        if (!verificar(TokenType.FECHA_PAREN)) { // Verifica se há pelo menos um argumento
            // Lida com o primeiro argumento
            if (indiceArgumento >= parametros.size()) {
                throw new ErroSemanticoException("Número excessivo de argumentos para a chamada de '" + subRotina.nome + "'.", tokenAtual);
            }
            ResultadoExpressao resExpr = expressao();
            Simbolo parametroEsperado = parametros.get(indiceArgumento);
            if (resExpr.tipo != parametroEsperado.tipo) {
                throw new ErroSemanticoException(
                        "Tipo de argumento incompatível na chamada de '" + subRotina.nome +
                                "'. Parâmetro " + (indiceArgumento + 1) + " espera " + parametroEsperado.tipo +
                                " mas recebeu " + resExpr.tipo + ".",
                        tokenAtual
                );
            }
            gerador.gerar("param", resExpr.nome, null, null);
            indiceArgumento++;

            // Lida com os argumentos subsequentes
            while (verificar(TokenType.VIRGULA)) {
                consumir(TokenType.VIRGULA);

                if (indiceArgumento >= parametros.size()) {
                    throw new ErroSemanticoException("Número excessivo de argumentos para a chamada de '" + subRotina.nome + "'.", tokenAtual);
                }
                resExpr = expressao();
                parametroEsperado = parametros.get(indiceArgumento);
                if (resExpr.tipo != parametroEsperado.tipo) {
                    throw new ErroSemanticoException(
                            "Tipo de argumento incompatível na chamada de '" + subRotina.nome +
                                    "'. Parâmetro " + (indiceArgumento + 1) + " espera " + parametroEsperado.tipo +
                                    " mas recebeu " + resExpr.tipo + ".",
                            tokenAtual
                    );
                }
                gerador.gerar("param", resExpr.nome, null, null);
                indiceArgumento++;
            }
        }

        if (indiceArgumento < parametros.size()) {
            throw new ErroSemanticoException("Faltam argumentos para a chamada de '" + subRotina.nome + "'. Esperado " + parametros.size() + ", mas fornecido " + indiceArgumento + ".", tokenAtual);
        }
    }
}