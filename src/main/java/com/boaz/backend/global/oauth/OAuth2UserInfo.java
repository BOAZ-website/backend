package com.boaz.backend.global.oauth;

public interface OAuth2UserInfo {
    String getProvider();
    String getProviderId();
    String getNickname();
}
