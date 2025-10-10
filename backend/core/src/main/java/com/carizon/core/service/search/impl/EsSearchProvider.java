package com.carizon.core.service.search.impl;

import com.carizon.core.dto.CarSummaryDto;
import com.carizon.core.service.CarService;
import com.carizon.core.service.search.SearchProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("elasticsearchSearchProvider")
public class EsSearchProvider implements SearchProvider {

    @Override
    public Page<CarSummaryDto> search(String query, CarService.CarSearchFilters filters, Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0);
    }
}
