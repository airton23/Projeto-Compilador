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
        if (operador != null && operador.endsWith(":")) {
            return String.format("%-10s", operador);           // rótulo
        }
        if (":=".equals(operador)) {
            return String.format("%-10s := %-10s", resultado, arg1); // cópia/atr
        }
        if ("goto".equals(operador)) {
            return String.format("%-10s %-10s", "goto", resultado);  // goto Lk
        }
        if ("if_false".equals(operador)) {
            return String.format("%-10s %-10s %-10s %-10s", "if_false", arg1, "goto", resultado);
        }
        if ("param".equals(operador)) {
            return String.format("%-10s %-10s", "param", arg1);
        }
        if ("call".equals(operador)) {
            return (resultado != null)
                    ? String.format("%-10s := %-10s %-10s", resultado, "call", arg1)
                    : String.format("%-10s %-10s", "call", arg1);
        }
        if (arg2 == null) { // unária (ex.: nao) ou I/O/retorno
            if ("escreva".equals(operador) || "retorno".equals(operador)) {
                return String.format("%-10s %-10s", operador, arg1);
            }
            return String.format("%-10s := %-10s %-10s", resultado, operador, arg1);
        }
        return String.format("%-10s := %-10s %-10s %-10s", resultado, arg1, operador, arg2); // binária
    }
}