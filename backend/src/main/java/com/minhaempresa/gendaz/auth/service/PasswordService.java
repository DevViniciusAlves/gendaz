package com.minhaempresa.gendaz.auth.service;

import com.minhaempresa.gendaz.shared.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
public class PasswordService {
    private static final String BCRYPT_PREFIX = "$2";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String hash(String senha) {
        validarSenha(senha);
        return encoder.encode(senha);
    }

    public boolean matches(String senha, String senhaSalva) {
        if (senhaSalva == null || senhaSalva.isBlank()) return false;

        if (senhaSalva.startsWith(BCRYPT_PREFIX)) {
            return encoder.matches(senha, senhaSalva);
        }

        return false;
    }

    public void validarSenha(String senha) {
        if (senha == null || senha.length() < 8 || senha.length() > 72) {
            throw new BusinessException("A senha deve ter entre 8 e 72 caracteres.");
        }
        if (!senha.matches(".*[a-z].*")
                || !senha.matches(".*[A-Z].*")
                || !senha.matches(".*\\d.*")
                || !senha.matches(".*[^A-Za-z0-9].*")) {
            throw new BusinessException("A senha deve ter letra maiuscula, letra minuscula, numero e caractere especial.");
        }
    }

}

