package com.healthcare.inventory.service.impl;

import com.healthcare.inventory.dto.ImportResponse;
import com.healthcare.inventory.dto.MedicineRequest;
import com.healthcare.inventory.dto.MedicineResponse;
import com.healthcare.inventory.dto.SummaryResponse;
import com.healthcare.inventory.exception.ResourceNotFoundException;
import com.healthcare.inventory.mapper.MedicineMapper;
import com.healthcare.inventory.model.Medicine;
import com.healthcare.inventory.repository.MedicineRepository;
import com.healthcare.inventory.repository.MedicineSpecifications;
import com.healthcare.inventory.service.MedicineService;
import com.healthcare.inventory.util.MedicineImportParser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class MedicineServiceImpl implements MedicineService {

    private final MedicineRepository repository;
    private final Validator validator;

    public MedicineServiceImpl(MedicineRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MedicineResponse> findAll(String search, String category, String status, Pageable pageable) {
        Specification<Medicine> spec = Specification
                .where(MedicineSpecifications.nameOrCompanyContains(search))
                .and(MedicineSpecifications.hasCategory(category));

        if (status != null) {
            switch (status.toLowerCase()) {
                case "low-stock" -> spec = spec.and(MedicineSpecifications.isLowStock());
                case "expired" -> spec = spec.and(MedicineSpecifications.isExpired());
                case "expiring-soon" ->
                    spec = spec.and(MedicineSpecifications.isExpiringSoon(MedicineMapper.EXPIRING_SOON_DAYS));
                default -> {
                    // "all" (or anything else) -> no extra filter
                }
            }
        }

        return repository.findAll(spec, pageable).map(MedicineMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicineResponse findById(Long id) {
        return MedicineMapper.toResponse(getMedicineOrThrow(id));
    }

    @Override
    public MedicineResponse create(MedicineRequest request) {
        Medicine medicine = MedicineMapper.toEntity(request);
        return MedicineMapper.toResponse(repository.save(medicine));
    }

    @Override
    public MedicineResponse update(Long id, MedicineRequest request) {
        Medicine medicine = getMedicineOrThrow(id);
        MedicineMapper.updateEntity(medicine, request);
        return MedicineMapper.toResponse(repository.save(medicine));
    }

    @Override
    public void delete(Long id) {
        repository.delete(getMedicineOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public SummaryResponse getSummary() {
        List<Medicine> all = repository.findAll();
        LocalDate today = LocalDate.now();

        long expired = all.stream().filter(m -> m.getExpiryDate().isBefore(today)).count();
        long lowStock = all.stream().filter(m -> m.getStock() <= m.getLowStockThreshold()).count();
        long expiringSoon = all.stream()
                .filter(m -> !m.getExpiryDate().isBefore(today))
                .filter(m -> !m.getExpiryDate().isAfter(today.plusDays(MedicineMapper.EXPIRING_SOON_DAYS)))
                .count();

        return SummaryResponse.builder()
                .total(all.size())
                .expired(expired)
                .lowStock(lowStock)
                .expiringSoon(expiringSoon)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return repository.findAll().stream()
                .map(Medicine::getCategory)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Override
    public ImportResponse importMedicines(MultipartFile file) {
        List<MedicineRequest> requests = MedicineImportParser.parse(file);
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("No valid medicine rows were found in the uploaded file.");
        }

        List<Medicine> medicines = requests.stream()
                .map(this::validateAndMap)
                .toList();

        repository.saveAll(medicines);
        return ImportResponse.builder()
                .imported(medicines.size())
                .message("Imported " + medicines.size() + " medicine(s) successfully.")
                .build();
    }

    private Medicine validateAndMap(MedicineRequest request) {
        Set<ConstraintViolation<MedicineRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .findFirst()
                    .map(ConstraintViolation::getMessage)
                    .orElse("Invalid medicine data in the uploaded file.");
            throw new IllegalArgumentException(message);
        }
        return MedicineMapper.toEntity(request);
    }

    private Medicine getMedicineOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found with id: " + id));
    }
}
