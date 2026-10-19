package com.healthcare.inventory.dto;

import com.healthcare.inventory.model.MedicineStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outbound representation of a medicine, including the computed status badge.
 */
@Getter
@Builder
@AllArgsConstructor
public class MedicineResponse {
    private Long id;
    private String name;
    private String company;
    private String category;
    private BigDecimal price;
    private Integer stock;
    private Integer lowStockThreshold;
    private LocalDate expiryDate;
    private String batchNumber;
    private MedicineStatus status;
    private String statusLabel;
    private long daysUntilExpiry;
}
