package com.minhaempresa.agendapro.plano.service;

import com.minhaempresa.agendapro.plano.dto.PlanoDtos.PlanoResponse;
import com.minhaempresa.agendapro.plano.entity.PlanoEntity;
import com.minhaempresa.agendapro.plano.mapper.PlanoMapper;
import com.minhaempresa.agendapro.plano.repository.PlanoRepository;
import com.minhaempresa.agendapro.shared.BusinessException;
import com.minhaempresa.agendapro.shared.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanoService {
    private final PlanoRepository planoRepository;
    private final PlanoMapper mapper = new PlanoMapper();

    @Transactional(readOnly = true)
    public List<PlanoResponse> listar() {
        return planoRepository.findAll().stream()
                .filter(plano -> plano.getNome().equals("BASICO") || plano.getNome().equals("PRO"))
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanoEntity buscarEntidade(Long id) {
        return planoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plano nao encontrado."));
    }

    @Transactional(readOnly = true)
    public PlanoEntity buscarPorNomePermitido(String nome) {
        if (nome == null || (!nome.equals("BASICO") && !nome.equals("PRO"))) {
            throw new BusinessException("Plano invalido. Escolha BASICO ou PRO.");
        }
        return planoRepository.findByNome(nome)
                .orElseThrow(() -> new ResourceNotFoundException("Plano nao encontrado."));
    }
}
