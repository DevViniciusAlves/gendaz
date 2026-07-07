package com.minhaempresa.agendapro.admin.service;

import com.minhaempresa.agendapro.usuario.entity.UsuarioEntity;
import java.time.LocalDateTime;

public record AdminSession(String token, UsuarioEntity admin, LocalDateTime criadoEm) {}
