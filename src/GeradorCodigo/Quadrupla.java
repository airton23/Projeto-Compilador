package GeradorCodigo;

public class Quadrupla {
    public final String operador;
    public final String arg1;
    public final String arg2;
    public final String resultado;

    public Quadrupla(String operador, String arg1, String arg2, String resultado) {
        this.operador = operador;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.resultado = resultado;
    }

    @Override
    public String toString() {
        if (operador.endsWith(":")) { // É um rótulo (label)
            return String.format("%-10s", operador);
        }
        if (operador.equals("goto") || operador.startsWith("if")) {
            return String.format("%-10s %-10s %-10s %-10s", operador, arg1, arg2, resultado);
        }
        if (arg2 == null) { // Operação unária ou cópia
            if (operador.equals("escreva") || operador.equals("retorno")) {
                return String.format("%-10s %-10s", operador, arg1);
            }
            return String.format("%-10s := %-10s %-10s", resultado, operador, arg1);
        }
        // Operação binária
        return String.format("%-10s := %-10s %-10s %-10s", resultado, arg1, operador, arg2);
    }
}