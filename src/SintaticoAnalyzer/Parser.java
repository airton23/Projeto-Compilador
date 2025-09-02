package SintaticoAnalyzer;

import LexicalAnalyzer.LexicalAnalyzer;
import LexicalAnalyzer.Token;
import LexicalAnalyzer.TokenType;
import SemanticsAnalyzer.Simbolo;
import SemanticsAnalyzer.TabelaDeSimbolos;
import SemanticsAnalyzer.TipoSimbolo;

import java.util.ArrayList;
import java.util.List;

public class Parser {

    private final LexicalAnalyzer lexer;
    private Token tokenAtual;

    // ANÁLISE SEMÂNTICA: Instância da tabela de símbolos para controle de escopo e declarações.
    private final TabelaDeSimbolos tabelaDeSimbolos;

    // Variáveis de estado para controle semântico contextual (laços, funções, etc.).
    private boolean dentroDeLaco = false;
    private enum CategoriaSubRotina { NENHUMA, FUNCAO, PROCEDIMENTO }
    private CategoriaSubRotina subRotinaAtual = CategoriaSubRotina.NENHUMA;
    // ANÁLISE SEMÂNTICA: Armazena o símbolo da função atual para verificar o tipo de retorno.
    private Simbolo funcaoAtual = null;

    public Parser(LexicalAnalyzer lexer) {
        this.lexer = lexer;
        // ANÁLISE SEMÂNTICA: Inicializa a tabela de símbolos. O escopo global é aberto no construtor da tabela.
        this.tabelaDeSimbolos = new TabelaDeSimbolos();
        this.tokenAtual = this.lexer.obterProximoToken();
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
        Token idPrograma = tokenAtual;
        consumir(TokenType.ID);
        // ANÁLISE SEMÂNTICA: Insere o nome do programa na tabela de símbolos do escopo global.
        try {
            tabelaDeSimbolos.inserir(new Simbolo(idPrograma.lexeme, TipoSimbolo.PROGRAMA, null));
        } catch (RuntimeException e) {
            throw new ErroSemanticoException(e.getMessage(), idPrograma);
        }
        consumir(TokenType.PONTO_E_VIRGULA);
        bloco();
        consumir(TokenType.PONTO);
        // ANÁLISE SEMÂNTICA: Fecha o escopo global ao final da compilação.
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

    // MODIFICADO: para realizar a análise semântica da declaração de variáveis.
    private void decl_var() {
        List<Token> identificadores = new ArrayList<>();
        lista_id(identificadores); // Coleta todos os identificadores (ex: a, b, c: inteiro)
        consumir(TokenType.DOIS_PONTOS);
        TokenType tipoVariavel = tipo(); // Obtém o tipo (INTEIRO ou BOOLEANO)

        // ANÁLISE SEMÂNTICA: Insere cada variável declarada na tabela de símbolos do escopo atual.
        for (Token id : identificadores) {
            try {
                tabelaDeSimbolos.inserir(new Simbolo(id.lexeme, TipoSimbolo.VARIAVEL, tipoVariavel));
            } catch (RuntimeException e) {
                // Lança um erro semântico se o identificador já foi declarado neste escopo.
                throw new ErroSemanticoException(e.getMessage(), id);
            }
        }
    }

    // MODIFICADO: para coletar os tokens de ID em uma lista.
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

    // MODIFICADO: para retornar o tipo encontrado.
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
        // A gramática original permite múltiplas declarações separadas por ';',
        // então mantemos o loop aqui.
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
        } else {
            // Este else não deve ser alcançado devido à condição do loop em decl_sub_opcional
        }
    }

    private void decl_proc() {
        consumir(TokenType.PROCEDIMENTO);
        Token idProc = tokenAtual;
        consumir(TokenType.ID);

        // ANÁLISE SEMÂNTICA: Insere o nome do procedimento no escopo atual ANTES de abrir um novo.
        try {
            tabelaDeSimbolos.inserir(new Simbolo(idProc.lexeme, TipoSimbolo.PROCEDIMENTO, null));
        } catch (RuntimeException e) {
            throw new ErroSemanticoException(e.getMessage(), idProc);
        }

        // ANÁLISE SEMÂNTICA: Abre um novo escopo para os parâmetros e variáveis locais do procedimento.
        tabelaDeSimbolos.abrirEscopo();

        parametros_formais_opc();
        consumir(TokenType.PONTO_E_VIRGULA);

        CategoriaSubRotina estadoAnterior = this.subRotinaAtual;
        this.subRotinaAtual = CategoriaSubRotina.PROCEDIMENTO;

        bloco();

        this.subRotinaAtual = estadoAnterior;
        // ANÁLISE SEMÂNTICA: Fecha o escopo do procedimento.
        tabelaDeSimbolos.fecharEscopo();
    }

    private void decl_func() {
        consumir(TokenType.FUNCAO);
        Token idFunc = tokenAtual;
        consumir(TokenType.ID);
        parametros_formais_opc(); // Parâmetros serão inseridos no novo escopo
        consumir(TokenType.DOIS_PONTOS);
        TokenType tipoRetorno = tipo();

        // ANÁLISE SEMÂNTICA: Cria o símbolo da função e o insere no escopo PAI.
        Simbolo simboloFuncao = new Simbolo(idFunc.lexeme, TipoSimbolo.FUNCAO, tipoRetorno);
        try {
            tabelaDeSimbolos.inserir(simboloFuncao);
        } catch (RuntimeException e) {
            throw new ErroSemanticoException(e.getMessage(), idFunc);
        }

        // ANÁLISE SEMÂNTICA: Abre um novo escopo para a função.
        tabelaDeSimbolos.abrirEscopo();

        consumir(TokenType.PONTO_E_VIRGULA);

        CategoriaSubRotina estadoAnteriorSub = this.subRotinaAtual;
        Simbolo estadoAnteriorFunc = this.funcaoAtual;
        this.subRotinaAtual = CategoriaSubRotina.FUNCAO;
        this.funcaoAtual = simboloFuncao;

        bloco();

        this.subRotinaAtual = estadoAnteriorSub;
        this.funcaoAtual = estadoAnteriorFunc;

        // ANÁLISE SEMÂNTICA: Fecha o escopo da função.
        tabelaDeSimbolos.fecharEscopo();
    }

    private void parametros_formais_opc() {
        if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            lista_parametros();
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
        // Reutiliza a lógica de declaração de variáveis para os parâmetros
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
            if (!this.dentroDeLaco) {
                throw new ErroSemanticoException("Comando 'break' só pode ser usado dentro de um laço 'enquanto'", tokenAtual);
            }
            consumir(TokenType.BREAK);
        } else if (verificar(TokenType.CONTINUE)) {
            if (!this.dentroDeLaco) {
                throw new ErroSemanticoException("Comando 'continue' só pode ser usado dentro de um laço 'enquanto'", tokenAtual);
            }
            consumir(TokenType.CONTINUE);
        } else {
            // Permite corpo de comando vazio antes do 'fim'
            if (!verificar(TokenType.FIM)) {
                throw new ErroSintaticoException("Esperado um comando válido (atribuição, se, enquanto, etc.)", tokenAtual);
            }
        }
    }

    private void comando_atr_chamada() {
        Token id = tokenAtual;
        consumir(TokenType.ID);

        // ANÁLISE SEMÂNTICA: Busca o identificador na tabela para ver se ele foi declarado.
        Simbolo simbolo = tabelaDeSimbolos.buscar(id.lexeme);
        if (simbolo == null) {
            throw new ErroSemanticoException("Identificador '" + id.lexeme + "' não foi declarado.", id);
        }

        atr_ou_chamada_proc(simbolo, id);
    }

    // ANÁLISE SEMÂNTICA: Este método agora recebe o símbolo encontrado para fazer as verificações.
    private void atr_ou_chamada_proc(Simbolo simbolo, Token token) {
        if (verificar(TokenType.ATRIBUICAO)) { // Atribuição: id := expressao
            // VERIFICAÇÃO DE CATEGORIA: Só se pode atribuir a variáveis ou ao nome da própria função (para retorno).
            boolean atribuicaoValida = simbolo.categoria == TipoSimbolo.VARIAVEL ||
                    (funcaoAtual != null && simbolo.nome.equals(funcaoAtual.nome));

            if (!atribuicaoValida) {
                throw new ErroSemanticoException("Atribuição inválida. '" + simbolo.nome + "' não é uma variável ou o nome da função atual.", token);
            }

            consumir(TokenType.ATRIBUICAO);
            TokenType tipoExpressao = expressao();

            // VERIFICAÇÃO DE TIPO: O tipo da expressão deve ser compatível com o tipo da variável/função.
            if (simbolo.tipo != tipoExpressao) {
                throw new ErroSemanticoException("Tipos incompatíveis. Não é possível atribuir um '" + tipoExpressao + "' a um '" + simbolo.tipo + "'.", token);
            }
        } else { // Chamada de procedimento: id(...) ou id
            // VERIFICAÇÃO DE CATEGORIA: Apenas procedimentos podem ser chamados desta forma.
            if (simbolo.categoria != TipoSimbolo.PROCEDIMENTO) {
                throw new ErroSemanticoException("'" + simbolo.nome + "' não é um procedimento. Apenas procedimentos podem ser chamados sem atribuição.", token);
            }
            argumentos_reais_opc();
        }
    }


    private void comando_condicional() {
        consumir(TokenType.SE);
        // ANÁLISE SEMÂNTICA: A expressão em um 'se' deve ser booleana.
        TokenType tipoExpr = expressao();
        if (tipoExpr != TokenType.BOOLEANO) {
            throw new ErroSemanticoException("A expressão da condição 'se' deve ser do tipo booleano, mas foi '" + tipoExpr + "'.", tokenAtual);
        }
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
        // ANÁLISE SEMÂNTICA: A expressão em um 'enquanto' deve ser booleana.
        TokenType tipoExpr = expressao();
        if (tipoExpr != TokenType.BOOLEANO) {
            throw new ErroSemanticoException("A expressão da condição 'enquanto' deve ser do tipo booleano, mas foi '" + tipoExpr + "'.", tokenAtual);
        }
        consumir(TokenType.FACA);

        boolean estadoAnterior = this.dentroDeLaco;
        this.dentroDeLaco = true;
        comando();
        this.dentroDeLaco = estadoAnterior;
    }

    private void comando_escrita() {
        consumir(TokenType.ESCREVA);
        consumir(TokenType.ABRE_PAREN);
        expressao(); // Pode escrever qualquer tipo, então não há verificação aqui.
        consumir(TokenType.FECHA_PAREN);
    }

    private void comando_retorno() {
        // VERIFICAÇÃO DE CONTEXTO: 'retorno' só é válido dentro de uma função.
        if (this.subRotinaAtual != CategoriaSubRotina.FUNCAO) {
            throw new ErroSemanticoException("Comando 'retorno' só pode ser usado dentro de uma função.", tokenAtual);
        }
        consumir(TokenType.RETORNO);
        TokenType tipoExpressao = expressao();

        // VERIFICAÇÃO DE TIPO: O tipo da expressão de retorno deve ser o mesmo do tipo de retorno da função.
        if (funcaoAtual.tipo != tipoExpressao) {
            throw new ErroSemanticoException("Tipo de retorno incompatível. A função '" + funcaoAtual.nome + "' espera '" + funcaoAtual.tipo + "', mas a expressão retornou '" + tipoExpressao + "'.", tokenAtual);
        }
    }

    // MODIFICADO: Os métodos de expressão agora retornam um TokenType para a checagem de tipos.
    private TokenType expressao() {
        TokenType tipoEsq = expressao_simples();
        // Se houver um operador relacional, o resultado da expressão é sempre booleano.
        if (verificar(TokenType.IGUAL) || verificar(TokenType.DIFERENTE) || verificar(TokenType.MENOR) ||
                verificar(TokenType.MENOR_IGUAL) || verificar(TokenType.MAIOR) || verificar(TokenType.MAIOR_IGUAL)) {
            op_rel();
            TokenType tipoDir = expressao_simples();
            // ANÁLISE SEMÂNTICA: Operadores relacionais só podem comparar tipos iguais (ambos inteiros).
            if (tipoEsq != TokenType.INTEIRO || tipoDir != TokenType.INTEIRO) {
                throw new ErroSemanticoException("Operadores relacionais (<, >, =, etc) só podem ser usados entre expressões do tipo inteiro.", tokenAtual);
            }
            return TokenType.BOOLEANO; // O resultado de uma comparação é sempre booleano.
        }
        return tipoEsq; // Se não houver operador relacional, o tipo é o da expressão simples.
    }

    private void op_rel() {
        avancarToken();
    }

    private TokenType expressao_simples() {
        sinal_opcional();
        TokenType tipoAcumulado = termo();

        while (verificar(TokenType.MAIS) || verificar(TokenType.MENOS) || verificar(TokenType.OU)) {
            Token op = tokenAtual;
            op_soma();
            TokenType tipoDir = termo();

            // ANÁLISE SEMÂNTICA: Checagem de tipo para operadores aditivos.
            if (op.type == TokenType.OU) {
                if (tipoAcumulado != TokenType.BOOLEANO || tipoDir != TokenType.BOOLEANO) {
                    throw new ErroSemanticoException("O operador 'ou' só pode ser usado entre expressões booleanas.", op);
                }
                tipoAcumulado = TokenType.BOOLEANO;
            } else { // MAIS ou MENOS
                if (tipoAcumulado != TokenType.INTEIRO || tipoDir != TokenType.INTEIRO) {
                    throw new ErroSemanticoException("Os operadores '+' e '-' só podem ser usados entre expressões inteiras.", op);
                }
                tipoAcumulado = TokenType.INTEIRO;
            }
        }
        return tipoAcumulado;
    }

    private void sinal_opcional() {
        if (verificar(TokenType.MAIS) || verificar(TokenType.MENOS)) {
            avancarToken();
        }
    }

    private void op_soma() {
        avancarToken();
    }

    private TokenType termo() {
        TokenType tipoAcumulado = fator();
        while (verificar(TokenType.MULT) || verificar(TokenType.DIVISAO) || verificar(TokenType.E)) {
            Token op = tokenAtual;
            op_mult();
            TokenType tipoDir = fator();

            // ANÁLISE SEMÂNTICA: Checagem de tipo para operadores multiplicativos.
            if (op.type == TokenType.E) {
                if (tipoAcumulado != TokenType.BOOLEANO || tipoDir != TokenType.BOOLEANO) {
                    throw new ErroSemanticoException("O operador 'e' só pode ser usado entre expressões booleanas.", op);
                }
                tipoAcumulado = TokenType.BOOLEANO;
            } else { // MULT ou DIVISAO
                if (tipoAcumulado != TokenType.INTEIRO || tipoDir != TokenType.INTEIRO) {
                    throw new ErroSemanticoException("Os operadores '*' e '/' só podem ser usados entre expressões inteiras.", op);
                }
                tipoAcumulado = TokenType.INTEIRO;
            }
        }
        return tipoAcumulado;
    }

    private void op_mult() {
        avancarToken();
    }

    private TokenType fator() {
        if (verificar(TokenType.ID)) {
            Token id = tokenAtual;
            consumir(TokenType.ID);

            // ANÁLISE SEMÂNTICA: Busca o símbolo para determinar se é uma variável ou chamada de função.
            Simbolo simbolo = tabelaDeSimbolos.buscar(id.lexeme);
            if (simbolo == null) {
                throw new ErroSemanticoException("Identificador '" + id.lexeme + "' não foi declarado.", id);
            }

            if (verificar(TokenType.ABRE_PAREN)) { // É uma chamada de função
                if (simbolo.categoria != TipoSimbolo.FUNCAO) {
                    throw new ErroSemanticoException("'" + id.lexeme + "' não é uma função e não pode ser chamado com argumentos.", id);
                }
                argumentos_reais_opc();
                return simbolo.tipo; // O tipo do fator é o tipo de retorno da função.
            } else { // É uma variável
                if (simbolo.categoria != TipoSimbolo.VARIAVEL) {
                    throw new ErroSemanticoException("'" + id.lexeme + "' não é uma variável. Esperado uso de variável em expressão.", id);
                }
                return simbolo.tipo; // O tipo do fator é o tipo da variável.
            }
        } else if (verificar(TokenType.NUMERO)) {
            consumir(TokenType.NUMERO);
            return TokenType.INTEIRO;
        } else if (verificar(TokenType.ABRE_PAREN)) {
            consumir(TokenType.ABRE_PAREN);
            TokenType tipo = expressao();
            consumir(TokenType.FECHA_PAREN);
            return tipo;
        } else if (verificar(TokenType.VERDADEIRO) || verificar(TokenType.FALSO)) {
            avancarToken();
            return TokenType.BOOLEANO;
        } else if (verificar(TokenType.NAO)) {
            consumir(TokenType.NAO);
            TokenType tipo = fator();
            // ANÁLISE SEMÂNTICA: 'nao' só pode ser aplicado a booleanos.
            if (tipo != TokenType.BOOLEANO) {
                throw new ErroSemanticoException("O operador 'nao' só pode ser aplicado a uma expressão booleana.", tokenAtual);
            }
            return TokenType.BOOLEANO;
        } else {
            throw new ErroSintaticoException("Esperado um fator (ID, número, expressão entre parênteses, etc.)", tokenAtual);
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