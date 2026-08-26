package com.minhaempresa.gendaz.shared.audit;

import java.util.Map;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboundAuditConfig {
    @Bean
    public HibernatePropertiesCustomizer outboundTrafficStatementInspector(OutboundSqlStatementInspector statementInspector) {
        return (props) -> props.put("hibernate.session_factory.statement_inspector", statementInspector);
    }
}

