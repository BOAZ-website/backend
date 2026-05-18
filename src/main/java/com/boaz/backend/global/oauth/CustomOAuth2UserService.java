package com.boaz.backend.global.oauth;

import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = createUserInfo(provider, oauth2User.getAttributes());
        User user = userService.findOrCreate(userInfo);

        return new OAuth2UserAdapter(oauth2User, user);
    }

    private OAuth2UserInfo createUserInfo(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "kakao" -> new KakaoOAuth2UserInfo(attributes);
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "naver" -> new NaverOAuth2UserInfo(attributes);
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }
}
