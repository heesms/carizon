package com.carizon.rag.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.service.CarImageExtractorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 이미지 추출 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Tag(name = "이미지 추출", description = "플랫폼 URL에서 차량 이미지 추출 API")
public class ImageExtractionController {
    
    private final CarImageExtractorService imageExtractorService;
    
    @GetMapping("/extract")
    @Operation(summary = "이미지 추출", description = "플랫폼 URL에서 차량 이미지를 추출합니다")
    public ResponseEntity<ApiResponse<String>> extractImage(@RequestParam String url) {
        try {
            String imageUrl = imageExtractorService.extractImageFromUrl(url);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success(imageUrl));
            } else {
                return ResponseEntity.ok(ApiResponse.success(null));
            }
        } catch (Exception e) {
            log.warn("Failed to extract image from URL: {}", url, e);
            return ResponseEntity.ok(ApiResponse.success(null));
        }
    }
}
