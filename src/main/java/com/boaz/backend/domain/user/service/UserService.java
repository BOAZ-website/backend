package com.boaz.backend.domain.user.service;

import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.oauth.OAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreate(OAuth2UserInfo userInfo) {
        return userRepository.findByProviderAndProviderId(userInfo.getProvider(), userInfo.getProviderId())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(userInfo.getProvider())
                                .providerId(userInfo.getProviderId())
                                .nickname(userInfo.getNickname())
                                .memberType(MemberType.OUTSIDER)
                                .build()
                ));
    }
}
