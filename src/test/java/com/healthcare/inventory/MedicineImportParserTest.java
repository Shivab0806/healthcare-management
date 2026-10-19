package com.healthcare.inventory;

import com.healthcare.inventory.dto.MedicineRequest;
import com.healthcare.inventory.util.MedicineImportParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MedicineImportParserTest {

    @Test
    void parseMixedStockCsvTemplate_shouldMatchFrontendTemplateColumns() {
        String csv = """
                name,company,category,stripPrice,tabletsPerStrip,stripQuantity,looseTablets,batchNumber,expiryDate,lowStockThreshold
                Paracetamol 500mg,Sunrise Pharma,Analgesic,25.00,10,3,17,SP-2201,2027-12-31,20
                Amoxicillin 250mg,MediCore Labs,Antibiotic,93.00,15,2,5,ML-5590,2027-06-30,15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "medicine-import-template.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        List<MedicineRequest> medicines = MedicineImportParser.parse(file);

        assertThat(medicines).hasSize(2);
        assertThat(medicines.get(0).getStock()).isEqualTo(47);
        assertThat(medicines.get(1).getStock()).isEqualTo(35);
        assertThat(medicines.get(0).getCompany()).isEqualTo("Sunrise Pharma");
        assertThat(medicines.get(0).getPrice()).isEqualByComparingTo("2.50");
        assertThat(medicines.get(1).getPrice()).isEqualByComparingTo("6.20");
        assertThat(medicines.get(1).getBatchNumber()).isEqualTo("ML-5590");
    }
}
