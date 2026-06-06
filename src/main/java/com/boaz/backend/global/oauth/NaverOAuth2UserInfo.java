package com.boaz.backend.global.oauth;

import java.util.Map;

public class NaverOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProvider() {
        return "naver";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getProviderId() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) return null;
        return (String) response.get("id");
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getNickname() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) return null;
        String name = (String) response.get("name");
        if (name != null && !name.isBlank()) return name;
        return (String) response.get("nickname");
    }
}
