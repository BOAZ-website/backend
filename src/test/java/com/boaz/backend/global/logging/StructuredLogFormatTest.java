package com.boaz.backend.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * prod 파일 로그 형식({@code logging.structured.format.file: ecs})으로 찍었을 때 한 건이 한 줄 JSON이고,
 * CloudWatch 지표 필터·Logs Insights가 읽을 필드가 들어가는지 확인한다.
 */
class StructuredLogFormatTest {

    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final Logger otherLogger = (Logger) LoggerFactory.getLogger("com.boaz.backend.test.Other");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private final ObjectMapper objectMapper = new ObjectMapper();
    private StructuredLogEncoder encoder;

    @BeforeEach
    void setUp() {
        context.putObject(Environment.class.getName(), new MockEnvironment());
        encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        appender.start();
        for (Logger logger : new Logger[]{filterLogger, otherLogger}) {
            logger.setLevel(Level.INFO);
            logger.addAppender(appender);
        }
    }

    @AfterEach
    void tearDown() {
        for (Logger logger : new Logger[]{filterLogger, otherLogger}) {
            logger.detachAppender(appender);
            logger.setLevel(null);
        }
        encoder.stop();
        MDC.clear();
    }

    private String encode(ILoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("요청 로그는 한 줄 JSON이고 요청 ID와 요청 필드가 최상위 필드로 들어간다")
    void request_log_fields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/faqs");
        request.addHeader("X-Amz-Cf-Id", "abcDEF123_-==");
        request.addHeader("CloudFront-Viewer-Address", "203.0.113.10:443");

        new RequestLoggingFilter(1000).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/faqs");
            req.setAttribute(RequestLoggingFilter.USER_ATTRIBUTE, "USER:7");
        });

        String line = encode(appender.list.get(0));
        assertThat(line.strip()).doesNotContain("\n");
        JsonNode json = objectMapper.readTree(line);
        assertThat(json.get("log.level").asText()).isEqualTo("INFO");
        assertThat(json.get("requestId").asText()).isEqualTo("abcDEF123_-==");
        assertThat(json.get("method").asText()).isEqualTo("GET");
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/faqs");
        assertThat(json.get("status").asInt()).isEqualTo(200);
        assertThat(json.has("durationMs")).isTrue();
        assertThat(json.get("user").asText()).isEqualTo("USER:7");
        assertThat(json.get("clientIp").asText()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("오류 로그는 스택 트레이스까지 한 줄이고, 같은 요청의 요청 ID가 붙는다")
    void error_log_with_stack_trace_is_one_line() throws Exception {
        MDC.put(RequestLoggingFilter.MDC_REQUEST_ID, "srv-test");
        otherLogger.error("UnhandledException: ", new IllegalStateException("boom"));

        String line = encode(appender.list.get(0));
        assertThat(line.strip()).doesNotContain("\n");
        JsonNode json = objectMapper.readTree(line);
        assertThat(json.get("log.level").asText()).isEqualTo("ERROR");
        assertThat(json.get("requestId").asText()).isEqualTo("srv-test");
        assertThat(json.get("error.type").asText()).isEqualTo(IllegalStateException.class.getName());
        assertThat(json.get("error.stack_trace").asText()).contains("boom").contains("\n\tat ");
    }
}
