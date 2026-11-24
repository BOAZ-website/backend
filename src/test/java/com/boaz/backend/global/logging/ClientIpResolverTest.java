package com.boaz.backend.global.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "203.0.113.10:46532, 203.0.113.10",
            "2001:db8::1:46532, 2001:db8::1",
            "2001:db8:85a3::8a2e:370:7334:443, 2001:db8:85a3::8a2e:370:7334",
            "'[2001:db8::1]:443', 2001:db8::1",
            "' 203.0.113.10:1 ', 203.0.113.10",
            "'::ffff:203.0.113.10:443', '::ffff:203.0.113.10'",
            "203.0.113.10:65535, 203.0.113.10"
    })
    @DisplayName("CloudFront-Viewer-Address에서 포트를 뗀다(IPv4·IPv6)")
    void strips_port(String header, String expected) {
        assertThat(ClientIpResolver.stripPort(header)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "203.0.113.10", "203.0.113.10:", "203.0.113.10:abc", ":443", "evil<script>:443", "1.2.3.4:999999",
            // IP 리터럴이 아님, 범위 밖 IPv4, 범위 밖 포트, 포트 없는 IPv6, 잘못된 IPv6
            "deadbeef:443", "999.999.999.999:443", "203.0.113.10:99999", "2001:db8::1", "2001:::1:443", "1.2.3:443"
    })
    @DisplayName("형식이 다르면 null")
    void invalid_format_returns_null(String header) {
        assertThat(ClientIpResolver.stripPort(header)).isNull();
    }

    @Test
    @DisplayName("헤더가 있으면 헤더 IP, getRemoteAddr(CloudFront 주소)는 쓰지 않는다")
    void resolve_uses_viewer_address() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("130.176.0.1");
        request.addHeader("CloudFront-Viewer-Address", "203.0.113.10:46532");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("헤더가 없거나 형식이 다르면 getRemoteAddr")
    void resolve_falls_back_to_remote_addr() {
        MockHttpServletRequest noHeader = new MockHttpServletRequest();
        noHeader.setRemoteAddr("127.0.0.1");
        MockHttpServletRequest badHeader = new MockHttpServletRequest();
        badHeader.setRemoteAddr("127.0.0.1");
        badHeader.addHeader("CloudFront-Viewer-Address", "garbage");

        assertThat(ClientIpResolver.resolve(noHeader)).isEqualTo("127.0.0.1");
        assertThat(ClientIpResolver.resolve(badHeader)).isEqualTo("127.0.0.1");
    }
}
