package com.minhaempresa.agendapro.shared;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;

public final class CookieHelper {
    private CookieHelper() {}

    public static Optional<String> lerCookie(HttpServletRequest request, String nome) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> nome.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
