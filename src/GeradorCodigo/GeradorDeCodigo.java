package GeradorCodigo;

import java.util.ArrayList;
import java.util.List;

public class GeradorDeCodigo {
    private final List<Quadrupla> quadruplas = new ArrayList<>();
    private int contadorTemp = 0;
    private int contadorRotulo = 0;

    public String novoTemp() {
        return "t" + contadorTemp++;
    }

    public String novoRotulo() {
        return "L" + contadorRotulo++;
    }

    public void gerar(String op, String arg1, String arg2, String res) {
        quadruplas.add(new Quadrupla(op, arg1, arg2, res));
    }

    public void imprimirCodigo() {
        System.out.println("\n--- CÓDIGO INTERMEDIÁRIO GERADO ---");
        for (int i = 0; i < quadruplas.size(); i++) {
            System.out.printf("%-4d: %s\n", i, quadruplas.get(i));
        }
        System.out.println("------------------------------------");
    }

    public List<Quadrupla> getQuadruplas() {
        return this.quadruplas;
    }
}