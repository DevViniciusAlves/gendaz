package com.minhaempresa.gendaz.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import com.minhaempresa.gendaz.shared.security.SessionAuthenticationFilter;

@Configuration
public class SecurityHeadersConfig {
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionAuthenticationFilter sessionAuthenticationFilter) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .sameSite("None")
                .secure(true));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(CSRF_IGNORADOS))
                .addFilterBefore(sessionAuthenticationFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .addFilterAfter(new com.minhaempresa.gendaz.shared.security.CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/api/health").permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/criar-conta",
                                "/api/auth/recuperar-senha",
                                "/api/auth/redefinir-senha",
                                "/api/auth/csrf",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/meu-gendaz/auth/solicitar-codigo",
                                "/api/meu-gendaz/auth/validar-codigo",
                                "/api/meu-gendaz/auth/refresh",
                                "/api/public/**",
                                "/api/pagamentos/webhook",
                                "/api/pagamentos/planos/webhook",
                                "/api/pagamentos/planos/webhook/cakto"
                        ).permitAll()
                        .requestMatchers("/api/admin/**", "/admin", "/admin/**").authenticated()
                        .requestMatchers("/api/meu-gendaz/**").authenticated()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
                        .contentTypeOptions(Customizer.withDefaults())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'self';"
                        ))
                );
        return http.build();
    }
}

