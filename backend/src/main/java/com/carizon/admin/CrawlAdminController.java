// com.carizon.admin.CrawlAdminController.java
package com.carizon.admin;

import com.carizon.batch.CrawlJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/crawl")
@RequiredArgsConstructor
public class CrawlAdminController {
    private final CrawlJobService job;

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> runNow(){
        job.runNow();
        return Map.of("ok", true, "message", "crawl started");
    }
}
