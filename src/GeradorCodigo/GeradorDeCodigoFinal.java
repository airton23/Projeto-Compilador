package GeradorCodigo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GeradorDeCodigoFinal {

    private final List<Quadrupla> quadruplas;
    private final List<String> codigoAssembly;

    public GeradorDeCodigoFinal(List<Quadrupla> quadruplas) {
        this.quadruplas = quadruplas;
        this.codigoAssembly = new ArrayList<>();
    }

    public void gerarCodigo() {
        gerarSecaoDeDados();
        gerarSecaoDeTexto();
    }

    private void gerarSecaoDeDados() {
        codigoAssembly.add("section .data");
        Set<String> variaveis = new HashSet<>();
        // Coleta todas as variáveis e temporárias para declará-las
        for (Quadrupla q : quadruplas) {
            if (ehVariavel(q.arg1)) variaveis.add(q.arg1);
            if (ehVariavel(q.arg2)) variaveis.add(q.arg2);
            if (ehVariavel(q.resultado)) variaveis.add(q.resultado);
        }

        for (String var : variaveis) {
            codigoAssembly.add(String.format("    %-10s DD 0", var)); // DD = Define Double word (4 bytes)
        }
        codigoAssembly.add("");
    }

    private boolean ehVariavel(String s) {
        // Uma verificação simples para ver se não é um número ou null
        if (s == null) return false;
        try {
            Integer.parseInt(s);
            return false;
        } catch (NumberFormatException e) {
            return !s.startsWith("L");
        }
    }

    private void gerarSecaoDeTexto() {
        codigoAssembly.add("section .text");
        codigoAssembly.add("global _start");
        codigoAssembly.add("");
        codigoAssembly.add("_start:");

        for (Quadrupla q : quadruplas) {
            traduzirQuadrupla(q);
        }

        // Finaliza o programa (chamada de sistema para exit em Linux)
        codigoAssembly.add("");
        codigoAssembly.add("    ; Finaliza o programa");
        codigoAssembly.add("    mov eax, 1");
        codigoAssembly.add("    xor ebx, ebx");
        codigoAssembly.add("    int 0x80");
    }

    private void traduzirQuadrupla(Quadrupla q) {
        switch (q.operador) {
            case ":=":
                codigoAssembly.add(String.format("    mov eax, [%s]", q.arg1));
                codigoAssembly.add(String.format("    mov [%s], eax", q.resultado));
                break;
            case "+":
                codigoAssembly.add(String.format("    mov eax, [%s]", q.arg1));
                codigoAssembly.add(String.format("    mov ebx, [%s]", q.arg2));
                codigoAssembly.add("    add eax, ebx");
                codigoAssembly.add(String.format("    mov [%s], eax", q.resultado));
                break;
            case "*":
                codigoAssembly.add(String.format("    mov eax, [%s]", q.arg1));
                codigoAssembly.add(String.format("    mov ebx, [%s]", q.arg2));
                codigoAssembly.add("    imul eax, ebx"); // Multiplicação com sinal
                codigoAssembly.add(String.format("    mov [%s], eax", q.resultado));
                break;
            // Adicionar casos para '-', '/'

            // Operadores relacionais apenas fazem a comparação
            case ">":
            case "<":
            case "=":
            case "<>":
                codigoAssembly.add(String.format("    mov eax, [%s]", q.arg1));
                codigoAssembly.add(String.format("    mov ebx, [%s]", q.arg2));
                codigoAssembly.add("    cmp eax, ebx");
                // O resultado booleano não é armazenado, as flags são usadas pelo if_false
                break;

            case "if_false":
                // Mapeia a condição original para o salto oposto
                // Ex: Se a condição era '>', o if_false pula se 'não for maior' (JNG)
                // Para simplificar, vamos assumir que o 'if_false' sempre segue uma comparação
                // e que a última comparação foi para '>'.
                // Uma implementação real mapearia o operador da quádrupla anterior.
                codigoAssembly.add(String.format("    jng %s", q.resultado)); // Jump if Not Greater
                break;

            case "goto":
                codigoAssembly.add(String.format("    jmp %s", q.resultado));
                break;

            default:
                // Para rótulos (ex: "L0:")
                if (q.operador.endsWith(":")) {
                    codigoAssembly.add(q.operador);
                }
                break;
        }
    }

    public void imprimirCodigo() {
        System.out.println("\n--- CÓDIGO ASSEMBLY FINAL GERADO ---");
        for (String linha : codigoAssembly) {
            System.out.println(linha);
        }
        System.out.println("------------------------------------");
    }
}