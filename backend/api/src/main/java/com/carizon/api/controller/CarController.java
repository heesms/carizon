package com.carizon.api.controller;

import com.carizon.api.dto.ApiResponse;
import com.carizon.core.dto.CarDetailDto;
import com.carizon.core.dto.CarSummaryDto;
import com.carizon.core.service.CarService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/cars")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @GetMapping
    public ApiResponse<Page<CarSummaryDto>> searchCars(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer priceMin,
            @RequestParam(required = false) Integer priceMax,
            @RequestParam(required = false) Integer kmMax,
            @RequestParam(required = false) Integer yymmFrom,
            @RequestParam(required = false) Integer yymmTo,
            @RequestParam(required = false) String[] fuel,
            @RequestParam(required = false) String[] transmission,
            @RequestParam(required = false) String[] body,
            @RequestParam(required = false) String[] region,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<CarSummaryDto> result = carService.searchCars(q, new CarService.CarSearchFilters(
            priceMin, priceMax, kmMax, yymmFrom, yymmTo, fuel, transmission, body, region, sort
        ), pageable);
        return ApiResponse.of(result, Map.of("page", page, "size", size, "total", result.getTotalElements()));
    }

    @GetMapping("/{id}")
    public ApiResponse<CarDetailDto> getCarDetail(@PathVariable Long id) {
        return carService.getCarDetail(id)
            .map(ApiResponse::of)
            .orElseGet(() -> ApiResponse.of(null, Map.of("error", "NOT_FOUND")));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<CarDetailDto> getCarHistory(@PathVariable Long id) {
        return ApiResponse.of(carService.getCarHistory(id));
    }
}
