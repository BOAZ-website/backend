package com.boaz.backend.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private static final String CF_ID = "SOX4xwn4XV6Q4rgb7XiVGOHms_BGlTAC4KyHmureZmBNrjGdRLiNIQ==";

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    /** MDC는 로그 이벤트를 읽을 때 가져오므로, 필터가 MDC를 지우기 전에 이벤트에 고정해 둔다. */
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Level originalLevel;

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter(1000);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        MDC.clear();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("130.176.0.1");
        return request;
    }

    /** 컨트롤러까지 간 것처럼 경로 템플릿을 넣고 상태 코드를 정하는 체인. */
    private static FilterChain controllerChain(String pattern, int status) {
        return (req, res) -> {
            if (pattern != null) {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
            }
            ((MockHttpServletResponse) res).setStatus(status);
        };
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> map = new HashMap<>();
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            pairs.forEach(p -> map.put(p.key, p.value));
        }
        return map;
    }

    @Nested
    @DisplayName("요청 ID")
    class RequestId {

        @Test
        @DisplayName("X-Amz-Cf-Id가 있으면 그 값을 MDC에 넣고, 요청이 끝나면 지운다")
        void uses_cloudfront_id() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/v1/faqs");
            request.addHeader("X-Amz-Cf-Id", CF_ID);
            AtomicReference<String> inChain = new AtomicReference<>();

            filter.doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> inChain.set(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)));

            assertThat(inChain.get()).isEqualTo(CF_ID);
            assertThat(onlyEvent().getMDCPropertyMap()).containsEntry(RequestLoggingFilter.MDC_REQUEST_ID, CF_ID);
            assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
        }

        @Test
        @DisplayName("헤더가 없거나 형식이 다르면 srv- 접두어를 붙인 ID를 만든다")
        void generates_server_id() throws Exception {
            MockHttpServletRequest noHeader = request("GET", "/api/v1/faqs");
            MockHttpServletRequest badHeader = request("GET", "/api/v1/faqs");
            badHeader.addHeader("X-Amz-Cf-Id", "bad id\n{\"injected\":1}");

            filter.doFilter(noHeader, new MockHttpServletResponse(), controllerChain(null, 200));
            filter.doFilter(badHeader, new MockHttpServletResponse(), controllerChain(null, 200));

            assertThat(appender.list).hasSize(2);
            assertThat(appender.list).allSatisfy(e ->
                    assertThat(e.getMDCPropertyMap().get(RequestLoggingFilter.MDC_REQUEST_ID)).startsWith("srv-"));
        }
    }

    @Nested
    @DisplayName("요청 로그 필드")
    class Fields {

        @Test
        @DisplayName("메서드·경로 템플릿·상태·처리 시간·사용자·클라이언트 IP를 남긴다")
        void logs_fields() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/v1/admin/applicants/42");
            request.addHeader("CloudFront-Viewer-Address", "2001:db8::1:443");
            FilterChain chain = (req, res) -> {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/admin/applicants/{id}");
                req.setAttribute(RequestLoggingFilter.USER_ATTRIBUTE, "ADMIN:10");
                ((MockHttpServletResponse) res).setStatus(200);
            };

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            ILoggingEvent event = onlyEvent();
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(keyValues(event))
                    .containsEntry("method", "GET")
                    .containsEntry("path", "/api/v1/admin/applicants/{id}")
                    .containsEntry("status", 200)
                    .containsEntry("user", "ADMIN:10")
                    .containsEntry("clientIp", "2001:db8::1")
                    .containsKey("durationMs");
        }

        @Test
        @DisplayName("실제 주소·쿼리 문자열·헤더 값은 남기지 않는다")
        void does_not_log_raw_uri_query_or_headers() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/v1/admin/applicants/42");
            request.setQueryString("email=hong@example.com&phone=01012345678");
            request.addParameter("email", "hong@example.com");
            request.addHeader("Authorization", "Bearer secret-token");
            request.addHeader("Cookie", "refreshToken=secret-cookie");

            filter.doFilter(request, new MockHttpServletResponse(),
                    controllerChain("/api/v1/admin/applicants/{id}", 200));

            ILoggingEvent event = onlyEvent();
            String everything = event.getFormattedMessage() + keyValues(event) + event.getMDCPropertyMap();
            assertThat(everything)
                    .doesNotContain("/applicants/42")
                    .doesNotContain("hong@example.com")
                    .doesNotContain("01012345678")
                    .doesNotContain("secret-token")
                    .doesNotContain("secret-cookie");
        }

        @Test
        @DisplayName("보안 필터에서 끝난 요청(401 등)은 경로 템플릿 없이 남긴다")
        void no_template_when_security_rejects() throws Exception {
            filter.doFilter(request("GET", "/api/v1/admin/accounts/me"), new MockHttpServletResponse(),
                    controllerChain(null, 401));

            Map<String, Object> kv = keyValues(onlyEvent());
            assertThat(kv).containsEntry("status", 401);
            assertThat(kv.get("path")).isNull();
            assertThat(kv.get("user")).isNull();
        }

        @Test
        @DisplayName("정적 리소스 처리기의 /** 는 경로 템플릿이 아닌 것으로 본다(탐색 봇 404)")
        void fallback_pattern_is_not_a_template() throws Exception {
            filter.doFilter(request("GET", "/.env.local"), new MockHttpServletResponse(),
                    controllerChain("/**", 404));

            Map<String, Object> kv = keyValues(onlyEvent());
            assertThat(kv).containsEntry("status", 404);
            assertThat(kv.get("path")).isNull();
        }

        @Test
        @DisplayName("체인에서 예외가 나오면 500으로 남기고 예외를 다시 던진다")
        void logs_500_when_chain_throws() {
            assertThatThrownBy(() -> filter.doFilter(request("POST", "/api/v1/x"), new MockHttpServletResponse(),
                    (req, res) -> {
                        throw new ServletException("boom");
                    }))
                    .isInstanceOf(ServletException.class);

            Map<String, Object> kv = keyValues(onlyEvent());
            assertThat(kv).containsEntry("status", 500).containsEntry("failed", true);
            assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
        }

        @Test
        @DisplayName("응답이 이미 확정된 뒤 예외가 나오면 실제 상태 코드를 남기고 failed로 구분한다")
        void keeps_committed_status_when_chain_throws() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThatThrownBy(() -> filter.doFilter(request("GET", "/api/v1/x"), response, (req, res) -> {
                ((MockHttpServletResponse) res).setStatus(200);
                res.flushBuffer();
                throw new IllegalStateException("after commit");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(response.isCommitted()).isTrue();
            assertThat(keyValues(onlyEvent())).containsEntry("status", 200).containsEntry("failed", true);
        }

        @Test
        @DisplayName("정상 요청에는 failed 필드가 없다")
        void no_failed_field_on_success() throws Exception {
            filter.doFilter(request("GET", "/api/v1/faqs"), new MockHttpServletResponse(),
                    controllerChain("/api/v1/faqs", 200));

            assertThat(keyValues(onlyEvent())).doesNotContainKey("failed");
        }
    }

    @Nested
    @DisplayName("ERROR dispatch")
    class ErrorDispatch {

        @Test
        @DisplayName("/error 재진입에서 원래 요청 ID를 MDC에 다시 넣고, 요청 로그는 다시 남기지 않는다")
        void restores_request_id_without_second_log() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/v1/x");
            request.addHeader("X-Amz-Cf-Id", CF_ID);
            filter.doFilter(request, new MockHttpServletResponse(), controllerChain(null, 500));
            assertThat(appender.list).hasSize(1);

            // 컨테이너가 같은 요청 객체로 /error에 다시 보내는 상황
            request.setDispatcherType(DispatcherType.ERROR);
            request.setRequestURI("/error");
            AtomicReference<String> inErrorDispatch = new AtomicReference<>();
            filter.doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> inErrorDispatch.set(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)));

            assertThat(inErrorDispatch.get()).isEqualTo(CF_ID);
            assertThat(appender.list).hasSize(1);
            assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
        }
    }

    @Nested
    @DisplayName("헬스체크")
    class HealthCheck {

        @Test
        @DisplayName("성공하고 빠른 헬스체크는 남기지 않는다")
        void skips_healthy() throws Exception {
            filter.doFilter(request("GET", "/actuator/health"), new MockHttpServletResponse(),
                    controllerChain(null, 200));

            assertThat(appender.list).isEmpty();
        }

        @Test
        @DisplayName("실패한 헬스체크는 남긴다")
        void logs_failed() throws Exception {
            filter.doFilter(request("GET", "/actuator/health"), new MockHttpServletResponse(),
                    controllerChain(null, 503));

            assertThat(keyValues(onlyEvent())).containsEntry("status", 503);
        }

        @Test
        @DisplayName("기준보다 느린 헬스체크는 남긴다")
        void logs_slow() throws Exception {
            RequestLoggingFilter strict = new RequestLoggingFilter(-1);

            strict.doFilter(request("GET", "/actuator/health"), new MockHttpServletResponse(),
                    controllerChain(null, 200));

            assertThat(keyValues(onlyEvent())).containsEntry("status", 200);
        }
    }
}
