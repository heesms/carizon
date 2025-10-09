package com.carizon.integration.dto;

public class CarPick {
    private String modelCode;

    private String carCode;   // ← 추가
    private Long carSeq;
    private String payload;

    public String getCarCode() {
        return carCode;
    }

    public void setCarCode(String carCode) {
        this.carCode = carCode;
    }

    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public Long getCarSeq() { return carSeq; }
    public void setCarSeq(Long carSeq) { this.carSeq = carSeq; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
