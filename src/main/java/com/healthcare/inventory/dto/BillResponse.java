// src/main/java/com/healthcare/inventory/dto/BillResponse.java
package com.healthcare.inventory.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BillResponse {
    private Long id;
    private String invoiceNumber;
    private String customerName;
    private String customerPhone;
    private String paymentMode;
    private LocalDateTime createdAt;

    private BigDecimal subTotal;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private BigDecimal taxPercent;
    private BigDecimal taxAmount;
    private BigDecimal grandTotal;

    private List<BillItemDetail> items;

    @Data
    @Builder
    public static class BillItemDetail {
        private Long medicineId;
        private String medicineName;
        private String company;
        private String batchNumber;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}