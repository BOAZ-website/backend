package com.boaz.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpleApiController {

    // GET 요청을 처리하는 엔드포인트 (경로: /api/hello)
    @GetMapping("/api/hello")
    public ApiResponse getHello(@RequestParam(defaultValue = "User") String name) {
        String message = name + "님, API 호출이 성공적으로 완료되었습니다.";
        return new ApiResponse(200, message);
    }

    // JSON 응답 형식으로 변환될 DTO 레코드
    public record ApiResponse(int status, String message) {}
}