package com.minhaempresa.gendaz.admin.service;

import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import java.time.LocalDateTime;

public record AdminSession(String token, UsuarioEntity admin, LocalDateTime criadoEm) {}

