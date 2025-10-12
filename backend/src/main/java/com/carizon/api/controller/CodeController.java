package com.carizon.api.controller;

import com.carizon.api.dto.CodeItemDto;
import com.carizon.api.dto.ModelImageDto;
import com.carizon.api.entity.CzModelImage;
import com.carizon.api.service.CarCodeService;
import com.carizon.api.service.CzModelImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Car Codes", description = "Car code selection APIs (maker, model group, model, trim, grade)")
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeController {

    private final CarCodeService carCodeService;
    private final CzModelImageService czModelImageService;

    @Operation(summary = "Get makers", description = "Get all car makers")
    @GetMapping("/makers")
    public List<CodeItemDto> getMakers() {
        return carCodeService.getMakers();
    }

    @Operation(summary = "Get model groups", description = "Get model groups, optionally filtered by maker")
    @GetMapping("/model-groups")
    public List<CodeItemDto> getModelGroups(
            @Parameter(description = "Maker code filter") @RequestParam(required = false) String maker
    ) {
        return carCodeService.getModelGroups(maker);
    }

    @Operation(summary = "Get models", description = "Get models, optionally filtered by maker and model group")
    @GetMapping("/models")
    public List<CodeItemDto> getModels(
            @Parameter(description = "Maker code filter") @RequestParam(required = false) String maker,
            @Parameter(description = "Model group code filter") @RequestParam(required = false) String modelGroup
    ) {
        return carCodeService.getModels(maker, modelGroup);
    }

    @Operation(summary = "Get trims", description = "Get trims, optionally filtered by maker, model group, and model")
    @GetMapping("/trims")
    public List<CodeItemDto> getTrims(
            @Parameter(description = "Maker code filter") @RequestParam(required = false) String maker,
            @Parameter(description = "Model group code filter") @RequestParam(required = false) String modelGroup,
            @Parameter(description = "Model code filter") @RequestParam(required = false) String model
    ) {
        return carCodeService.getTrims(maker, modelGroup, model);
    }

    @Operation(summary = "Get grades", description = "Get grades, optionally filtered by maker, model group, model, and trim")
    @GetMapping("/grades")
    public List<CodeItemDto> getGrades(
            @Parameter(description = "Maker code filter") @RequestParam(required = false) String maker,
            @Parameter(description = "Model group code filter") @RequestParam(required = false) String modelGroup,
            @Parameter(description = "Model code filter") @RequestParam(required = false) String model,
            @Parameter(description = "Trim code filter") @RequestParam(required = false) String trim
    ) {
        return carCodeService.getGrades(maker, modelGroup, model, trim);
    }

    @Operation(summary = "Get representative model image", description = "Get representative image URL for a model code")
    @GetMapping("/model-image")
    public Map<String, String> getModelImage(
            @Parameter(description = "Model code", required = true) @RequestParam String modelCode
    ) {
        String imageUrl = czModelImageService.getRepresentativeImageUrl(modelCode);
        Map<String, String> result = new HashMap<>();
        result.put("modelCode", modelCode);
        result.put("imageUrl", imageUrl);
        return result;
    }

    @Operation(summary = "Get all model images", description = "Get all images for a model code")
    @GetMapping("/model-images")
    public List<ModelImageDto> getModelImages(
            @Parameter(description = "Model code", required = true) @RequestParam String modelCode
    ) {
        List<CzModelImage> images = czModelImageService.getAllImagesForModel(modelCode);
        return images.stream()
                .map(img -> new ModelImageDto(
                        img.getId(),
                        img.getModelCode(),
                        img.getImageUrl(),
                        img.getSortOrder(),
                        img.getIsMain()
                ))
                .toList();
    }
}
