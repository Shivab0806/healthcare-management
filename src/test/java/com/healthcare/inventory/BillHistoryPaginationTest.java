package com.healthcare.inventory;

import com.healthcare.inventory.model.Bill;
import com.healthcare.inventory.repository.BillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BillHistoryPaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BillRepository billRepository;

    @BeforeEach
    void setUp() {
        billRepository.deleteAll();
    }

    @Test
    void shouldReturnPaginatedBillHistory() throws Exception {
        billRepository.save(Bill.builder()
                .invoiceNumber("INV-TEST-001")
                .customerName("Alice")
                .customerPhone("9876543210")
                .paymentMode("CASH")
                .subTotal(new BigDecimal("100.00"))
                .discountPercent(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .taxPercent(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("100.00"))
                .itemsJson("[]")
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/bills")
                .param("page", "0")
                .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerName").value("Alice"))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.number").value(0));
    }
}
