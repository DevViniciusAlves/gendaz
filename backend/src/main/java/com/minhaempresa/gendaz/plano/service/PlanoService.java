package com.minhaempresa.gendaz.plano.service;

import com.minhaempresa.gendaz.plano.dto.PlanoDtos.PlanoResponse;
import com.minhaempresa.gendaz.plano.entity.PlanoEntity;
import com.minhaempresa.gendaz.plano.mapper.PlanoMapper;
import com.minhaempresa.gendaz.plano.repository.PlanoRepository;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.shared.ResourceNotFoundException;
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
                .filter(plano -> plano.getNome().equals("BASICO") || plano.getNome().equals("PRO") || plano.getNome().equals("PLUS") || plano.getNome().equals("ENTERPRISE"))
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
        if (nome == null || (!nome.equals("BASICO") && !nome.equals("PRO") && !nome.equals("PLUS") && !nome.equals("ENTERPRISE"))) {
            throw new BusinessException("Plano invalido. Escolha BASICO, PRO, PLUS ou ENTERPRISE.");
        }
        return planoRepository.findByNome(nome)
                .orElseThrow(() -> new ResourceNotFoundException("Plano nao encontrado."));
    }
}

