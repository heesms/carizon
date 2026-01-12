package com.carizon.service;
import com.carizon.domain.mapper.CarMapper;
import com.carizon.dto.ModelImageDto;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class ModelQueryService {
  private final CarMapper mapper;
  public ModelQueryService(CarMapper mapper){ this.mapper = mapper; }
  public List<ModelImageDto> images(String modelCode){ return mapper.selectModelImages(modelCode); }
}