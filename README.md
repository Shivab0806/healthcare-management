# Healthcare Medicine Inventory

A Spring Boot 3.3 / Java 21 project: a REST API for CRUD + pagination over a
medicine inventory, with a Thymeleaf UI that calls that same API via `fetch()`.

## Stack
- Java 21, Spring Boot 3.3.5
- Spring Web, Spring Data JPA, Spring Validation
- Thymeleaf + Tailwind CSS (CDN) + vanilla JS
## Run it
```bash
mvn spring-boot:run
```
Then open:
- UI:  http://localhost:8080/medicines

## Project layout
```
src/main/java/com/healthcare/inventory
├── model/              Medicine entity, MedicineStatus enum
├── dto/                MedicineRequest / MedicineResponse / SummaryResponse
├── repository/         MedicineRepository + JPA Specifications (search/filter)
├── mapper/             Entity <-> DTO mapping + status calculation
├── service/            MedicineService + impl (business logic)
├── controller/
│   ├── MedicineRestController   -> /api/medicines/**  (JSON)
│   └── MedicineViewController   -> /medicines/**      (Thymeleaf pages)
└── exception/           ResourceNotFoundException + @RestControllerAdvice

src/main/resources
├── templates/           inventory.html, medicine-form.html
├── static/js/           inventory.js, medicine-form.js (fetch calls to the API)
└── data.sql             12 seeded demo medicines
```

## REST API

| Method | Endpoint                     | Description                                   |
|--------|-------------------------------|------------------------------------------------|
| GET    | `/api/medicines`              | Paginated list. Query params: `search`, `category`, `status` (`all`/`low-stock`/`expired`/`expiring-soon`), `page`, `size`, `sortBy`, `direction` |
| GET    | `/api/medicines/{id}`         | Get one medicine                               |
| POST   | `/api/medicines`               | Create a medicine                              |
| PUT    | `/api/medicines/{id}`         | Update a medicine                              |
| DELETE | `/api/medicines/{id}`         | Delete a medicine                              |
| GET    | `/api/medicines/summary`      | Counts for the alert banners + filter tabs     |
| GET    | `/api/medicines/categories`   | Distinct category list for the dropdown        |

Example request body (POST/PUT):
```json
{
  "name": "Paracetamol 500mg",
  "company": "Sunrise Pharma",
  "category": "Analgesic",
  "price": 2.50,
  "stock": 450,
  "lowStockThreshold": 50,
  "batchNumber": "SP-2201",
  "expiryDate": "2027-06-30"
}
```

Example paginated response (`GET /api/medicines?page=0&size=10`):
```json
{
  "content": [ { "id": 1, "name": "Paracetamol 500mg", "...": "...", "status": "OK", "statusLabel": "OK" } ],
  "totalElements": 12,
  "totalPages": 2,
  "number": 0,
  "size": 10,
  "first": true,
  "last": false
}
```

## Status rules
A medicine's status is computed on every read (never stored), against the
current date:
- **Expired** — expiry date is in the past
- **Low stock** — stock ≤ its low-stock threshold
- **Expiring soon** — expires within 30 days, not already expired
- **OK** — none of the above

When several apply, the badge shown follows this priority:
Expired > Low stock > Expiring soon > OK. The filter tabs count each
condition independently (a medicine can count toward more than one tab).
