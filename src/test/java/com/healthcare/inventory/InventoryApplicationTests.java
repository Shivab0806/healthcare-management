package com.healthcare.inventory;

import com.healthcare.inventory.repository.MedicineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MedicineRepository medicineRepository;

    @Test
    void importCsvShouldCreateMedicines() throws Exception {
        medicineRepository.deleteAll();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "medicines.csv",
                "text/csv",
                """
                        name,company,category,price,stock,lowStockThreshold,batchNumber,expiryDate
                        Vitamin D,Health Labs,Supplement,12.99,25,10,HD-1001,2026-12-31
                        Aspirin,QuickCare,Analgesic,3.50,80,20,QC-2002,2027-01-15
                        """.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/medicines/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        assertThat(medicineRepository.count()).isEqualTo(2);
    }
}
