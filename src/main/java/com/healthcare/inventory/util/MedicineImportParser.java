package com.healthcare.inventory.util;

import com.healthcare.inventory.dto.MedicineRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MedicineImportParser {

    private MedicineImportParser() {
    }

    public static List<MedicineRequest> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a CSV or Excel file to import.");
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";

        if ("csv".equals(extension)) {
            return parseCsv(file);
        }
        if ("xls".equals(extension) || "xlsx".equals(extension)) {
            return parseWorkbook(file);
        }

        throw new IllegalArgumentException("Unsupported file type. Please upload a CSV or Excel (.xls/.xlsx) file.");
    }

    private static List<MedicineRequest> parseCsv(MultipartFile file) {
        List<MedicineRequest> medicines = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setIgnoreSurroundingSpaces(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)) {

            Iterator<CSVRecord> iterator = parser.iterator();
            if (!iterator.hasNext()) {
                return medicines;
            }

            CSVRecord headerRow = iterator.next();
            List<String> headers = new ArrayList<>();
            for (String header : headerRow) {
                headers.add(normalizeHeader(header));
            }

            while (iterator.hasNext()) {
                CSVRecord record = iterator.next();
                if (record == null || isBlankRecord(record)) {
                    continue;
                }

                Map<String, String> row = new HashMap<>();
                for (int index = 0; index < headers.size(); index++) {
                    String header = headers.get(index);
                    String value = index < record.size() ? record.get(index) : "";
                    row.put(header, value);
                }
                if (isBlankRow(row)) {
                    continue;
                }
                medicines.add(toMedicineRequest(row));
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Unable to read the CSV file. Please check that the headers and values are valid.", ex);
        }

        return medicines;
    }

    private static List<MedicineRequest> parseWorkbook(MultipartFile file) {
        List<MedicineRequest> medicines = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                throw new IllegalArgumentException("The Excel file is empty or does not contain any rows to import.");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("The Excel file must contain a header row.");
            }

            Map<String, Integer> indexes = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = normalizeHeader(getCellValue(cell));
                if (!header.isBlank()) {
                    indexes.put(header, cell.getColumnIndex());
                }
            }

            if (indexes.isEmpty()) {
                throw new IllegalArgumentException("No valid header columns were found in the uploaded file.");
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, indexes)) {
                    continue;
                }

                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, Integer> entry : indexes.entrySet()) {
                    Cell cell = row.getCell(entry.getValue(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    values.put(entry.getKey(), getCellValue(cell));
                }
                if (isBlankRow(values)) {
                    continue;
                }
                medicines.add(toMedicineRequest(values));
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException(
                    "Unable to read the Excel file. Please verify the file format and column names.", ex);
        }

        return medicines;
    }

    private static MedicineRequest toMedicineRequest(Map<String, String> values) {
        MedicineRequest request = new MedicineRequest();

        request.setName(readRequired(values, "name", "medicinename", "medicine", "medicine_name", "medicine_name"));
        request.setCompany(readRequired(values, "company", "companyname", "manufacturer", "supplier", "company_name"));
        request.setCategory(readRequired(values, "category", "type", "medicinecategory"));

        request.setPrice(parseUnitPrice(values));
        request.setStock(parseStockValue(values));

        String lowStockText = readOptional(values, "lowstockthreshold", "reorderlevel", "threshold", "lowstock",
                "minimumstocklevel", "stockthreshold");
        request.setLowStockThreshold(lowStockText == null ? 20 : Integer.parseInt(lowStockText));

        request.setBatchNumber(readRequired(values, "batchnumber", "batch", "batchno", "lotnumber", "batch_number"));

        String expiryDateText = readRequired(values, "expirydate", "expirationdate", "expiry", "expiration",
                "expirationdate", "expdate");
        request.setExpiryDate(parseDate(expiryDateText));

        return request;
    }

    private static BigDecimal parseUnitPrice(Map<String, String> values) {
        String directPriceText = readOptional(values, "price", "unitprice", "priceperunit", "sellingprice",
                "unit_price", "price_per_unit");
        String stripPriceText = readOptional(values, "stripprice", "priceperstrip", "strippricevalue",
                "strip_cost", "price_per_strip", "strip_price");
        String tabletsPerStripText = readOptional(values, "tabletsperstrip", "tabletsinstrip", "striptablets",
                "tablets_per_strip", "tabletsperstripcount");

        if (stripPriceText != null) {
            BigDecimal stripPrice = new BigDecimal(stripPriceText);
            int tabletsPerStrip = parseIntegerOrZero(tabletsPerStripText);
            if (tabletsPerStrip > 0) {
                return stripPrice.divide(BigDecimal.valueOf(tabletsPerStrip), 4, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.HALF_UP);
            }
            return stripPrice.setScale(2, RoundingMode.HALF_UP);
        }

        if (directPriceText == null || directPriceText.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing price value. Use 'stripPrice' or provide a unit price for the medicine.");
        }

        return new BigDecimal(directPriceText).setScale(2, RoundingMode.HALF_UP);
    }

    private static int parseStockValue(Map<String, String> values) {
        String stockText = readOptional(values, "stock", "quantity", "unitsavailable", "totalstock");
        String tabletsPerStripText = readOptional(values, "tabletsperstrip", "tabletsinstrip", "striptablets",
                "tablets_per_strip", "tabletsperstripcount");
        String stripQuantityText = readOptional(values, "stripquantity", "strips", "numberofstrips", "stripcount",
                "noofstrips", "numberofstrip");
        String looseTabletsText = readOptional(values, "loosetablets", "extrasingletablets", "singletablets",
                "loosecount", "singletablets", "extraunits", "loose_tablets");

        boolean hasStripFields = tabletsPerStripText != null || stripQuantityText != null || looseTabletsText != null;
        if (hasStripFields) {
            int tabletsPerStrip = parseIntegerOrZero(tabletsPerStripText);
            int stripQuantity = parseIntegerOrZero(stripQuantityText);
            int looseTablets = parseIntegerOrZero(looseTabletsText);
            int stripTotal = tabletsPerStrip > 0 ? tabletsPerStrip * stripQuantity : 0;
            return stripTotal + looseTablets;
        }

        if (stockText == null || stockText.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing stock value. Use 'stock' or provide strip/loose tablet columns.");
        }
        return Integer.parseInt(stockText);
    }

    private static int parseIntegerOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    private static String readRequired(Map<String, String> row, String... keys) {
        String value = readOptional(row, keys);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required column value. Expected one of: " + String.join(", ", keys));
        }
        return value;
    }

    private static String readOptional(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String normalized = normalizeHeader(key);
            if (row.containsKey(normalized)) {
                String value = row.get(normalized);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static boolean isBlankRecord(CSVRecord record) {
        for (int index = 0; index < record.size(); index++) {
            if (record.get(index) != null && !record.get(index).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlankRow(Map<String, String> row) {
        return row.values().stream().allMatch(value -> value == null || value.isBlank());
    }

    private static boolean isBlankRow(Row row, Map<String, Integer> indexes) {
        for (Map.Entry<String, Integer> entry : indexes.entrySet()) {
            Cell cell = row.getCell(entry.getValue(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            if (!getCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    java.util.Date date = cell.getDateCellValue();
                    yield java.time.LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault())
                            .toLocalDate().toString();
                }
                yield String.valueOf(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK -> "";
            default -> "";
        };
    }

    private static LocalDate parseDate(String rawValue) {
        String trimmed = rawValue.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("MM-dd-yyyy"));

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }

        throw new IllegalArgumentException(
                "Invalid expiry date '" + rawValue + "'. Use yyyy-MM-dd or dd/MM/yyyy format.");
    }

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }
}
