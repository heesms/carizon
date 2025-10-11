package com.carizon.api.controller;

import com.carizon.api.dto.CarDetailDto;
import com.carizon.api.dto.CarListItemDto;
import com.carizon.api.dto.PriceHistoryPointDto;
import com.carizon.api.service.CarDetailService;
import com.carizon.api.service.CarQueryService;
import com.carizon.api.service.PriceHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Cars", description = "Car listing and detail APIs")
@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
public class CarsController {

    private final CarQueryService carQueryService;
    private final CarDetailService carDetailService;
    private final PriceHistoryService priceHistoryService;

    @Operation(summary = "Search cars", description = "Search and filter cars with pagination")
    @GetMapping
    public Page<CarListItemDto> search(
            @Parameter(description = "Maker code filter") @RequestParam(required = false) String maker,
            @Parameter(description = "Model group code filter") @RequestParam(required = false) String modelGroup,
            @Parameter(description = "Model code filter") @RequestParam(required = false) String model,
            @Parameter(description = "Trim code filter") @RequestParam(required = false) String trim,
            @Parameter(description = "Grade code filter") @RequestParam(required = false) String grade,
            @Parameter(description = "Text search query") @RequestParam(required = false) String q,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field and direction (e.g., year,desc)") @RequestParam(required = false) String sort
    ) {
        return carQueryService.search(maker, modelGroup, model, trim, grade, q, page, size, sort);
    }

    @Operation(summary = "Get car detail", description = "Get detailed information for a specific car")
    @GetMapping("/{carId}")
    public CarDetailDto detail(@PathVariable Long carId) {
        return carDetailService.getDetail(carId);
    }

    @Operation(summary = "Get price history", description = "Get price history for a car across all or specific platform")
    @GetMapping("/{carId}/price-history")
    public List<PriceHistoryPointDto> priceHistory(
            @PathVariable Long carId,
            @Parameter(description = "Optional platform car ID to filter specific platform") 
            @RequestParam(required = false) Long platformCarId
    ) {
        return priceHistoryService.getHistory(carId, platformCarId);
    }
}
