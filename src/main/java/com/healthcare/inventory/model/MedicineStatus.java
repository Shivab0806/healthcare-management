package com.healthcare.inventory.model;

import lombok.Getter;

@Getter
public enum MedicineStatus {
    OK("OK"),
    LOW_STOCK("Low stock"),
    EXPIRED("Expired"),
    EXPIRING_SOON("Expiring soon");

    private final String label;

    MedicineStatus(String label) {
        this.label = label;
    }

}
