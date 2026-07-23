package com.minhaempresa.agendapro.shared;

import com.minhaempresa.agendapro.auth.interceptor.UsuarioSessionInterceptor;
import com.minhaempresa.agendapro.auth.interceptor.AdminIpWhitelistInterceptor;
import com.minhaempresa.agendapro.auth.interceptor.AdminTokenInterceptor;
import com.minhaempresa.agendapro.shared.security.RateLimitInterceptor;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class WebConfig implements WebMvcConfigurer {
    private final Environment environment;
    private final String frontendUrl;
    private final UsuarioSessionInterceptor usuarioSessionInterceptor;
    private final AdminIpWhitelistInterceptor adminIpWhitelistInterceptor;
    private final AdminTokenInterceptor adminTokenInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(
            Environment environment,
            @Value("${FRONTEND_URL:https://gendaz.site}") String frontendUrl,
            UsuarioSessionInterceptor usuarioSessionInterceptor,
            AdminIpWhitelistInterceptor adminIpWhitelistInterceptor,
            AdminTokenInterceptor adminTokenInterceptor,
            RateLimitInterceptor rateLimitInterceptor
    ) {
        this.environment = environment;
        this.frontendUrl = frontendUrl;
        this.usuarioSessionInterceptor = usuarioSessionInterceptor;
        this.adminIpWhitelistInterceptor = adminIpWhitelistInterceptor;
        this.adminTokenInterceptor = adminTokenInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        List<String> allowedOrigins = production
                ? Stream.concat(
                        Arrays.stream(frontendUrl.split(",")),
                        Stream.of(
                                "https://gendaz.site",
                                "https://www.gendaz.site"
                        )
                ).toList()
                : List.of(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:5174",
                        "http://127.0.0.1:5174"
                );

        Set<String> origens = new LinkedHashSet<>();
        for (String origem : allowedOrigins) {
            if (origem != null && !origem.isBlank()) {
                origens.add(origem.trim());
            }
        }

        registry.addMapping("/api/**")
                .allowedOrigins(origens.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "X-Usuario-Id", "X-Usuario-Perfil", "X-Session-Token")
                .exposedHeaders("Set-Cookie", "Retry-After")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**");
        registry.addInterceptor(usuarioSessionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/criar-conta",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/public/**",
                        "/api/health/**"
                );
        registry.addInterceptor(adminIpWhitelistInterceptor)
                .addPathPatterns("/admin", "/admin/**", "/api/admin", "/api/admin/**");
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
