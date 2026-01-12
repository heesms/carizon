package com.carizon.controller;
import com.carizon.service.CodeQueryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/codes")
public class CodeController {
  private final CodeQueryService service;
  public CodeController(CodeQueryService service){ this.service = service; }
  @GetMapping("/makers")
  public List<Map<String,Object>> makers(){ return service.makers(); }
  @GetMapping("/model-groups")
  public List<Map<String,Object>> modelGroups(@RequestParam String makerCode){ return service.modelGroups(makerCode); }
  @GetMapping("/models")
  public List<Map<String,Object>> models(@RequestParam String makerCode, @RequestParam String modelGroupCode){
    return service.models(makerCode, modelGroupCode);
  }
  @GetMapping("/trims")
  public List<Map<String,Object>> trims(@RequestParam String makerCode, @RequestParam String modelGroupCode, @RequestParam String modelCode){
    return service.trims(makerCode, modelGroupCode, modelCode);
  }
  @GetMapping("/grades")
  public List<Map<String,Object>> grades(@RequestParam String makerCode, @RequestParam String modelGroupCode, @RequestParam String modelCode, @RequestParam String trimCode){
    return service.grades(makerCode, modelGroupCode, modelCode, trimCode);
  }
}