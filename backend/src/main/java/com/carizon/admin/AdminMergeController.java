package com.carizon.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMergeController {

    private final MergeService mergeService;
    @PostMapping("/merge/encar")
    public ResponseEntity<?> mergeEncar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();

        int upserts = mergeService.mergeFromEncar_NoBinding(d);
        return ResponseEntity.ok(Map.of("status","OK","encar_upserts",upserts));
    }

    @PostMapping("/merge/chacha")
    public ResponseEntity<?> mergeChacha(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int upserts = mergeService.mergeFromChacha_NoBinding(d);
        return ResponseEntity.ok(Map.of("status","OK","chacha_upserts",upserts));
    }

    @PostMapping("/merge/chutcha")
    public ResponseEntity<?> mergeChutcha(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int upserts = mergeService.mergeFromChutcha_NoBinding(d);
        return ResponseEntity.ok(Map.of("status","OK","chutcha_upserts",upserts));
    }

    @PostMapping("/merge/all")
    public ResponseEntity<?> mergeAll(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate d = bizDate != null ? bizDate : LocalDate.now();
        int e = mergeService.mergeFromEncar_NoBinding(d);
        int c1 = mergeService.mergeFromChacha_NoBinding(d);
        int c2 = mergeService.mergeFromChutcha_NoBinding(d);
        int k = mergeService.mergeFromKcarLinked_NoBinding(d); // KCar는 매핑된 것만
        return ResponseEntity.ok(Map.of("status","OK","encar",e,"chacha",c1,"chutcha",c2,"kcar_linked",k));
    }

}
