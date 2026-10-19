package com.healthcare.inventory;

import com.healthcare.inventory.model.Bill;
import com.healthcare.inventory.model.Medicine;
import com.healthcare.inventory.repository.BillRepository;
import com.healthcare.inventory.repository.MedicineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BillPdfApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Test
    void billPdfEndpointShouldReturnPdf() throws Exception {
        billRepository.deleteAll();
        medicineRepository.deleteAll();

        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("Amlodipine")
                .company("Health Labs")
                .category("Cardiac")
                .price(new BigDecimal("30.00"))
                .stock(50)
                .lowStockThreshold(10)
                .batchNumber("HL-101")
                .expiryDate(LocalDate.now().plusMonths(12))
                .build());

        Bill bill = billRepository.save(Bill.builder()
                .invoiceNumber("INV-TEST-001")
                .customerName("Rahul")
                .customerPhone("9876543210")
                .paymentMode("CASH")
                .subTotal(new BigDecimal("30.00"))
                .discountPercent(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .taxPercent(new BigDecimal("12.00"))
                .taxAmount(new BigDecimal("3.60"))
                .grandTotal(new BigDecimal("33.60"))
                .itemsJson("[{\"medicineId\":" + medicine.getId()
                        + ",\"medicineName\":\"Amlodipine\",\"company\":\"Health Labs\",\"batchNumber\":\"HL-101\",\"quantity\":1,\"unitPrice\":30.00,\"lineTotal\":30.00}]")
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/bills/{id}/pdf", bill.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")));
    }
}
