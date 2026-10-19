package com.healthcare.inventory.controller;

import com.healthcare.inventory.dto.ImportResponse;
import com.healthcare.inventory.dto.MedicineRequest;
import com.healthcare.inventory.dto.MedicineResponse;
import com.healthcare.inventory.dto.SummaryResponse;
import com.healthcare.inventory.service.MedicineService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST API for the Medicine Inventory.
 *
 * GET /api/medicines - paginated list (search / category / status filters)
 * GET /api/medicines/{id} - single medicine
 * POST /api/medicines - create
 * PUT /api/medicines/{id} - update
 * DELETE /api/medicines/{id} - delete
 * GET /api/medicines/summary - counts for the alert banners + filter tabs
 * GET /api/medicines/categories - distinct category list, for the dropdown
 */
@RestController
@RequestMapping("/api/medicines")
public class MedicineRestController {

    private final MedicineService service;

    public MedicineRestController(MedicineService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<MedicineResponse>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(service.findAll(search, category, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicineResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<MedicineResponse> create(@Valid @RequestBody MedicineRequest request) {
        MedicineResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MedicineResponse> update(@PathVariable Long id, @Valid @RequestBody MedicineRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<ImportResponse> importMedicines(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importMedicines(file));
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> summary() {
        return ResponseEntity.ok(service.getSummary());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(service.getCategories());
    }
}
