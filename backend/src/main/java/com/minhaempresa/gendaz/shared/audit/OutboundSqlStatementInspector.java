package com.minhaempresa.gendaz.shared.audit;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboundSqlStatementInspector implements StatementInspector {
    private final OutboundTrafficAuditService auditService;
    private final boolean enabled;

    public OutboundSqlStatementInspector(
            OutboundTrafficAuditService auditService,
            @Value("${app.outbound-audit.enabled:false}") boolean enabled
    ) {
        this.auditService = auditService;
        this.enabled = enabled;
    }

    @Override
    public String inspect(String sql) {
        if (!enabled || sql == null || sql.isBlank()) {
            return sql;
        }
        String origem = localizarOrigem();
        auditService.registrarPostgres(origem, sql, auditService.bytesUtf8(sql));
        return sql;
    }

    private String localizarOrigem() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .filter(frame -> {
                            String nome = frame.getClassName();
                            return nome.startsWith("com.minhaempresa.gendaz.")
                                    && !nome.contains(".shared.audit.")
                                    && !nome.contains(".repository.");
                        })
                        .map(frame -> frame.getClassName().substring("com.minhaempresa.gendaz.".length()) + "#" + frame.getMethodName())
                        .findFirst()
                        .orElse("unknown#unknown"));
    }
}

