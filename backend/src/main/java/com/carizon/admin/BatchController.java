package com.carizon.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/batch")
public class BatchController {
    private final BatchService batchService;

    // 전체 배치: merge(플랫폼->platform_car, master 생성) + snapshot(가격이력) + sold 처리
    @PostMapping("/run")
    public ResponseEntity<?> runFullBatch(
            @RequestParam(value = "bizDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        var result = batchService.runFullBatch(d);
        return ResponseEntity.ok(result);
    }

    // 개별 단계 호출도 가능
    @PostMapping("/merge")
    public ResponseEntity<?> runMerge(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int merged = batchService.mergeAllPlatforms(d);
        return ResponseEntity.ok(Map.of("mergedPlatforms", merged));
    }

    @PostMapping("/snapshot")
    public ResponseEntity<?> runSnapshot(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        batchService.snapshotPrices(d);
        batchService.closeMissingAds(d);
        return ResponseEntity.ok(Map.of("status","OK"));
    }
}
