package com.carizon.api.service;

import com.carizon.api.entity.CzModelImage;
import com.carizon.api.repository.CzModelImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CzModelImageService {

    private final CzModelImageRepository czModelImageRepository;

    // Default fallback image URL
    private static final String DEFAULT_IMAGE_URL = 
        "https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/CN7/avante_cn7_main.jpg";

    /**
     * Get representative image URL for a model code
     * Prefer is_main=1, then lowest sort_order
     * If multiple is_main=1, pick first (effectively random among them)
     * Fallback to default image if none found
     */
    public String getRepresentativeImageUrl(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return DEFAULT_IMAGE_URL;
        }

        List<CzModelImage> images = czModelImageRepository.findByModelCodeOrderByMainAndSort(modelCode);
        
        if (images.isEmpty()) {
            return DEFAULT_IMAGE_URL;
        }

        return images.get(0).getImageUrl();
    }

    /**
     * Get all images for a model code
     * Ordered by is_main DESC, sort_order ASC, id ASC
     */
    public List<CzModelImage> getAllImagesForModel(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return List.of();
        }

        return czModelImageRepository.findByModelCodeOrderByIsMainDescSortOrderAscIdAsc(modelCode);
    }
}
