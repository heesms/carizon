package com.carizon.mapping;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class MappingController {

    private final CodeMappingService mapping;
    private final MasterMergeService merge;

    /** 오늘자 증분 기준 */
    @PostMapping("/mapping/hier/{platform}/today")
    public String autoToday(@PathVariable String platform) {
        int n = mapping.runAutoMapping(platform, CodeMappingService.Scope.TODAY);
        return "hier-mapped (TODAY): " + n;
    }

    /** 전체 기준 */
    @PostMapping("/mapping/hier/{platform}/full")
    public String autoFull(@PathVariable String platform) {
        int n = mapping.runAutoMapping(platform, CodeMappingService.Scope.FULL);
        return "hier-mapped (FULL): " + n;
    }

    @PostMapping("/mapping/hier/merge")
    public String autoFull() {
        int n =     merge.updateCarMasterFromMapping();
        return "MERGED (FULL): " + n;
    }



}
