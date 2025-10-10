package com.carizon.core.service.search;

import com.carizon.core.dto.CarSummaryDto;
import com.carizon.core.service.CarService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchProvider {
    Page<CarSummaryDto> search(String query, CarService.CarSearchFilters filters, Pageable pageable);
}
