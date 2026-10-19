package com.healthcare.inventory.repository;

import com.healthcare.inventory.model.Bill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    @Query("SELECT b FROM Bill b WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(b.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(b.customerName) LIKE LOWER(CONCAT('%', :search, '%'))) "
            +
            "AND (:fromDate IS NULL OR b.createdAt >= :fromDate) " +
            "AND (:toDate IS NULL OR b.createdAt <= :toDate)")
    Page<Bill> searchBills(@Param("search") String search,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);
}
