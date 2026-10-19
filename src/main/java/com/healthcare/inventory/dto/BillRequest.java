package com.healthcare.inventory.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BillRequest {
    @NotBlank(message = "Customer/Patient name is required")
    private String customerName;

    private String customerPhone;
    private String paymentMode; // CASH, UPI, CARD

    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "100.0", inclusive = true)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "100.0", inclusive = true)
    private BigDecimal taxPercent = BigDecimal.ZERO; // e.g., GST %

    @NotEmpty(message = "Bill must contain at least one medicine item")
    private List<BillItemRequest> items;

    @Data
    public static class BillItemRequest {
        @NotNull
        private Long medicineId;

        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private String quantityType = "TABLET";

        @Min(value = 1, message = "Tablets per strip must be at least 1")
        private Integer tabletsPerStrip = 10;

        public int getEffectiveQuantity() {
            if (quantity == null) {
                return 0;
            }
            if (quantityType == null || "TABLET".equalsIgnoreCase(quantityType.trim())) {
                return quantity;
            }
            int stripSize = (tabletsPerStrip == null || tabletsPerStrip <= 0) ? 1 : tabletsPerStrip;
            return quantity * stripSize;
        }
    }
}