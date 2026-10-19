//package com.healthcare.inventory;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.healthcare.inventory.dto.BillRequest;
//import com.healthcare.inventory.repository.BillRepository;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.math.BigDecimal;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@SpringBootTest
//@AutoConfigureMockMvc
//class BillApiTests {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private BillRepository billRepository;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Test
//    void shouldSaveAndFetchBillHistory() throws Exception {
//        billRepository.deleteAll();
//
//        BillRequest request = new BillRequest();
//        request.setCustomerName("Alice");
//        request.setSubtotal(new BigDecimal("500.00"));
//        request.setDiscountPercent(new BigDecimal("10"));
//        request.setTaxPercent(new BigDecimal("5"));
//        request.setDiscountAmount(new BigDecimal("50.00"));
//        request.setTaxAmount(new BigDecimal("22.50"));
//        request.setTotal(new BigDecimal("472.50"));
//        request.setItemCount(2);
//        request.setItemsJson("Paracetamol x2, Cough Syrup x1");
//
//        mockMvc.perform(post("/api/bills")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.customerName").value("Alice"))
//                .andExpect(jsonPath("$.total").value(472.50));
//
//        mockMvc.perform(get("/api/bills"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$[0].customerName").value("Alice"));
//
//        assertThat(billRepository.count()).isEqualTo(1);
//    }
//}
