package com.minhaempresa.gendaz.agendamentopublico.dto;

import com.minhaempresa.gendaz.agendamento.dto.AgendamentoDtos.AgendamentoResponse;
import com.minhaempresa.gendaz.configuracao.dto.HorarioAtendimentoDtos.HorarioAtendimentoResponse;
import com.minhaempresa.gendaz.shared.TelefoneInternacional;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.minhaempresa.gendaz.profissional.enums.DiaSemana;
import com.minhaempresa.gendaz.shared.enums.StatusCadastro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public final class AgendamentoPublicoDtos {
    private AgendamentoPublicoDtos() {}

    public record BookingEmpresaResponse(
            String slug,
            String nomeFantasia,
            boolean disponivel,
            String mensagem,
            List<BookingServicoResponse> servicos,
            List<BookingProfissionalResponse> profissionais,
            List<HorarioAtendimentoResponse> horariosAtendimento
    ) {}

    public record BookingServicoResponse(
            Long id,
            String nome,
            String descricao,
            Integer duracaoMinutos,
            BigDecimal valor
    ) {}

    public record BookingProfissionalResponse(
            Long id,
            String nome,
            String especialidade,
            StatusCadastro status,
            Set<DiaSemana> diasTrabalho
    ) {}

    public record CriarAgendamentoPublicoRequest(
            @NotNull Long servicoId,
            Long profissionalId,
            @NotNull LocalDate data,
            @NotNull LocalTime horaInicio,
            String cupomCodigo,
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Nome deve conter apenas letras.") String clienteNome,
            @NotBlank @TelefoneInternacional String clienteTelefone,
            @Email @Size(max = 120) String clienteEmail,
            @Size(max = 500) String observacao
    ) {}

    public record AgendamentoPublicoResponse(
            String mensagem,
            AgendamentoResponse agendamento
    ) {}
}

