package com.carizon.api.controller;

import com.carizon.api.dto.CarMasterRow;
import com.carizon.api.mapper.CarMasterMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Master", description = "Car master data APIs")
@RestController
@RequestMapping("/api/master")
@RequiredArgsConstructor
public class MasterController {

    private final CarMasterMapper carMasterMapper;

    @Operation(summary = "Search car master", description = "Search car master records with filters and pagination")
    @GetMapping
    public Page<CarMasterRow> search(
            @Parameter(description = "Maker code filter") @RequestParam(required = false) String maker,
            @Parameter(description = "Model group code filter") @RequestParam(required = false) String modelGroup,
            @Parameter(description = "Model code filter") @RequestParam(required = false) String model,
            @Parameter(description = "Trim code filter") @RequestParam(required = false) String trim,
            @Parameter(description = "Grade code filter") @RequestParam(required = false) String grade,
            @Parameter(description = "Year filter") @RequestParam(required = false) Short year,
            @Parameter(description = "Region filter") @RequestParam(required = false) String region,
            @Parameter(description = "Status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    ) {
        int offset = page * size;
        
        List<CarMasterRow> masters = carMasterMapper.searchMaster(
                maker, modelGroup, model, trim, grade, year, region, status, offset, size);
        int total = carMasterMapper.countMaster(
                maker, modelGroup, model, trim, grade, year, region, status);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return new PageImpl<>(masters, pageable, total);
    }

    @Operation(summary = "Get car master by ID", description = "Get a specific car master record by ID")
    @GetMapping("/{carId}")
    public CarMasterRow detail(@PathVariable Long carId) {
        CarMasterRow master = carMasterMapper.getMasterById(carId);
        if (master == null) {
            throw new RuntimeException("Car master not found: " + carId);
        }
        return master;
    }
}
