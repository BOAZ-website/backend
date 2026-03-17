package com.boaz.backend.domain.faq.controller;

import com.boaz.backend.domain.faq.dto.FaqResponse;
import com.boaz.backend.domain.faq.service.FaqService;
import com.boaz.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/archiving")
@Tag(name = "FAQ", description = "FAQ API")
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/faqs")
    @Operation(summary = "FAQ 조회", description = "카테고리별 FAQ 목록을 order_num 오름차순으로 반환합니다.")
    public ResponseEntity<ApiResponse<List<FaqResponse>>> getFaqs(
            @RequestParam String category) {
        return ResponseEntity.ok(ApiResponse.ok(faqService.getFaqs(category)));
    }
}
