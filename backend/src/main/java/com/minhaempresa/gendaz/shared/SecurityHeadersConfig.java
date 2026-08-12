package com.minhaempresa.gendaz.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityHeadersConfig {
    @Value("${app.frontend-url:https://gendaz.site}")
    private String frontendUrl;

    private static final String[] CSRF_IGNORADOS = {
            "/api/health",
            "/health",
            "/api/public/**",
            "/api/auth/recuperar-senha",
            "/api/auth/redefinir-senha",
            "/api/meu-gendaz/auth/solicitar-codigo",
            "/api/meu-gendaz/auth/validar-codigo",
            "/api/pagamentos/webhook",
            "/api/pagamentos/planos/webhook",
            "/api/pagamentos/planos/webhook/cakto"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .sameSite("None")
                .secure(true));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(CSRF_IGNORADOS))
                .addFilterAfter(new com.minhaempresa.gendaz.shared.security.CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/api/health").permitAll()
                        .anyRequest().permitAll())
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
                        .contentTypeOptions(Customizer.withDefaults())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data: https:; "
                                        + "connect-src 'self' " + frontendUrl + " https://gendaz.site https://www.gendaz.site http://localhost:5173 http://127.0.0.1:5173 http://localhost:5174 http://127.0.0.1:5174; "
                                        + "frame-ancestors 'self';"
                        ))
                );
        return http.build();
    }
}

