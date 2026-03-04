package com.carizon.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.dto.SeoModelDto;
import com.carizon.service.SeoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/seo")
@RequiredArgsConstructor
public class SeoDataController {

    private final SeoDataService seoDataService;

    /** 모델 SEO 데이터 (embed_text3 + 이미지 + 매물 통계) */
    @GetMapping("/model/{modelCode}")
    public ResponseEntity<ApiResponse<SeoModelDto>> getModelSeo(@PathVariable String modelCode) {
        return seoDataService.getModelSeo(modelCode)
                .map(dto -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic())
                        .<ApiResponse<SeoModelDto>>body(ApiResponse.success(dto)))
                .orElse(ResponseEntity.notFound().<ApiResponse<SeoModelDto>>build());
    }

    /** embed_text3 있는 모델 목록 (makerCode 필터 선택) */
    @GetMapping("/models")
    public ResponseEntity<ApiResponse<List<SeoModelDto>>> getModels(
            @RequestParam(required = false) String makerCode,
            @RequestParam(defaultValue = "200") int limit) {
        List<SeoModelDto> models = seoDataService.getModelsWithDescription(makerCode, limit);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic())
                .body(ApiResponse.success(models));
    }
}
