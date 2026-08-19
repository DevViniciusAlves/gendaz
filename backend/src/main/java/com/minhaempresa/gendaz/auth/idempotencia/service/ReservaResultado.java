package com.minhaempresa.gendaz.auth.idempotencia.service;

import com.minhaempresa.gendaz.auth.idempotencia.entity.CadastroIdempotenciaEntity;

/**
 * Resultado da tentativa de reservar/avaliar uma chave de idempotencia.
 *
 * RESERVADO -> esta request ganhou o direito de executar o cadastro.
 * COMPLETADO -> a chave ja concluiu um cadastro; resposta deve ser reconstruida.
 * EM_PROCESSAMENTO -> outra request esta processando a mesma chave agora.
 */
public record ReservaResultado(TipoReserva tipo, CadastroIdempotenciaEntity registro) {

    public static ReservaResultado reservado(CadastroIdempotenciaEntity registro) {
        return new ReservaResultado(TipoReserva.RESERVADO, registro);
    }

    public static ReservaResultado completado(CadastroIdempotenciaEntity registro) {
        return new ReservaResultado(TipoReserva.COMPLETADO, registro);
    }

    public static ReservaResultado emProcessamento(CadastroIdempotenciaEntity registro) {
        return new ReservaResultado(TipoReserva.EM_PROCESSAMENTO, registro);
    }

    public enum TipoReserva {
        RESERVADO,
        COMPLETADO,
        EM_PROCESSAMENTO
    }
}
