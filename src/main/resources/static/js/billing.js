// src/main/resources/static/js/billing.js
let allMedicines = [];
let cart = []; // [{ medicineId, name, batchNumber, unitPrice, maxStock, quantity, quantityType, tabletsPerStrip }]
let billHistory = [];
let invoiceModal = null;
let currentInvoice = null;
let historyPage = 0;
let historyPageSize = 10;
let historyTotalPages = 1;

document.addEventListener('DOMContentLoaded', () => {
    invoiceModal = new bootstrap.Modal(document.getElementById('printableInvoiceModal'));
    loadMedicinesForPOS();

    document.getElementById('posSearchInput').addEventListener('input', renderMedicineCatalog);
    document.getElementById('discountPercent').addEventListener('input', updateCartTotals);
    document.getElementById('taxPercent').addEventListener('input', updateCartTotals);
    document.getElementById('clearCartBtn').addEventListener('click', clearCart);
    document.getElementById('generateBillBtn').addEventListener('click', submitBill);
    document.getElementById('billHistoryToggleBtn').addEventListener('click', () => {
        const panel = document.getElementById('billHistoryPanel');
        const hidden = panel.classList.contains('d-none');
        panel.classList.toggle('d-none', !hidden);
        if (hidden) {
            loadBillHistory(0);
        }
    });
    document.getElementById('clearHistoryFiltersBtn').addEventListener('click', () => {
        document.getElementById('historySearchInput').value = '';
        document.getElementById('historyFromDate').value = '';
        document.getElementById('historyToDate').value = '';
        document.getElementById('historyPageSize').value = '10';
        historyPageSize = 10;
        historyPage = 0;
        loadBillHistory(0);
    });
    document.getElementById('refreshHistoryBtn').addEventListener('click', () => loadBillHistory(0));
    document.getElementById('historySearchInput').addEventListener('input', () => {
        historyPage = 0;
        loadBillHistory(0);
    });
    document.getElementById('historyFromDate').addEventListener('change', () => {
        historyPage = 0;
        loadBillHistory(0);
    });
    document.getElementById('historyToDate').addEventListener('change', () => {
        historyPage = 0;
        loadBillHistory(0);
    });
    document.getElementById('historyPageSize').addEventListener('change', () => {
        historyPageSize = Number(document.getElementById('historyPageSize').value || 10);
        historyPage = 0;
        loadBillHistory(0);
    });
    document.getElementById('historyPrevBtn').addEventListener('click', () => {
        if (historyPage > 0) loadBillHistory(historyPage - 1);
    });
    document.getElementById('historyNextBtn').addEventListener('click', () => {
        if (historyPage + 1 < historyTotalPages) loadBillHistory(historyPage + 1);
    });

    document.getElementById('printInvoiceBtn').addEventListener('click', () => window.print());
    document.getElementById('downloadPdfBtn').addEventListener('click', downloadInvoicePDF);
});

async function loadMedicinesForPOS() {
    try {
        const res = await fetch('/api/medicines?size=500');
        const data = await res.json();
        allMedicines = Array.isArray(data) ? data : (data.content || []);
        renderMedicineCatalog();
    } catch (err) {
        showAlert('Failed to load medicines catalog: ' + err.message, 'danger');
    }
}

