package com.carizon.core.service;

import com.carizon.core.domain.alert.Alert;

import java.util.List;

public interface AlertService {
    List<Alert> listAlerts(Long userId);
    Alert createAlert(Long userId, String criteria);
    void deleteAlert(Long userId, Long alertId);
}
