package com.minhaempresa.agendapro.mensagem.service;

import com.minhaempresa.agendapro.agendamento.service.AgendamentoService;
import com.minhaempresa.agendapro.conversa.entity.ConversaEntity;
import com.minhaempresa.agendapro.conversa.service.ConversaService;
import com.minhaempresa.agendapro.mensagem.dto.MensagemDtos.EnviarHorariosRequest;
import com.minhaempresa.agendapro.mensagem.dto.MensagemDtos.EnviarMensagemRequest;
import com.minhaempresa.agendapro.mensagem.dto.MensagemDtos.MensagemResponse;
import com.minhaempresa.agendapro.mensagem.entity.MensagemEntity;
import com.minhaempresa.agendapro.mensagem.enums.DirecaoMensagem;
import com.minhaempresa.agendapro.mensagem.enums.TipoMensagem;
import com.minhaempresa.agendapro.mensagem.gateway.WhatsappGateway;
import com.minhaempresa.agendapro.mensagem.mapper.MensagemMapper;
import com.minhaempresa.agendapro.mensagem.repository.MensagemRepository;
import com.minhaempresa.agendapro.shared.SanitizacaoService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MensagemService {
    private final MensagemRepository mensagemRepository;
    private final ConversaService conversaService;
    private final AgendamentoService agendamentoService;
    private final WhatsappGateway whatsappGateway;
    private final SanitizacaoService sanitizacaoService;
    private final MensagemMapper mapper = new MensagemMapper();

    @Transactional(readOnly = true)
    public List<MensagemResponse> listarPorConversa(Long conversaId) {
        return mensagemRepository.findByConversaIdOrderByDataEnvioAsc(conversaId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public MensagemResponse enviar(EnviarMensagemRequest request) {
        ConversaEntity conversa = conversaService.buscarEntidade(request.conversaId());
        String conteudo = sanitizacaoService.textoObrigatorio(request.conteudo());
        whatsappGateway.enviarMensagem(conversa.getCliente().getTelefone(), conteudo);
        return salvar(conversa, conteudo, DirecaoMensagem.EMPRESA_PARA_CLIENTE, TipoMensagem.TEXTO);
    }

    @Transactional
    public MensagemResponse enviarHorariosDisponiveis(EnviarHorariosRequest request) {
        ConversaEntity conversa = conversaService.buscarEntidade(request.conversaId());
        String horarios = String.join(", ", agendamentoService.horariosDisponiveis(request.empresaId(), request.profissionalId(), request.servicoId(), request.data()));
        String conteudo = sanitizacaoService.textoObrigatorio("Horarios disponiveis para " + request.data() + ": " + horarios);
        whatsappGateway.enviarMensagem(conversa.getCliente().getTelefone(), conteudo);
        return salvar(conversa, conteudo, DirecaoMensagem.EMPRESA_PARA_CLIENTE, TipoMensagem.HORARIOS_DISPONIVEIS);
    }

    @Transactional
    public MensagemResponse salvar(ConversaEntity conversa, String conteudo, DirecaoMensagem direcao, TipoMensagem tipo) {
        MensagemEntity mensagem = MensagemEntity.builder()
                .conversa(conversa)
                .conteudo(conteudo)
                .direcao(direcao)
                .tipo(tipo)
                .dataEnvio(LocalDateTime.now())
                .build();
        conversaService.atualizarUltimaMensagem(conversa, conteudo);
        return mapper.toResponse(mensagemRepository.save(mensagem));
    }
}
