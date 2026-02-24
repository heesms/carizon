package com.carizon.controller;
import com.carizon.service.CodeQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController @RequestMapping("/api/codes")
public class CodeController {
  private final CodeQueryService service;
  public CodeController(CodeQueryService service){ this.service = service; }
  @GetMapping("/makers")
  public List<Map<String,Object>> makers(@RequestParam Map<String, String> params){
    long start = System.currentTimeMillis();
    log.info("[codes.makers] request start");
    List<Map<String,Object>> rows = service.makers(params);
    long totalMs = System.currentTimeMillis() - start;
    log.info("[codes.makers] request done: rows={}, totalMs={}ms", rows.size(), totalMs);
    return rows;
  }
  @GetMapping("/body-types")
  public List<Map<String,Object>> bodyTypes(@RequestParam Map<String, String> params){ return service.bodyTypes(params); }
  @GetMapping("/fuels")
  public List<Map<String,Object>> fuels(@RequestParam Map<String, String> params){ return service.fuels(params); }
  @GetMapping("/colors")
  public List<Map<String,Object>> colors(@RequestParam Map<String, String> params){ return service.colors(params); }
  @GetMapping("/model-groups")
  public List<Map<String,Object>> modelGroups(@RequestParam String makerCode, @RequestParam Map<String, String> params){
    long start = System.currentTimeMillis();
    log.info("[codes.model-groups] request start: makerCode={}", makerCode);
    List<Map<String,Object>> rows = service.modelGroups(makerCode, params);
    long totalMs = System.currentTimeMillis() - start;
    log.info("[codes.model-groups] request done: makerCode={}, rows={}, totalMs={}ms", makerCode, rows.size(), totalMs);
    return rows;
  }
  @GetMapping("/models")
  public List<Map<String,Object>> models(@RequestParam String makerCode, @RequestParam String modelGroupCode, @RequestParam Map<String, String> params){
    long start = System.currentTimeMillis();
    log.info("[codes.models] request start: makerCode={}, modelGroupCode={}", makerCode, modelGroupCode);
    List<Map<String,Object>> rows = service.models(makerCode, modelGroupCode, params);
    long totalMs = System.currentTimeMillis() - start;
    log.info("[codes.models] request done: makerCode={}, modelGroupCode={}, rows={}, totalMs={}ms",
        makerCode, modelGroupCode, rows.size(), totalMs);
    return rows;
  }
  @GetMapping("/trims")
  public List<Map<String,Object>> trims(@RequestParam String makerCode, @RequestParam String modelGroupCode, @RequestParam String modelCode, @RequestParam Map<String, String> params){
    return service.trims(makerCode, modelGroupCode, modelCode, params);
  }
  @GetMapping("/grades")
  public List<Map<String,Object>> grades(@RequestParam String makerCode, @RequestParam String modelGroupCode, @RequestParam String modelCode, @RequestParam String trimCode){
    return service.grades(makerCode, modelGroupCode, modelCode, trimCode);
  }
}
