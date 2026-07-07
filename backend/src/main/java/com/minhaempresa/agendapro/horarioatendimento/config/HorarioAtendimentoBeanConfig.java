package com.minhaempresa.agendapro.horarioatendimento.config;

import com.minhaempresa.agendapro.horarioatendimento.repository.HorarioAtendimentoRepository;
import com.minhaempresa.agendapro.horarioatendimento.service.HorarioAtendimentoService;
import com.minhaempresa.agendapro.usuario.repository.UsuarioRepository;
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
