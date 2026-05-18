package com.boaz.backend.domain.auth.oauth2;

import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = extractProviderId(provider, oauth2User);
        String nickname = extractNickname(provider, oauth2User);

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(provider)
                                .providerId(providerId)
                                .nickname(nickname)
                                .memberType(MemberType.OUTSIDER)
                                .build()
                ));

        return new CustomOAuth2User(oauth2User, user);
    }

    private String extractProviderId(String provider, OAuth2User oauth2User) {
        return switch (provider) {
            case "kakao" -> String.valueOf(oauth2User.getAttribute("id"));
            case "google" -> oauth2User.getAttribute("sub");
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }

    @SuppressWarnings("unchecked")
    private String extractNickname(String provider, OAuth2User oauth2User) {
        return switch (provider) {
            case "kakao" -> {
                Map<String, Object> kakaoAccount = oauth2User.getAttribute("kakao_account");
                if (kakaoAccount != null) {
                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                    if (profile != null) yield (String) profile.get("nickname");
                }
                yield null;
            }
            case "google" -> oauth2User.getAttribute("name");
            default -> null;
        };
    }
}
