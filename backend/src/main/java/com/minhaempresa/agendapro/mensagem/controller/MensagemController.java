package com.minhaempresa.agendapro.mensagem.controller;

import com.minhaempresa.agendapro.mensagem.dto.MensagemDtos.EnviarHorariosRequest;
import com.minhaempresa.agendapro.mensagem.dto.MensagemDtos.EnviarMensagemRequest;
import com.minhaempresa.agendapro.mensagem.dto.MensagemDtos.MensagemResponse;
import com.minhaempresa.agendapro.mensagem.service.MensagemService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mensagens")
@RequiredArgsConstructor
public class MensagemController {
    private final MensagemService mensagemService;

    @GetMapping("/conversa/{conversaId}")
    public ResponseEntity<List<MensagemResponse>> listarPorConversa(@PathVariable Long conversaId) {
        return ResponseEntity.ok(mensagemService.listarPorConversa(conversaId));
    }

    @PostMapping("/enviar")
    public ResponseEntity<MensagemResponse> enviar(@Valid @RequestBody EnviarMensagemRequest request) {
        return ResponseEntity.ok(mensagemService.enviar(request));
    }

    @PostMapping("/enviar-horarios-disponiveis")
    public ResponseEntity<MensagemResponse> enviarHorariosDisponiveis(@Valid @RequestBody EnviarHorariosRequest request) {
        return ResponseEntity.ok(mensagemService.enviarHorariosDisponiveis(request));
    }
}
