package com.boaz.backend.global.logging;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 모든 요청을 한 줄씩 남기는 요청 로그 필터.
 *
 * <p>Spring Security 필터(순서 -100)보다 앞에서 돌아서 보안 필터가 끝낸 401·403과 {@code JwtAuthenticationFilter}의 500도
 * 남는다. {@code SecurityConfig}에 넣지 않고 {@code @Component}로 서블릿 필터에 직접 등록한다.
 *
 * <p>남기는 것: 요청 ID, 메서드, 경로 템플릿, 상태 코드, 처리 시간(ms), 사용자 식별자, 클라이언트 IP.
 * 남기지 않는 것: 실제 요청 주소와 쿼리 문자열, 요청 본문, 헤더(토큰·쿠키 포함).
 *
 * <p>요청 ID는 MDC({@value #MDC_REQUEST_ID})에 넣어 같은 요청에서 나온 모든 로그에 붙는다. 예외가 필터 밖으로 나가
 * 컨테이너가 {@code /error}로 다시 보내는 ERROR dispatch에서도 같은 ID를 MDC에 다시 넣는다. 요청 로그는 원래 요청에서
 * 한 번만 남긴다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";

    /** {@code JwtAuthenticationFilter}가 인증에 성공하면 {@code USER:{id}} 또는 {@code ADMIN:{id}}를 넣는 요청 속성. */
    public static final String USER_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".user";

    /** ERROR dispatch에서 같은 요청 ID를 다시 쓰려고 요청 속성에도 둔다. */
    static final String REQUEST_ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".requestId";

    static final String CLOUDFRONT_REQUEST_ID_HEADER = "X-Amz-Cf-Id";
    /** CloudFront를 거치지 않은 요청(EC2 안에서 호출, 로컬)에 서버가 만든 ID의 접두어. */
    static final String SERVER_REQUEST_ID_PREFIX = "srv-";
    static final String HEALTH_PATH = "/actuator/health";

    /** CloudFront 요청 ID는 base64url 문자 약 56자다. 다른 값이면 서버가 만든다. */
    private static final Pattern CLOUDFRONT_REQUEST_ID = Pattern.compile("[A-Za-z0-9_=\\-]{1,128}");
    /** 경로 템플릿이 없는 요청이 정적 리소스 처리기에 걸리면 이 값이 들어온다(탐색 봇의 404). 템플릿이 없는 것으로 본다. */
    private static final String FALLBACK_PATTERN = "/**";

    private final long healthSlowThresholdMs;

    public RequestLoggingFilter(
            @Value("${boaz.logging.health-slow-threshold-ms:1000}") long healthSlowThresholdMs
    ) {
        this.healthSlowThresholdMs = healthSlowThresholdMs;
    }

    /** ERROR dispatch에서도 돌아서 요청 ID를 MDC에 다시 넣는다(요청 로그는 남기지 않음). */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            doFilterErrorDispatch(request, response, filterChain);
            return;
        }
        long startNanos = System.nanoTime();
        String requestId = resolveRequestId(request);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        MDC.put(MDC_REQUEST_ID, requestId);
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            failed = true;
            throw e;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            // 예외가 밖으로 나왔는데 응답이 아직 확정되지 않았으면 컨테이너가 500을 보낸다.
            // 이미 확정(commit)됐으면 클라이언트는 그 상태 코드를 받았으므로 그대로 남기고, 실패는 failed 필드로 구분한다.
            int status = failed && !response.isCommitted()
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    : response.getStatus();
            try {
                logRequest(request, status, durationMs, failed);
            } finally {
                MDC.remove(MDC_REQUEST_ID);
            }
        }
    }

    private void doFilterErrorDispatch(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        MDC.put(MDC_REQUEST_ID, requestId instanceof String id ? id : resolveRequestId(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private void logRequest(HttpServletRequest request, int status, long durationMs, boolean failed) {
        if (!failed && isHealthyHealthCheck(request, status, durationMs)) {
            return;
        }
        String method = request.getMethod();
        String path = resolvePathTemplate(request);
        Object user = request.getAttribute(USER_ATTRIBUTE);
        LoggingEventBuilder event = log.atInfo()
                .addKeyValue("method", method)
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("user", user)
                .addKeyValue("clientIp", ClientIpResolver.resolve(request));
        if (failed) {
            // 처리 중 예외가 필터 밖으로 나온 요청. 응답이 이미 확정돼 status가 2xx여도 실패로 찾을 수 있게 한다.
            event = event.addKeyValue("failed", true);
        }
        event.log("{} {} {} {}ms", method, path != null ? path : "-", status, durationMs);
    }

    /** 성공한 헬스체크는 1분마다 같은 줄만 쌓이므로 남기지 않는다. 실패하거나 느리면 남긴다. */
    private boolean isHealthyHealthCheck(HttpServletRequest request, int status, long durationMs) {
        String uri = request.getRequestURI();
        boolean health = HEALTH_PATH.equals(uri) || uri.startsWith(HEALTH_PATH + "/");
        return health && status < 400 && durationMs <= healthSlowThresholdMs;
    }

    static String resolveRequestId(HttpServletRequest request) {
        String cloudFrontId = request.getHeader(CLOUDFRONT_REQUEST_ID_HEADER);
        if (StringUtils.hasText(cloudFrontId) && CLOUDFRONT_REQUEST_ID.matcher(cloudFrontId).matches()) {
            return cloudFrontId;
        }
        return SERVER_REQUEST_ID_PREFIX + UUID.randomUUID();
    }

    /** 컨트롤러까지 간 요청만 경로 템플릿이 있다. 보안 필터에서 끝난 요청과 없는 경로는 {@code null}. */
    static String resolvePathTemplate(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String p && !p.isEmpty() && !FALLBACK_PATTERN.equals(p)) {
            return p;
        }
        return null;
    }
}
