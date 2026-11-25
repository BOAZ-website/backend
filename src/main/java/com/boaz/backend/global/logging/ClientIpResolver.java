package com.boaz.backend.global.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * 요청 로그에 남길 클라이언트 IP를 정한다.
 *
 * <p>{@code getRemoteAddr()}는 CloudFront(시즌에는 ALB) 주소라 쓰지 않는다. {@code X-Forwarded-For}의 맨 왼쪽 값은
 * 클라이언트가 조작할 수 있고, 맨 오른쪽 값은 시즌 ALB 구조에서 CloudFront 주소가 된다. CloudFront가 넣어 주는
 * {@code CloudFront-Viewer-Address}(형식 {@code IP:포트})는 평시·시즌 모두 사용자 IP다. 이 헤더는 원본 요청 정책이
 * 전달하도록 설정돼 있어야 오고, 우리 CloudFront를 거친 요청일 때만 믿을 수 있다.
 */
public final class ClientIpResolver {

    static final String VIEWER_ADDRESS_HEADER = "CloudFront-Viewer-Address";

    private static final Pattern IPV4 = Pattern.compile(
            "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern PORT = Pattern.compile("\\d{1,5}");
    private static final int MAX_PORT = 65535;

    private ClientIpResolver() {
    }

    /** {@code CloudFront-Viewer-Address}에서 포트를 뗀 IP. 헤더가 없거나 형식이 다르면 {@code getRemoteAddr()}. */
    public static String resolve(HttpServletRequest request) {
        String ip = stripPort(request.getHeader(VIEWER_ADDRESS_HEADER));
        return ip != null ? ip : request.getRemoteAddr();
    }

    /**
     * {@code 203.0.113.10:46532} → {@code 203.0.113.10}, {@code 2001:db8::1:46532} → {@code 2001:db8::1}.
     * IPv6도 대괄호 없이 오므로 마지막 {@code :} 뒤를 포트로 본다. IP 리터럴이 아니거나 포트가 범위 밖이면 {@code null}.
     */
    static String stripPort(String viewerAddress) {
        if (!StringUtils.hasText(viewerAddress)) {
            return null;
        }
        String value = viewerAddress.trim();
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || !isPort(value.substring(colon + 1))) {
            return null;
        }
        String host = value.substring(0, colon);
        if (host.length() > 2 && host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return isIpLiteral(host) ? host : null;
    }

    private static boolean isPort(String port) {
        return PORT.matcher(port).matches() && Integer.parseInt(port) <= MAX_PORT;
    }

    /**
     * IPv4는 정규식으로, IPv6는 {@link InetAddress#getByName}으로 검사한다. {@code :}가 들어간 값은 IPv6 리터럴로만
     * 해석되고 이름 조회(DNS)를 하지 않는다. {@code :}가 없는 값은 IPv4 정규식만 통과시켜 DNS 조회로 가지 않게 한다.
     */
    static boolean isIpLiteral(String host) {
        if (IPV4.matcher(host).matches()) {
            return true;
        }
        if (host.indexOf(':') < 0 || !IPV6_CHARS.matcher(host).matches()) {
            return false;
        }
        try {
            InetAddress.getByName(host);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
