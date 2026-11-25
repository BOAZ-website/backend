package com.boaz.backend.global.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private MockMvc mockMvc;

    @RestController
    static class TestController {

        @GetMapping("/test/get-only")
        String getOnly() {
            return "ok";
        }

        @PostMapping(value = "/test/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnly(@RequestBody Payload payload) {
            return "ok";
        }

        @GetMapping("/test/missing")
        String missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, ".env.local");
        }

        @GetMapping("/test/items/{id}")
        String item(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }

        record Payload(String email, LocalDate birthDate) {
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    private String allLogText() {
        StringBuilder sb = new StringBuilder();
        appender.list.forEach(e -> {
            sb.append(e.getFormattedMessage());
            if (e.getThrowableProxy() != null) {
                sb.append(e.getThrowableProxy().getMessage());
            }
        });
        return sb.toString();
    }

    @Test
    @DisplayName("없는 경로(NoResourceFoundException)는 404·WARN")
    void not_found_is_404_warn() throws Exception {
        mockMvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage()).doesNotContain(".env.local");
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405·WARN, Allow 헤더 유지")
    void method_not_allowed_is_405_warn() throws Exception {
        mockMvc.perform(post("/test/get-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415·WARN")
    void unsupported_media_type_is_415_warn() throws Exception {
        mockMvc.perform(post("/test/json-only").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("그 외 예외는 500·ERROR와 스택 트레이스")
    void unhandled_is_500_error() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    @DisplayName("형식이 틀린 본문은 로그에 입력값 대신 필드 이름만 남긴다(응답 메시지는 그대로)")
    void invalid_format_logs_field_name_only() throws Exception {
        mockMvc.perform(post("/test/json-only").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hong@example.com\",\"birthDate\":\"secret-1999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'birthDate' 필드의 값이 유효하지 않습니다. (입력값: secret-1999)"));

        assertThat(onlyEvent().getFormattedMessage()).contains("field=birthDate");
        assertThat(allLogText()).doesNotContain("secret-1999").doesNotContain("hong@example.com");
    }

    @Test
    @DisplayName("타입이 틀린 파라미터는 로그에 입력값 대신 파라미터 이름만 남긴다")
    void type_mismatch_logs_parameter_name_only() throws Exception {
        mockMvc.perform(get("/test/items/secret-value"))
                .andExpect(status().isBadRequest());

        ILoggingEvent event = onlyEvent();
        assertThat(event.getFormattedMessage()).contains("parameter=id");
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(allLogText()).doesNotContain("secret-value");
    }
}