function renderMedicineCatalog() {
    const query = document.getElementById('posSearchInput').value.toLowerCase().trim();
    const listEl = document.getElementById('posMedicineList');

    const filtered = allMedicines.filter(m =>
        (m.name && m.name.toLowerCase().includes(query)) ||
        (m.batchNumber && m.batchNumber.toLowerCase().includes(query)) ||
        (m.category && m.category.toLowerCase().includes(query))
    );

    document.getElementById('catalogCount').textContent = `${filtered.length} Items`;

    if (filtered.length === 0) {
        listEl.innerHTML = `<div class="text-center text-muted py-4">No matching medicines found.</div>`;
        return;
    }

    listEl.innerHTML = filtered.map(m => {
        const stock = Number(m.stock ?? m.quantity ?? 0);
        const status = (m.status || '').toString();
        const outOfStock = stock <= 0 || status === 'EXPIRED';
        return `
        <div class="medicine-item-card d-flex justify-content-between align-items-center ${outOfStock ? 'opacity-50' : ''}"
             onclick="${outOfStock ? '' : `addToCart(${m.id})`}">
            <div>
                <div class="fw-semibold">${escapeHtml(m.name)}</div>
                <div class="small text-muted">
                    Batch: ${escapeHtml(m.batchNumber || 'N/A')} | Exp: ${escapeHtml(m.expiryDate || 'N/A')}
                </div>
            </div>
            <div class="text-end">
                <div class="fw-bold" style="color: var(--primary);">₹${Number(m.price).toFixed(2)}</div>
                <span class="badge-status badge-${status || 'IN_STOCK'}">Stock: ${stock}</span>
            </div>
        </div>`;
    }).join('');
}

function getEffectiveTabletQuantity(item) {
    if (!item) return 0;
    const stripSize = Number(item.tabletsPerStrip || 1);
    return item.quantityType === 'STRIP' ? Number(item.quantity || 0) * stripSize : Number(item.quantity || 0);
}

function addToCart(medicineId) {
    const med = allMedicines.find(m => m.id === medicineId);
    const stock = Number(med?.stock ?? med?.quantity ?? 0);
    if (!med || stock <= 0) return;

    const existing = cart.find(item => item.medicineId === medicineId);
    const stripSize = Number(med.tabletsPerStrip || 10);
    if (existing) {
        const currentTablets = getEffectiveTabletQuantity(existing);
        if (currentTablets + (existing.quantityType === 'STRIP' ? stripSize : 1) > stock) {
            showAlert(`Cannot exceed available stock (${stock}) for ${med.name}`, 'warning');
            return;
        }
        existing.quantity += 1;
    } else {
        cart.push({
            medicineId: med.id,
            name: med.name,
            batchNumber: med.batchNumber || 'N/A',
            unitPrice: Number(med.price),
            maxStock: stock,
            quantity: 1,
            quantityType: 'TABLET',
            tabletsPerStrip: stripSize
        });
    }
    renderCart();
}

function updateQuantity(medicineId, newQty) {
    const item = cart.find(i => i.medicineId === medicineId);
    if (!item) return;

    const parsed = parseInt(newQty, 10);
    if (isNaN(parsed) || parsed <= 0) {
        removeFromCart(medicineId);
        return;
    }

    const maxAllowed = item.quantityType === 'STRIP'
        ? Math.max(1, Math.floor(item.maxStock / Math.max(1, Number(item.tabletsPerStrip || 1))))
        : item.maxStock;

    if (parsed > maxAllowed) {
        showAlert(`Only ${maxAllowed} ${item.quantityType === 'STRIP' ? 'strips' : 'tablets'} available in stock for ${item.name}`, 'warning');
        item.quantity = maxAllowed;
    } else {
        item.quantity = parsed;
    }
    renderCart();
}

function updateQuantityType(medicineId, quantityType) {
    const item = cart.find(i => i.medicineId === medicineId);
    if (!item) return;
    item.quantityType = quantityType;
    const maxAllowed = quantityType === 'STRIP'
        ? Math.max(1, Math.floor(item.maxStock / Math.max(1, Number(item.tabletsPerStrip || 1))))
        : item.maxStock;
    if (item.quantity > maxAllowed) {
        item.quantity = maxAllowed;
    }
    renderCart();
}

function removeFromCart(medicineId) {
    cart = cart.filter(i => i.medicineId !== medicineId);
    renderCart();
}

