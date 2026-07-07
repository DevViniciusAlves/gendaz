package com.minhaempresa.agendapro.shared;

import com.minhaempresa.agendapro.empresa.enums.TipoDocumento;

public final class DocumentoUtils {
    private DocumentoUtils() {}

    public static String normalizar(String documento) {
        return documento == null ? "" : documento.replaceAll("\\D", "");
    }

    public static void validar(TipoDocumento tipoDocumento, String documento) {
        String normalizado = normalizar(documento);
        if (tipoDocumento == null) {
            throw new BusinessException("Informe o tipo do documento.");
        }
        if (normalizado.isBlank()) {
            throw new BusinessException("Documento e obrigatorio.");
        }
        if (todosDigitosIguais(normalizado)) {
            throw new BusinessException("Documento invalido.");
        }
        switch (tipoDocumento) {
            case CPF -> validarCpf(normalizado);
            case CNPJ -> validarCnpj(normalizado);
        }
    }

    private static void validarCpf(String cpf) {
        if (cpf.length() != 11 || !algoritmoCpfValido(cpf)) {
            throw new BusinessException("CPF invalido.");
        }
    }

    private static void validarCnpj(String cnpj) {
        if (cnpj.length() != 14 || !algoritmoCnpjValido(cnpj)) {
            throw new BusinessException("CNPJ invalido.");
        }
    }

    private static boolean todosDigitosIguais(String valor) {
        return valor.chars().distinct().count() == 1;
    }

    private static boolean algoritmoCpfValido(String cpf) {
        int soma = 0;
        for (int i = 0; i < 9; i++) {
            soma += Character.getNumericValue(cpf.charAt(i)) * (10 - i);
        }
        int primeiroDigito = calcularDigitoCpf(soma);
        if (primeiroDigito != Character.getNumericValue(cpf.charAt(9))) {
            return false;
        }
        soma = 0;
        for (int i = 0; i < 10; i++) {
            soma += Character.getNumericValue(cpf.charAt(i)) * (11 - i);
        }
        int segundoDigito = calcularDigitoCpf(soma);
        return segundoDigito == Character.getNumericValue(cpf.charAt(10));
    }

    private static int calcularDigitoCpf(int soma) {
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean algoritmoCnpjValido(String cnpj) {
        int[] pesosPrimeiro = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesosSegundo = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int primeiro = calcularDigitoCnpj(cnpj, pesosPrimeiro);
        if (primeiro != Character.getNumericValue(cnpj.charAt(12))) {
            return false;
        }
        int segundo = calcularDigitoCnpj(cnpj, pesosSegundo);
        return segundo == Character.getNumericValue(cnpj.charAt(13));
    }

    private static int calcularDigitoCnpj(String cnpj, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.getNumericValue(cnpj.charAt(i)) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
