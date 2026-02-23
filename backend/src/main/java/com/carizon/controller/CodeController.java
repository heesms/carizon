package com.carizon.controller;
import com.carizon.service.CodeQueryService;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/codes")
public class CodeController {
  private final CodeQueryService service;
  public CodeController(CodeQueryService service){ this.service = service; }
  @GetMapping("/makers")
  public List<Map<String,Object>> makers(@RequestParam Map<String, String> q){
    return service.makers(normalize(q));
  }
  @GetMapping("/model-groups")
  public List<Map<String,Object>> modelGroups(@RequestParam String makerCode, @RequestParam Map<String, String> q){
    return service.modelGroups(makerCode, normalize(q, Arrays.asList("makerCode")));
  }
  @GetMapping("/models")
  public List<Map<String,Object>> models(@RequestParam String makerCode, @RequestParam String modelGroupCode, @RequestParam Map<String, String> q){
    return service.models(makerCode, modelGroupCode, normalize(q, Arrays.asList("makerCode", "modelGroupCode")));
  }
  @GetMapping("/trims")
  public List<Map<String,Object>> trims(@RequestParam String makerCode, @RequestParam String modelGroupCode, @RequestParam String modelCode, @RequestParam Map<String, String> q){
    return service.trims(makerCode, modelGroupCode, modelCode, normalize(q, Arrays.asList("makerCode", "modelGroupCode", "modelCode")));
  }
  @GetMapping("/grades")
  public List<Map<String,Object>> grades(@RequestParam String makerCode, @RequestParam String modelGroupCode, @RequestParam String modelCode, @RequestParam String trimCode){
    return service.grades(makerCode, modelGroupCode, modelCode, trimCode);
  }
  @GetMapping("/body-types")
  public List<Map<String,Object>> bodyTypes(@RequestParam Map<String, String> q){
    return service.bodyTypes(normalize(q));
  }
  @GetMapping("/fuels")
  public List<Map<String,Object>> fuels(@RequestParam Map<String, String> q){
    return service.fuels(normalize(q));
  }
  @GetMapping("/colors")
  public List<Map<String,Object>> colors(@RequestParam Map<String, String> q){
    return service.colors(normalize(q));
  }

  @SuppressWarnings("unchecked")
  private Map<String,Object> normalize(Map<String, String> q){
    if (q == null || q.isEmpty()) return new LinkedCaseInsensitiveMap<>();
    Map<String,Object> out = new LinkedCaseInsensitiveMap<>();
    q.forEach((k, v) -> {
      if (v == null || v.isEmpty()) return;
      out.put(k, v);
    });
    return out;
  }

  private Map<String,Object> normalize(Map<String, String> q, List<String> exclude){
    if (q == null || q.isEmpty()) return new LinkedCaseInsensitiveMap<>();
    Map<String,Object> out = new LinkedCaseInsensitiveMap<>();
    q.forEach((k, v) -> {
      if (exclude.contains(k)) return;
      if (v == null || v.isEmpty()) return;
      out.put(k, v);
    });
    return out;
  }
} 