function clearCart() {
    cart = [];
    document.getElementById('customerName').value = '';
    document.getElementById('customerPhone').value = '';
    document.getElementById('discountPercent').value = '0';
    renderCart();
}

function renderCart() {
    const tbody = document.getElementById('cartTableBody');
    if (cart.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center text-muted py-4">
                    <i class="bi bi-cart3 fs-3 d-block mb-1"></i>
                    No medicines added yet. Click any medicine on the left to add.
                </td>
            </tr>`;
        updateCartTotals();
        return;
    }

    tbody.innerHTML = cart.map(item => {
        const tabletQty = getEffectiveTabletQuantity(item);
        const lineTotal = item.unitPrice * tabletQty;
        const maxAllowed = item.quantityType === 'STRIP'
            ? Math.max(1, Math.floor(item.maxStock / Math.max(1, Number(item.tabletsPerStrip || 1))))
            : item.maxStock;
        return `
        <tr>
            <td>
                <div class="fw-semibold">${escapeHtml(item.name)}</div>
                <small class="text-muted">Batch: ${escapeHtml(item.batchNumber)}</small>
            </td>
            <td class="text-end">₹${item.unitPrice.toFixed(2)}</td>
            <td class="text-center">
                <div class="d-flex gap-2 align-items-center justify-content-center">
                    <select class="form-select form-select-sm w-auto" onchange="updateQuantityType(${item.medicineId}, this.value)">
                        <option value="TABLET" ${item.quantityType === 'TABLET' ? 'selected' : ''}>Tablet(s)</option>
                        <option value="STRIP" ${item.quantityType === 'STRIP' ? 'selected' : ''}>Strip(s)</option>
                    </select>
                    <div class="input-group input-group-sm" style="width: 120px;">
                        <button class="btn btn-outline-secondary" type="button" onclick="updateQuantity(${item.medicineId}, ${Math.max(1, item.quantity - 1)})">-</button>
                        <input type="number" class="form-control text-center" value="${item.quantity}" min="1" max="${maxAllowed}"
                               onchange="updateQuantity(${item.medicineId}, this.value)">
                        <button class="btn btn-outline-secondary" type="button" onclick="updateQuantity(${item.medicineId}, ${item.quantity + 1})">+</button>
                    </div>
                </div>
            </td>
            <td class="text-end fw-semibold">₹${lineTotal.toFixed(2)}</td>
            <td class="text-center">
                <button class="btn btn-sm text-danger" onclick="removeFromCart(${item.medicineId})">
                    <i class="bi bi-x-lg"></i>
                </button>
            </td>
        </tr>`;
    }).join('');

    updateCartTotals();
}

function updateCartTotals() {
    const subTotal = cart.reduce((sum, item) => sum + (item.unitPrice * getEffectiveTabletQuantity(item)), 0);
    const discPct = Math.min(100, Math.max(0, parseFloat(document.getElementById('discountPercent').value) || 0));
    const taxPct = Math.min(100, Math.max(0, parseFloat(document.getElementById('taxPercent').value) || 0));

    const discAmt = (subTotal * discPct) / 100;
    const taxable = subTotal - discAmt;
    const taxAmt = (taxable * taxPct) / 100;
    const grandTotal = taxable + taxAmt;

    document.getElementById('calcSubTotal').textContent = `₹${subTotal.toFixed(2)}`;
    document.getElementById('calcDiscountAmt').textContent = `-₹${discAmt.toFixed(2)}`;
    document.getElementById('calcTaxAmt').textContent = `+₹${taxAmt.toFixed(2)}`;
    document.getElementById('calcGrandTotal').textContent = `₹${grandTotal.toFixed(2)}`;
}

async function submitBill() {
    const customerName = document.getElementById('customerName').value.trim();
    if (!customerName) {
        showAlert('Please enter the Patient / Customer Name.', 'warning');
        document.getElementById('customerName').focus();
        return;
    }
    if (cart.length === 0) {
        showAlert('Please add at least one medicine to the bill.', 'warning');
        return;
    }

    const payload = {
        customerName,
        customerPhone: document.getElementById('customerPhone').value.trim(),
        paymentMode: document.getElementById('paymentMode').value,
        discountPercent: parseFloat(document.getElementById('discountPercent').value) || 0,
        taxPercent: parseFloat(document.getElementById('taxPercent').value) || 0,
        items: cart.map(i => ({
            medicineId: i.medicineId,
            quantity: i.quantity,
            quantityType: i.quantityType || 'TABLET',
            tabletsPerStrip: Number(i.tabletsPerStrip || 1)
        }))
    };

    try {
        const res = await fetch('/api/bills', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Failed to generate bill');
        }

        const savedBill = await res.json();
        showAlert(`Invoice ${savedBill.invoiceNumber} generated successfully!`, 'success');
        clearCart();
        loadMedicinesForPOS();
        loadBillHistory();
        openInvoiceModal(savedBill);
    } catch (err) {
        showAlert(err.message, 'danger');
    }
}

async function loadBillHistory(page = 0) {
    try {
        const search = document.getElementById('historySearchInput')?.value?.trim() || '';
        const fromDate = document.getElementById('historyFromDate')?.value || '';
        const toDate = document.getElementById('historyToDate')?.value || '';

        const params = new URLSearchParams({
            page: String(page),
            size: String(historyPageSize)
        });

        if (search) params.set('search', search);
        if (fromDate) params.set('fromDate', fromDate);
        if (toDate) params.set('toDate', toDate);

        const res = await fetch(`/api/bills?${params.toString()}`);
        const payload = await res.json();
        billHistory = Array.isArray(payload.content) ? payload.content : [];
        historyPage = Number(payload.number || 0);
        historyTotalPages = Number(payload.totalPages || 1);
        renderHistoryPagination();
        renderBillHistory();
    } catch (err) {
        console.error('Error loading history:', err);
    }
}

function renderHistoryPagination() {
    const pageInfo = document.getElementById('historyPageInfo');
    const prevBtn = document.getElementById('historyPrevBtn');
    const nextBtn = document.getElementById('historyNextBtn');

    if (pageInfo) {
        pageInfo.textContent = `Page ${historyPage + 1} of ${Math.max(historyTotalPages, 1)}`;
    }
    if (prevBtn) prevBtn.disabled = historyPage <= 0;
    if (nextBtn) nextBtn.disabled = historyPage >= historyTotalPages - 1;
}

function renderBillHistory() {
    const tbody = document.getElementById('billHistoryBody');

    if (!billHistory || billHistory.length === 0) {
        tbody.innerHTML = `<tr><td colspan="10" class="text-center text-muted py-4">No saved invoices found for this page.</td></tr>`;
        return;
    }

    tbody.innerHTML = billHistory.map(b => `
        <tr>
            <td class="fw-semibold text-primary">${escapeHtml(b.invoiceNumber || '#' + b.id)}</td>
            <td>${formatDateTime(b.createdAt)}</td>
            <td class="fw-medium">${escapeHtml(b.customerName)}</td>
            <td>${escapeHtml(b.customerPhone || '-')}</td>
            <td><span class="badge bg-light text-dark border">${escapeHtml(b.paymentMode || 'CASH')}</span></td>
            <td class="text-end">₹${Number(b.subTotal || 0).toFixed(2)}</td>
            <td class="text-end text-success">-₹${Number(b.discountAmount || 0).toFixed(2)}</td>
            <td class="text-end">₹${Number(b.taxAmount || 0).toFixed(2)}</td>
            <td class="text-end fw-bold">₹${Number(b.grandTotal || 0).toFixed(2)}</td>
            <td class="text-center">
                <button class="btn btn-sm btn-outline-dark" onclick="viewBillById(${b.id})" title="View / Print / PDF">
                    <i class="bi bi-printer me-1"></i> Invoice
                </button>
            </td>
        </tr>
    `).join('');
}

function viewBillById(id) {
    const bill = billHistory.find(b => b.id === id);
    if (bill) openInvoiceModal(bill);
}

function buildInvoiceDocumentHtml(bill) {
    const items = bill.items || [];
    const invoiceNumber = bill.invoiceNumber || `INV-${bill.id}`;
    const customerName = bill.customerName || 'Walk-in Customer';
    const phone = bill.customerPhone || 'N/A';
    const paymentMode = bill.paymentMode || 'CASH';
    const subTotal = Number(bill.subTotal || 0);
    const discountAmount = Number(bill.discountAmount || 0);
    const taxAmount = Number(bill.taxAmount || 0);
    const grandTotal = Number(bill.grandTotal || 0);
    const discountPercent = Number(bill.discountPercent || 0);
    const taxPercent = Number(bill.taxPercent || 0);

    return `
        <div class="invoice-box m-3" style="background:#fff; padding:2rem; border:1px solid #e2e8f0; border-radius:0.5rem; color:#0f172a; font-family:Inter, sans-serif; width:100%; box-sizing:border-box;">
            <div style="display:flex; justify-content:space-between; align-items:flex-start; border-bottom:1px solid #e2e8f0; padding-bottom:1rem; margin-bottom:1.5rem; gap:1rem;">
                <div>
                    <h4 id="invModalTitle" style="font-size:2.1rem; font-weight:800; letter-spacing:0.02em; margin:0 0 0.3rem 0; color:#0f766e;">MEDINVENTORY PHARMACY</h4>
                    <p id="invModalMeta" style="margin:0.2rem 0; color:#64748b; font-size:0.9rem;">Healthcare &amp; Surgical Supplies | GSTIN: 36AABCU9603R1ZM</p>
                    <p id="invModalContact" style="margin:0.2rem 0; color:#64748b; font-size:0.9rem;">Phone: +91 98765 43210 | support@medinventory.com</p>
                </div>
                <div style="text-align:right; min-width:220px;">
                    <h5 id="invModalNumber" style="font-size:1.8rem; font-weight:800; margin:0 0 0.4rem 0; color:#0f172a;">${escapeHtml(invoiceNumber)}</h5>
                    <p id="invModalDate" style="margin:0.2rem 0; color:#64748b; font-size:0.9rem;">Date: ${escapeHtml(formatDateTime(bill.createdAt))}</p>
                    <span id="invModalPayment" style="display:inline-block; margin-top:0.35rem; padding:0.28rem 0.8rem; border-radius:999px; background:#1f2937; color:#fff; font-size:0.75rem; font-weight:700; text-transform:uppercase;">${escapeHtml(paymentMode)}</span>
                </div>
            </div>

            <div style="margin-bottom:1.5rem;">
                <span style="display:block; font-size:0.75rem; text-transform:uppercase; color:#64748b; font-weight:700; letter-spacing:0.08em; margin-bottom:0.35rem;">Billed To:</span>
                <h6 id="invModalCustomer" style="margin:0; font-size:1.3rem; font-weight:800; color:#0f172a;">${escapeHtml(customerName)}</h6>
                <p id="invModalPhone" style="margin:0.25rem 0 0 0; color:#64748b; font-size:0.9rem;">Phone: ${escapeHtml(phone)}</p>
            </div>

            <table style="width:100%; border-collapse:collapse; border:1px solid #dfe7ee; margin-bottom:1.25rem; font-size:1rem;">
                <thead>
                    <tr style="background:#f1f5f9;">
                        <th style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; width:40px; text-align:left; font-weight:700; color:#475569;">#</th>
                        <th style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; text-align:left; font-weight:700; color:#475569;">Medicine Description</th>
                        <th style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; text-align:left; font-weight:700; color:#475569;">Batch</th>
                        <th style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; text-align:center; font-weight:700; color:#475569;">Qty</th>
                        <th style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; text-align:right; font-weight:700; color:#475569;">Unit Price</th>
                        <th style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; text-align:right; font-weight:700; color:#475569;">Total</th>
                    </tr>
                </thead>
                <tbody id="invModalItems">
                    ${items.map((item, idx) => `
                        <tr>
                            <td style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem;">${idx + 1}</td>
                            <td style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem;">${escapeHtml(item.medicineName)}</td>
                            <td style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem;">${escapeHtml(item.batchNumber || '-')}</td>
                            <td style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; text-align:center;">${item.quantity}</td>
                            <td style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; text-align:right;">₹${Number(item.unitPrice).toFixed(2)}</td>
                            <td style="border:1px solid #dfe7ee; padding:0.75rem 0.7rem; text-align:right; font-weight:700;">₹${Number(item.lineTotal).toFixed(2)}</td>
                        </tr>
                    `).join('') || '<tr><td colspan="6" style="padding:0.75rem; text-align:center; color:#64748b;">No items in this invoice.</td></tr>'}
                </tbody>
            </table>

            <div style="display:flex; justify-content:flex-end;">
                <div style="width:290px; font-size:1rem; color:#0f172a;">
                    <div style="display:flex; justify-content:space-between; padding:0.22rem 0; color:#475569;">
                        <span>Subtotal:</span>
                        <span id="invModalSubTotal">₹${subTotal.toFixed(2)}</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; padding:0.22rem 0; color:#475569;">
                        <span>Discount (<span id="invModalDiscPct">${discountPercent}</span>%):</span>
                        <span id="invModalDiscAmt">-₹${discountAmount.toFixed(2)}</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; padding:0.22rem 0; color:#475569;">
                        <span>Tax / GST (<span id="invModalTaxPct">${taxPercent}</span>%):</span>
                        <span id="invModalTaxAmt">+₹${taxAmount.toFixed(2)}</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center; padding-top:0.6rem; margin-top:0.5rem; border-top:1px solid #dfe7ee; font-size:1.15rem; font-weight:800;">
                        <span>Grand Total:</span>
                        <span id="invModalGrandTotal">₹${grandTotal.toFixed(2)}</span>
                    </div>
                </div>
            </div>

            <div style="margin-top:2rem; padding-top:1rem; border-top:1px solid #e2e8f0; text-align:center; color:#64748b; font-size:0.92rem; font-style:italic;">
                Thank you for your visit! Get well soon. Goods once sold will not be taken back without original invoice.
            </div>
        </div>
    `;
}

function openInvoiceModal(bill) {
    currentInvoice = bill;
    const modal = document.getElementById('invoicePdfArea');
    modal.innerHTML = buildInvoiceDocumentHtml(bill);
    invoiceModal.show();
}

async function downloadInvoicePDF() {
    if (!currentInvoice || !currentInvoice.id) {
        showAlert('No invoice available to download.', 'warning');
        return;
    }

    try {
        const response = await fetch(`/api/bills/${currentInvoice.id}/pdf`);
        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(errorText || 'Unable to download PDF.');
        }

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${currentInvoice.invoiceNumber || 'invoice'}.pdf`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    } catch (err) {
        console.error(err);
        showAlert(err.message || 'Invoice PDF could not be downloaded.', 'danger');
    }
}

function formatDateTime(dtStr) {
    if (!dtStr) return '-';
    const d = new Date(dtStr);
    return d.toLocaleString('en-IN', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

function showAlert(msg, type = 'info') {
    const container = document.getElementById('alertContainer');
    container.innerHTML = `
        <div class="alert alert-${type} alert-dismissible fade show shadow-sm" role="alert">
            ${escapeHtml(msg)}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>`;
    setTimeout(() => { container.innerHTML = ''; }, 5000);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/[&<>"']/g, s => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[s]));
}