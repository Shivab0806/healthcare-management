// src/main/java/com/healthcare/inventory/controller/BillRestController.java
package com.healthcare.inventory.controller;

import com.healthcare.inventory.dto.BillRequest;
import com.healthcare.inventory.dto.BillResponse;
import com.healthcare.inventory.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillRestController {

    private final BillService billService;

    @PostMapping
    public ResponseEntity<BillResponse> generateBill(@Valid @RequestBody BillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billService.createBill(request));
    }

    @GetMapping
    public ResponseEntity<Page<BillResponse>> getBillHistory(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(billService.getAllBills(page, size, search, fromDate, toDate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillResponse> getBillById(@PathVariable Long id) {
        return ResponseEntity.ok(billService.getBillById(id));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        BillResponse bill = billService.getBillById(id);
        byte[] pdf = billService.generatePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename((bill.getInvoiceNumber() != null ? bill.getInvoiceNumber() : "invoice-" + bill.getId())
                        + ".pdf")
                .build());

        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}