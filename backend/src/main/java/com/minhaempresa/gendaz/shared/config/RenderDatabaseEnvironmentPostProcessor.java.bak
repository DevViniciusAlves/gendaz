package com.minhaempresa.gendaz.shared.config;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class RenderDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "render-database-normalizer";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new LinkedHashMap<>();

        String urlSource = datasourceUrlSource(environment);
        String rawUrl = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("JDBC_DATABASE_URL"),
                environment.getProperty("DATABASE_URL")
        );

        if (!StringUtils.hasText(rawUrl)) {
            urlSource = "PG* variables";
            rawUrl = buildJdbcUrlFromPgVars(environment);
        }

        String normalizedUrl = normalizeJdbcUrl(rawUrl);
        logDatasourceTarget(urlSource, normalizedUrl);
        if (StringUtils.hasText(normalizedUrl) && isBlank(environment, "spring.datasource.url")) {
            overrides.put("spring.datasource.url", normalizedUrl);
        }

        if (StringUtils.hasText(normalizedUrl)) {
            if (isBlank(environment, "spring.datasource.driver-class-name")) {
                overrides.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
            }
            if (isBlank(environment, "spring.jpa.database-platform")) {
                overrides.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
            }
        }

        String username = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_USERNAME"),
                environment.getProperty("JDBC_DATABASE_USERNAME"),
                environment.getProperty("DATABASE_USERNAME"),
                environment.getProperty("PGUSER")
        );
        String password = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_PASSWORD"),
                environment.getProperty("JDBC_DATABASE_PASSWORD"),
                environment.getProperty("DATABASE_PASSWORD"),
                environment.getProperty("PGPASSWORD")
        );

        if (StringUtils.hasText(username) && isBlank(environment, "spring.datasource.username")) {
            overrides.put("spring.datasource.username", username);
        }
        if (StringUtils.hasText(password) && isBlank(environment, "spring.datasource.password")) {
            overrides.put("spring.datasource.password", password);
        }

        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isBlank(ConfigurableEnvironment environment, String key) {
        return !StringUtils.hasText(environment.getProperty(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String buildJdbcUrlFromPgVars(ConfigurableEnvironment environment) {
        String host = firstNonBlank(environment.getProperty("PGHOST"), environment.getProperty("POSTGRES_HOST"));
        String port = firstNonBlank(environment.getProperty("PGPORT"), environment.getProperty("POSTGRES_PORT"));
        String database = firstNonBlank(environment.getProperty("PGDATABASE"), environment.getProperty("POSTGRES_DB"));
        if (!StringUtils.hasText(host) || !StringUtils.hasText(database)) {
            return "";
        }
        String safePort = StringUtils.hasText(port) ? port : "5432";
        String query = firstNonBlank(environment.getProperty("PGSSLMODE"), environment.getProperty("SPRING_DATASOURCE_QUERY"));
        StringBuilder builder = new StringBuilder("jdbc:postgresql://")
                .append(host)
                .append(":")
                .append(safePort)
                .append("/")
                .append(database);
        if (StringUtils.hasText(query)) {
            if (query.contains("=")) {
                builder.append("?").append(query);
            } else {
                builder.append("?sslmode=").append(query.toLowerCase(Locale.ROOT));
            }
        } else {
            builder.append("?sslmode=require");
        }
        return builder.toString();
    }

    private String datasourceUrlSource(ConfigurableEnvironment environment) {
        if (StringUtils.hasText(environment.getProperty("SPRING_DATASOURCE_URL"))) {
            return "SPRING_DATASOURCE_URL";
        }
        if (StringUtils.hasText(environment.getProperty("JDBC_DATABASE_URL"))) {
            return "JDBC_DATABASE_URL";
        }
        if (StringUtils.hasText(environment.getProperty("DATABASE_URL"))) {
            return "DATABASE_URL";
        }
        return "none";
    }

    private void logDatasourceTarget(String source, String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            System.out.println("[database-config] datasource nao configurado por variavel de ambiente.");
            return;
        }
        try {
            String uriValue = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring("jdbc:".length()) : jdbcUrl;
            URI uri = URI.create(uriValue);
            String database = StringUtils.hasText(uri.getPath()) ? uri.getPath().replaceFirst("^/", "") : "";
            String query = uri.getQuery();
            String sslmode = "not-set";
            boolean userInUrl = false;
            boolean passwordInUrl = false;
            if (StringUtils.hasText(query)) {
                for (String param : query.split("&")) {
                    String lowerParam = param.toLowerCase(Locale.ROOT);
                    if (lowerParam.startsWith("sslmode=")) {
                        sslmode = param.substring("sslmode=".length());
                    }
                    if (lowerParam.startsWith("user=")) {
                        userInUrl = true;
                    }
                    if (lowerParam.startsWith("password=")) {
                        passwordInUrl = true;
                    }
                }
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            System.out.printf(
                    "[database-config] source=%s host=%s port=%d database=%s sslmode=%s userInUrl=%s passwordInUrl=%s preferIPv4Stack=%s preferIPv6Addresses=%s%n",
                    source,
                    uri.getHost(),
                    port,
                    database,
                    sslmode,
                    userInUrl,
                    passwordInUrl,
                    System.getProperty("java.net.preferIPv4Stack"),
                    System.getProperty("java.net.preferIPv6Addresses")
            );
        } catch (RuntimeException ex) {
            System.out.printf("[database-config] falha ao sanitizar datasource source=%s error=%s%n", source, ex.getClass().getSimpleName());
        }
    }

    private String normalizeJdbcUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "";
        }
        String value = rawUrl.trim();
        if (value.startsWith("jdbc:postgresql://")) {
            return value;
        }
        if (value.startsWith("postgres://") || value.startsWith("postgresql://")) {
            try {
                URI uri = URI.create(value);
                StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                        .append(uri.getHost());
                if (uri.getPort() > 0) {
                    jdbc.append(":").append(uri.getPort());
                }
                String path = uri.getPath();
                if (StringUtils.hasText(path)) {
                    jdbc.append(path);
                }
                String query = uri.getQuery();
                if (StringUtils.hasText(query)) {
                    jdbc.append("?").append(query);
                } else {
                    jdbc.append("?sslmode=require");
                }
                return jdbc.toString();
            } catch (IllegalArgumentException ex) {
                return "";
            }
        }
        return value;
    }
}

