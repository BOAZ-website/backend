package com.boaz.backend.domain.auth.handler;

import com.boaz.backend.domain.auth.entity.RefreshToken;
import com.boaz.backend.domain.auth.oauth2.CustomOAuth2User;
import com.boaz.backend.domain.auth.repository.RefreshTokenRepository;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.AccountType;
import com.boaz.backend.global.security.JwtProvider;
import com.boaz.backend.global.util.CookieProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CookieProvider cookieProvider;

    @Value("${oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        CustomOAuth2User customUser = (CustomOAuth2User) authentication.getPrincipal();
        User user = customUser.getUser();

        String accessToken = jwtProvider.generateUserAccessToken(user.getId());
        String refreshToken = jwtProvider.generateUserRefreshToken(user.getId());

        refreshTokenRepository.findByAccountTypeAndAccountId(AccountType.USER, user.getId())
                .ifPresentOrElse(
                        rt -> rt.update(refreshToken, jwtProvider.getRefreshTokenExpiry()),
                        () -> refreshTokenRepository.save(RefreshToken.builder()
                                .accountType(AccountType.USER)
                                .accountId(user.getId())
                                .token(refreshToken)
                                .expiresAt(jwtProvider.getRefreshTokenExpiry())
                                .build())
                );

        cookieProvider.addUserRefreshTokenCookie(response, refreshToken);

        getRedirectStrategy().sendRedirect(request, response, redirectUri + "#token=" + accessToken);
    }
}
