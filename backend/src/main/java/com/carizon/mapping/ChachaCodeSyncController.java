package com.carizon.mapping;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/chacha")
@RequiredArgsConstructor
public class ChachaCodeSyncController {

    private final ChachaCodeSyncService svc;

    /** 전체 메이커부터 전레벨 풀 동기화 */
    @PostMapping("/sync-all")
    public String syncAll(
            @RequestParam(value = "downloadImages", defaultValue = "true") boolean downloadImages,
            @RequestParam(value = "imageLimit", required = false) Integer imageLimit,
            @RequestParam(value = "imageDir", required = false) String imageDir,
            @RequestParam(value = "skipExistingImages", defaultValue = "true") boolean skipExistingImages
    ) {
        svc.syncAll(downloadImages, imageLimit, imageDir, skipExistingImages);
        return "CHACHACHA code sync (ALL) done" + (downloadImages ? " + image sync" : "");
    }

/** 특정 메이커만 전레벨 동기화 */
    /*

    @PostMapping("/sync-maker/{makerCode}")
    public String syncMaker(@PathVariable String makerCode) {
        svc.syncOneMaker(makerCode);
        return "CHACHACHA code sync (maker=" + makerCode + ") done";
    }
    */
}
