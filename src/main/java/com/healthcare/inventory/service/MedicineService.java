package com.healthcare.inventory.service;

import com.healthcare.inventory.dto.ImportResponse;
import com.healthcare.inventory.dto.MedicineRequest;
import com.healthcare.inventory.dto.MedicineResponse;
import com.healthcare.inventory.dto.SummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MedicineService {

    Page<MedicineResponse> findAll(String search, String category, String status, Pageable pageable);

    MedicineResponse findById(Long id);

    MedicineResponse create(MedicineRequest request);

    MedicineResponse update(Long id, MedicineRequest request);

    void delete(Long id);

    SummaryResponse getSummary();

    List<String> getCategories();

    ImportResponse importMedicines(MultipartFile file);
}
