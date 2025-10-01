package com.smn.maratang.Dangerouses;

public class DangerItem {
    private String title;
    private String timestamp;
    private String riskLevel;

    public DangerItem(String title, String timestamp, String riskLevel) {
        this.title = title;
        this.timestamp = timestamp;
        this.riskLevel = riskLevel;
    }

    public String getTitle() {
        return title;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getRiskLevel() {
        return riskLevel;
    }
}
