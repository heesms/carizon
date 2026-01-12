package com.carizon.controller;
import com.carizon.service.ModelQueryService;
import com.carizon.dto.ModelImageDto;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/models")
public class ModelController {
  private final ModelQueryService service;
  public ModelController(ModelQueryService service){ this.service = service; }
  @GetMapping("/{modelCode}/images")
  public List<ModelImageDto> images(@PathVariable String modelCode){
    return service.images(modelCode);
  }
}