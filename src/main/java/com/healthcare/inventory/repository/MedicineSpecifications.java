package com.healthcare.inventory.repository;

import com.healthcare.inventory.model.Medicine;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Reusable JPA Specifications used to build the dynamic search/filter query
 * behind GET /api/medicines.
 */
public class MedicineSpecifications {

    private MedicineSpecifications() {
    }

    public static Specification<Medicine> nameOrCompanyContains(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("company")), like)
            );
        };
    }

    public static Specification<Medicine> hasCategory(String category) {
        return (root, query, cb) -> {
            if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("category")), category.toLowerCase());
        };
    }

    public static Specification<Medicine> isLowStock() {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("stock"), root.get("lowStockThreshold"));
    }

    public static Specification<Medicine> isExpired() {
        return (root, query, cb) -> cb.lessThan(root.get("expiryDate"), LocalDate.now());
    }

    public static Specification<Medicine> isExpiringSoon(int days) {
        return (root, query, cb) -> {
            LocalDate today = LocalDate.now();
            LocalDate until = today.plusDays(days);
            return cb.and(
                    cb.greaterThanOrEqualTo(root.get("expiryDate"), today),
                    cb.lessThanOrEqualTo(root.get("expiryDate"), until)
            );
        };
    }
}
