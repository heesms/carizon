package com.carizon.api.controller;

import com.carizon.api.dto.CodeRow;
import com.carizon.api.mapper.CodeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Code", description = "Code lookup APIs for makers, models, trims, and grades")
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeController {

    private final CodeMapper codeMapper;

    @Operation(summary = "Get all makers", description = "Retrieve all available car makers")
    @GetMapping("/makers")
    public List<CodeRow> getMakers() {
        return codeMapper.getMakers();
    }

    @Operation(summary = "Get model groups", description = "Retrieve model groups for a specific maker")
    @GetMapping("/model-groups")
    public List<CodeRow> getModelGroups(
            @Parameter(description = "Maker code") @RequestParam(required = false) String maker) {
        return codeMapper.getModelGroups(maker);
    }

    @Operation(summary = "Get models", description = "Retrieve models for a specific maker and model group")
    @GetMapping("/models")
    public List<CodeRow> getModels(
            @Parameter(description = "Maker code") @RequestParam(required = false) String maker,
            @Parameter(description = "Model group code") @RequestParam(required = false) String modelGroup) {
        return codeMapper.getModels(maker, modelGroup);
    }

    @Operation(summary = "Get trims", description = "Retrieve trims for a specific maker, model group, and model")
    @GetMapping("/trims")
    public List<CodeRow> getTrims(
            @Parameter(description = "Maker code") @RequestParam(required = false) String maker,
            @Parameter(description = "Model group code") @RequestParam(required = false) String modelGroup,
            @Parameter(description = "Model code") @RequestParam(required = false) String model) {
        return codeMapper.getTrims(maker, modelGroup, model);
    }

    @Operation(summary = "Get grades", description = "Retrieve grades for a specific maker, model group, model, and trim")
    @GetMapping("/grades")
    public List<CodeRow> getGrades(
            @Parameter(description = "Maker code") @RequestParam(required = false) String maker,
            @Parameter(description = "Model group code") @RequestParam(required = false) String modelGroup,
            @Parameter(description = "Model code") @RequestParam(required = false) String model,
            @Parameter(description = "Model trim code") @RequestParam(required = false) String trim) {
        return codeMapper.getGrades(maker, modelGroup, model, trim);
    }
}
