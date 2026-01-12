package com.carizon.service;
import com.carizon.domain.mapper.CarMapper;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class CodeQueryService {
  private final CarMapper mapper;
  public CodeQueryService(CarMapper mapper){ this.mapper = mapper; }
  public List<Map<String,Object>> makers(){ return mapper.selectMakers(); }
  public List<Map<String,Object>> modelGroups(String makerCode){ return mapper.selectModelGroups(makerCode); }
  public List<Map<String,Object>> models(String makerCode, String modelGroupCode){ return mapper.selectModels(makerCode, modelGroupCode); }
  public List<Map<String,Object>> trims(String makerCode, String modelGroupCode, String modelCode){ return mapper.selectTrims(makerCode, modelGroupCode, modelCode); }
  public List<Map<String,Object>> grades(String makerCode, String modelGroupCode, String modelCode, String trimCode){ return mapper.selectGrades(makerCode, modelGroupCode, modelCode, trimCode); }
}