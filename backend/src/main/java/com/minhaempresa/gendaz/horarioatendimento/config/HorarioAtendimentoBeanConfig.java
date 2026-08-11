package com.minhaempresa.gendaz.horarioatendimento.config;

import com.minhaempresa.gendaz.horarioatendimento.repository.HorarioAtendimentoRepository;
import com.minhaempresa.gendaz.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HorarioAtendimentoBeanConfig {
    @Bean
    public HorarioAtendimentoService horarioAtendimentoService(
            HorarioAtendimentoRepository horarioAtendimentoRepository,
            UsuarioRepository usuarioRepository
    ) {
        return new HorarioAtendimentoService(horarioAtendimentoRepository, usuarioRepository);
    }
}

