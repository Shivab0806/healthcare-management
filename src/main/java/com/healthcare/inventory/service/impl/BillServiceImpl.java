package com.healthcare.inventory.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.inventory.dto.BillRequest;
import com.healthcare.inventory.dto.BillResponse;
import com.healthcare.inventory.exception.ResourceNotFoundException;
import com.healthcare.inventory.model.Bill;
import com.healthcare.inventory.model.Medicine;
import com.healthcare.inventory.repository.BillRepository;
import com.healthcare.inventory.repository.MedicineRepository;
import com.healthcare.inventory.service.BillService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {

    private final BillRepository billRepository;
    private final MedicineRepository medicineRepository;
    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;

    @Override
    @Transactional
    public BillResponse createBill(BillRequest request) {
        List<BillResponse.BillItemDetail> itemDetails = new ArrayList<>();
        BigDecimal subTotal = BigDecimal.ZERO;

        for (BillRequest.BillItemRequest itemReq : request.getItems()) {
            Medicine medicine = medicineRepository.findById(itemReq.getMedicineId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Medicine not found with ID: " + itemReq.getMedicineId()));

            int effectiveQuantity = itemReq.getEffectiveQuantity();

            if (medicine.getExpiryDate() != null && medicine.getExpiryDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Cannot bill expired medicine: " + medicine.getName()
                        + " (Expired on " + medicine.getExpiryDate() + ")");
            }

            if (medicine.getStock() < effectiveQuantity) {
                throw new IllegalArgumentException("Insufficient stock for " + medicine.getName()
                        + ". Available: " + medicine.getStock() + ", Requested: " + effectiveQuantity);
            }

            medicine.setStock(medicine.getStock() - effectiveQuantity);
            medicineRepository.save(medicine);

            BigDecimal unitPrice = medicine.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(effectiveQuantity))
                    .setScale(2, RoundingMode.HALF_UP);
            subTotal = subTotal.add(lineTotal);

            itemDetails.add(BillResponse.BillItemDetail.builder()
                    .medicineId(medicine.getId())
                    .medicineName(medicine.getName())
                    .company(medicine.getCompany())
                    .batchNumber(medicine.getBatchNumber())
                    .quantity(effectiveQuantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build());
        }

        BigDecimal discountPct = request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO;
        BigDecimal taxPct = request.getTaxPercent() != null ? request.getTaxPercent() : BigDecimal.ZERO;

        BigDecimal discountAmount = subTotal.multiply(discountPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount = subTotal.subtract(discountAmount);
        BigDecimal taxAmount = taxableAmount.multiply(taxPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = taxableAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        String invoiceNo = "INV-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + ThreadLocalRandom.current().nextInt(100, 999);

        String itemsJson = "[]";
        try {
            itemsJson = objectMapper.writeValueAsString(itemDetails);
        } catch (Exception ignored) {
        }

        Bill savedBill = billRepository.save(Bill.builder()
                .invoiceNumber(invoiceNo)
                .customerName(request.getCustomerName().trim())
                .customerPhone(request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : "")
                .paymentMode(request.getPaymentMode() != null ? request.getPaymentMode() : "CASH")
                .subTotal(subTotal)
                .discountPercent(discountPct)
                .discountAmount(discountAmount)
                .taxPercent(taxPct)
                .taxAmount(taxAmount)
                .grandTotal(grandTotal)
                .itemsJson(itemsJson)
                .createdAt(LocalDateTime.now())
                .build());

        return mapToResponse(savedBill, itemDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BillResponse> getAllBills(int page, int size, String search, LocalDate fromDate, LocalDate toDate) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedSearch = (search == null || search.isBlank()) ? "" : search.trim();

        LocalDateTime start = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime end = toDate != null ? toDate.plusDays(1).atStartOfDay() : null;

        return billRepository.searchBills(normalizedSearch, start, end, pageable)
                .map(bill -> mapToResponse(bill, parseItems(bill.getItemsJson())));
    }

    @Override
    @Transactional(readOnly = true)
    public BillResponse getBillById(Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with ID: " + id));
        return mapToResponse(bill, parseItems(bill.getItemsJson()));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        BillResponse bill = getBillById(id);

        Context context = new Context();
        context.setVariable("bill", bill);
        context.setVariable("invoiceNumber", bill.getInvoiceNumber());
        context.setVariable("customerName", bill.getCustomerName());
        context.setVariable("customerPhone", bill.getCustomerPhone());
        context.setVariable("paymentMode", bill.getPaymentMode());
        context.setVariable("createdAt", bill.getCreatedAt());
        context.setVariable("subTotal", bill.getSubTotal());
        context.setVariable("discountAmount", bill.getDiscountAmount());
        context.setVariable("taxAmount", bill.getTaxAmount());
        context.setVariable("grandTotal", bill.getGrandTotal());
        context.setVariable("discountPercent", bill.getDiscountPercent());
        context.setVariable("taxPercent", bill.getTaxPercent());

        String html = templateEngine.process("invoice-pdf", context);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(
                    () -> getClass().getResourceAsStream("/fonts/DejaVuSans.ttf"),
                    "DejaVu Sans");
            builder.useFastMode();
            builder.withHtmlContent(html, "");
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate invoice PDF for billId=" + id, ex);
        }
    }

    private List<BillResponse.BillItemDetail> parseItems(String json) {
        if (json == null || json.isBlank())
            return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<BillResponse.BillItemDetail>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private BillResponse mapToResponse(Bill bill, List<BillResponse.BillItemDetail> items) {
        return BillResponse.builder()
                .id(bill.getId())
                .invoiceNumber(bill.getInvoiceNumber())
                .customerName(bill.getCustomerName())
                .customerPhone(bill.getCustomerPhone())
                .paymentMode(bill.getPaymentMode())
                .createdAt(bill.getCreatedAt())
                .subTotal(bill.getSubTotal())
                .discountPercent(bill.getDiscountPercent())
                .discountAmount(bill.getDiscountAmount())
                .taxPercent(bill.getTaxPercent())
                .taxAmount(bill.getTaxAmount())
                .grandTotal(bill.getGrandTotal())
                .items(items)
                .build();
    }
}