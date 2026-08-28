package com.minhaempresa.gendaz.shared;

import com.minhaempresa.gendaz.shared.security.AdminAuthenticationFilter;
import com.minhaempresa.gendaz.shared.security.AdminImpersonationGuardFilter;
import com.minhaempresa.gendaz.shared.security.GendazSessionAuthenticationFilter;
import com.minhaempresa.gendaz.shared.security.MeuGendazSessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityHeadersConfig {
    @Value("${app.frontend-url:https://gendaz.site}")
    private String frontendUrl;

    private static final String[] CSRF_IGNORADOS = {
            "/api/health",
            "/health",
            "/api/public/**",
            "/api/admin",
            "/api/admin/**",
            "/api/auth/recuperar-senha",
            "/api/auth/redefinir-senha",
            "/api/usuarios/convites/aceitar",
            "/api/usuarios/convites/recusar",
            "/api/meu-gendaz/auth/solicitar-codigo",
            "/api/meu-gendaz/auth/validar-codigo",
            "/api/pagamentos/webhook/stripe"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GendazSessionAuthenticationFilter gendazSessionAuthenticationFilter,
            MeuGendazSessionAuthenticationFilter meuGendazSessionAuthenticationFilter,
            AdminImpersonationGuardFilter adminImpersonationGuardFilter,
            AdminAuthenticationFilter adminAuthenticationFilter
    ) throws Exception {

        // Corrigido: HttpOnly=true para evitar roubo do cookie CSRF via XSS.
        CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .sameSite("None")
                .secure(true));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(CSRF_IGNORADOS))
                .addFilterAfter(new com.minhaempresa.gendaz.shared.security.CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterBefore(adminAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gendazSessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminImpersonationGuardFilter, GendazSessionAuthenticationFilter.class)
                .addFilterBefore(meuGendazSessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/health",
                                "/api/health",
                                "/api/auth/csrf",
                                "/api/usuarios/convites/publico"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/criar-conta",
                                "/api/auth/recuperar-senha",
                                "/api/auth/redefinir-senha",
                                "/api/auth/logout",
                                "/api/usuarios/convites/aceitar",
                                "/api/usuarios/convites/recusar",
                                "/api/meu-gendaz/auth/solicitar-codigo",
                                "/api/meu-gendaz/auth/validar-codigo",
                                "/api/meu-gendaz/auth/logout"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/meu-gendaz/empresa/*", "/api/meu-gendaz/perfil").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/meu-gendaz/perfil").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/pagamentos/planos/webhook", "/api/pagamentos/webhook/stripe").permitAll()
                        .requestMatchers("/api/admin/auth/login", "/api/admin/access").permitAll()
                        .requestMatchers("/api/admin", "/api/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/admin", "/admin/**").permitAll()
                        .anyRequest().authenticated())
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

