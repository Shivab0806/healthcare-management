package com.healthcare.inventory.service;

import com.healthcare.inventory.dto.BillRequest;
import com.healthcare.inventory.dto.BillResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface BillService {
    BillResponse createBill(BillRequest request);

    Page<BillResponse> getAllBills(int page, int size, String search, LocalDate fromDate, LocalDate toDate);

    BillResponse getBillById(Long id);

    byte[] generatePdf(Long id);
}