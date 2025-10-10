package com.carizon.integration.controller;

import com.carizon.integration.service.ModelImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model-images")
public class ModelImageController {

    private final ModelImageService imageService;

    /**
     * 모델코드별 대표 car_seq를 뽑아 1/2번 이미지를 다운로드합니다.
     * 기본 저장 경로: C:\carizon\model_image
     *
     * 예) POST /api/model-images/download?limit=200
     *    POST /api/model-images/download?dir=D:\imgs&limit=100
     */
    @PostMapping("/download")
    public ResponseEntity<?> download(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "dir", required = false) String dir
    ) throws Exception {
        Path target = (dir == null || dir.isBlank())
                ? Paths.get("C:\\carizon\\model_image")
                : Paths.get(dir);

        imageService.downloadRepresentativeImages(target, limit);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "OK");
        resp.put("targetDir", target.toAbsolutePath().toString());
        resp.put("limit", limit);
        return ResponseEntity.ok(resp);
    }

    /** 간단 헬스체크 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true);
    }
}
