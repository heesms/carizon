package com.carizon.admin;

import com.carizon.merge.MergeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/merge")
public class MergeAdminController {
    private final MergeService mergeService;

    // 전체 배치: merge(플랫폼->platform_car, master 생성) + snapshot(가격이력) + sold 처리
    @PostMapping("/run")
    public ResponseEntity<?> runFullBatch(
            @RequestParam(value = "bizDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        var result = mergeService.runFullBatch(d);
        return ResponseEntity.ok(result);
    }

    // 개별 단계 호출도 가능
    @PostMapping("/all")
    public ResponseEntity<?> runMerge(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int merged = mergeService.mergeAllPlatforms(d);
        return ResponseEntity.ok(Map.of("mergedPlatforms", merged));
    }


    // 개별 단계 호출도 가능
    @PostMapping("/cha")
    public ResponseEntity<?> runMergeCha(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int merged = mergeService.mergeChachacha(d);
        return ResponseEntity.ok(Map.of("mergedPlatforms", merged));
    }

    // 개별 단계 호출도 가능
    @PostMapping("/kcar")
    public ResponseEntity<?> runMergeKcar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int merged = mergeService.mergeKcar(d);
        return ResponseEntity.ok(Map.of("mergedPlatforms", merged));
    }

    // 개별 단계 호출도 가능
    @PostMapping("/encar")
    public ResponseEntity<?> runMergeEncar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int merged = mergeService.mergeEncar(d);
        return ResponseEntity.ok(Map.of("mergedPlatforms", merged));
    }

    // 개별 단계 호출도 가능
    @PostMapping("/chutcha")
    public ResponseEntity<?> runMergeChutcha(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int merged = mergeService.mergeChutcha(d);
        return ResponseEntity.ok(Map.of("mergedPlatforms", merged));
    }

    @PostMapping("/snapshot")
    public ResponseEntity<?> runSnapshot(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        mergeService.snapshotPrices(d);
        mergeService.closeMissingAds(d);
        return ResponseEntity.ok(Map.of("status","OK"));
    }
}
