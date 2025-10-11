package com.carizon.core.domain.web;

import com.carizon.core.domain.service.MyCarQueryService;
import com.carizon.core.domain.web.dto.CarDetailDto;
import com.carizon.core.domain.web.dto.CarListItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/my-cars")
public class CarController {

    private final MyCarQueryService service;

    @GetMapping
    public Page<CarListItemDto> search(
            @RequestParam(required = false) String maker,
            @RequestParam(required = false, name="modelGroup") String modelGroup,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String trim,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false, name="q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.search(maker, modelGroup, model, trim, grade, query, page, size, sort);
    }

    @GetMapping("/{myCarKey}")
    public CarDetailDto detail(@PathVariable String myCarKey) {
        return service.detail(myCarKey);
    }
}
