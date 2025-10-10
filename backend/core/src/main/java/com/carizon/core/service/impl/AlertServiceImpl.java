package com.carizon.core.service.impl;

import com.carizon.core.domain.alert.Alert;
import com.carizon.core.repository.AlertRepository;
import com.carizon.core.service.AlertService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;

    public AlertServiceImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public List<Alert> listAlerts(Long userId) {
        return alertRepository.findByUserId(userId);
    }

    @Override
    public Alert createAlert(Long userId, String criteria) {
        return alertRepository.save(new Alert(userId, criteria));
    }

    @Override
    public void deleteAlert(Long userId, Long alertId) {
        alertRepository.deleteByIdAndUserId(alertId, userId);
    }
}
