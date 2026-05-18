package com.boaz.backend.global.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

public class CookieUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModules(SecurityJackson2Modules.getModules(CookieUtils.class.getClassLoader()));

    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> c.getName().equals(name))
                .findFirst();
    }

    public static String serialize(Object object) {
        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(object);
            return Base64.getUrlEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    public static <T> T deserialize(Cookie cookie, Class<T> cls) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cookie.getValue());
            return OBJECT_MAPPER.readValue(bytes, cls);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize cookie", e);
        }
    }
}
