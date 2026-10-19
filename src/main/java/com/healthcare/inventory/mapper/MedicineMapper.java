package com.healthcare.inventory.mapper;

import com.healthcare.inventory.dto.MedicineRequest;
import com.healthcare.inventory.dto.MedicineResponse;
import com.healthcare.inventory.model.Medicine;
import com.healthcare.inventory.model.MedicineStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class MedicineMapper {

    /** Window (in days) used to flag a medicine as "Expiring soon". */
    public static final int EXPIRING_SOON_DAYS = 30;

    private MedicineMapper() {
    }

    public static Medicine toEntity(MedicineRequest req) {
        return Medicine.builder()
                .name(req.getName())
                .company(req.getCompany())
                .category(req.getCategory())
                .price(req.getPrice())
                .stock(req.getStock())
                .lowStockThreshold(req.getLowStockThreshold() != null ? req.getLowStockThreshold() : 20)
                .batchNumber(req.getBatchNumber())
                .expiryDate(req.getExpiryDate())
                .build();
    }

    public static void updateEntity(Medicine medicine, MedicineRequest req) {
        medicine.setName(req.getName());
        medicine.setCompany(req.getCompany());
        medicine.setCategory(req.getCategory());
        medicine.setPrice(req.getPrice());
        medicine.setStock(req.getStock());
        medicine.setLowStockThreshold(req.getLowStockThreshold() != null ? req.getLowStockThreshold() : 20);
        medicine.setBatchNumber(req.getBatchNumber());
        medicine.setExpiryDate(req.getExpiryDate());
    }

    public static MedicineResponse toResponse(Medicine m) {
        LocalDate today = LocalDate.now();
        long daysUntilExpiry = ChronoUnit.DAYS.between(today, m.getExpiryDate());
        MedicineStatus status = resolveStatus(m, today);

        return MedicineResponse.builder()
                .id(m.getId())
                .name(m.getName())
                .company(m.getCompany())
                .category(m.getCategory())
                .price(m.getPrice())
                .stock(m.getStock())
                .lowStockThreshold(m.getLowStockThreshold())
                .batchNumber(m.getBatchNumber())
                .expiryDate(m.getExpiryDate())
                .status(status)
                .statusLabel(status.getLabel())
                .daysUntilExpiry(daysUntilExpiry)
                .build();
    }

    /** Priority when a medicine matches more than one condition: Expired > Low stock > Expiring soon > OK. */
    public static MedicineStatus resolveStatus(Medicine m, LocalDate today) {
        if (m.getExpiryDate().isBefore(today)) {
            return MedicineStatus.EXPIRED;
        }
        if (m.getStock() <= m.getLowStockThreshold()) {
            return MedicineStatus.LOW_STOCK;
        }
        if (!m.getExpiryDate().isAfter(today.plusDays(EXPIRING_SOON_DAYS))) {
            return MedicineStatus.EXPIRING_SOON;
        }
        return MedicineStatus.OK;
    }
}
