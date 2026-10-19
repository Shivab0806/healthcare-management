const state = {
    search: '',
    category: 'all',
    status: 'all',
    page: 0,
    size: 10,
};

let deleteTargetId = null;

const statusBadgeClasses = {
    OK: 'bg-emerald-50 text-emerald-700',
    LOW_STOCK: 'bg-amber-100 text-amber-800',
    EXPIRED: 'bg-red-100 text-red-700',
    EXPIRING_SOON: 'bg-sky-100 text-sky-700',
};

document.addEventListener('DOMContentLoaded', () => {
    showStoredToast();
    loadCategories();
    loadSummary();
    loadMedicines();

    document.getElementById('search-input').addEventListener('input', debounce((e) => {
        state.search = e.target.value;
        state.page = 0;
        loadMedicines();
    }, 350));

    document.getElementById('category-select').addEventListener('change', (e) => {
        state.category = e.target.value;
        state.page = 0;
        loadMedicines();
    });

    document.querySelectorAll('.status-tab').forEach(btn => {
        btn.addEventListener('click', () => {
            state.status = btn.dataset.status;
            state.page = 0;
            document.querySelectorAll('.status-tab').forEach(b => {
                b.classList.remove('bg-slate-800', 'text-white');
                b.classList.add('bg-white', 'border', 'border-slate-200', 'text-slate-700');
            });
            btn.classList.remove('bg-white', 'border', 'border-slate-200', 'text-slate-700');
            btn.classList.add('bg-slate-800', 'text-white');
            loadMedicines();
        });
    });

    document.getElementById('page-size-select').addEventListener('change', (e) => {
        state.size = parseInt(e.target.value, 10);
        state.page = 0;
        loadMedicines();
    });

    document.getElementById('prev-page').addEventListener('click', () => {
        if (state.page > 0) {
            state.page -= 1;
            loadMedicines();
        }
    });

    document.getElementById('next-page').addEventListener('click', () => {
        state.page += 1;
        loadMedicines();
    });

    document.getElementById('delete-cancel').addEventListener('click', closeDeleteModal);
    document.getElementById('delete-confirm').addEventListener('click', confirmDelete);

    document.getElementById('import-btn').addEventListener('click', () => {
        document.getElementById('import-modal').classList.remove('hidden');
        document.getElementById('import-modal').classList.add('flex');
    });
    document.getElementById('download-template-btn').addEventListener('click', downloadImportTemplate);
    document.getElementById('import-cancel').addEventListener('click', closeImportModal);
    document.getElementById('import-cancel-action').addEventListener('click', closeImportModal);
    document.getElementById('import-form').addEventListener('submit', handleImport);
});

function debounce(fn, wait) {
    let t;
    return (...args) => {
        clearTimeout(t);
        t = setTimeout(() => fn(...args), wait);
    };
}

async function loadCategories() {
    try {
        const res = await fetch('/api/medicines/categories');
        const categories = await res.json();
        const select = document.getElementById('category-select');
        categories.forEach(cat => {
            const opt = document.createElement('option');
            opt.value = cat;
            opt.textContent = cat;
            select.appendChild(opt);
        });
    } catch (err) {
        console.error('Failed to load categories', err);
    }
}

async function loadSummary() {
    try {
        const res = await fetch('/api/medicines/summary');
        const summary = await res.json();

        // Update filter tab badges
        document.getElementById('tab-low').textContent = summary.lowStock ?? 0;
        document.getElementById('tab-expired').textContent = summary.expired ?? 0;
        document.getElementById('tab-expiring').textContent = summary.expiringSoon ?? 0;

        // Also update the 4 top KPI cards if elements exist
        const totalEl = document.getElementById('totalMedicines');
        const lowEl = document.getElementById('lowStockCount');
        const outEl = document.getElementById('outOfStockCount');
        const expEl = document.getElementById('expiredCount');

        if (totalEl && summary.total !== undefined) totalEl.textContent = summary.total;
        if (lowEl) lowEl.textContent = summary.lowStock ?? 0;
        if (outEl) outEl.textContent = summary.outOfStock ?? summary.expiringSoon ?? 0;
        if (expEl) expEl.textContent = summary.expired ?? 0;

        const expiredAlert = document.getElementById('expired-alert');
        if (summary.expired > 0) {
            document.getElementById('expired-alert-title').textContent =
                `${summary.expired} medicine(s) have expired`;
            expiredAlert.classList.remove('hidden');
        } else {
            expiredAlert.classList.add('hidden');
        }

        const lowStockAlert = document.getElementById('low-stock-alert');
        if (summary.lowStock > 0) {
            document.getElementById('low-stock-alert-title').textContent =
                `${summary.lowStock} medicine(s) are running low`;
            lowStockAlert.classList.remove('hidden');
        } else {
            lowStockAlert.classList.add('hidden');
        }
    } catch (err) {
        console.error('Failed to load summary', err);
    }
}

async function loadMedicines() {
    const params = new URLSearchParams({
        search: state.search,
        category: state.category,
        status: state.status,
        page: state.page,
        size: state.size,
    });

    try {
        const res = await fetch(`/api/medicines?${params.toString()}`);
        if (!res.ok) throw new Error('Failed to load medicines');
        const data = await res.json();
        renderTable(data.content);
        renderPagination(data);
    } catch (err) {
        console.error(err);
    }
}

function renderTable(items) {
    const tbody = document.getElementById('medicine-table-body');
    const emptyState = document.getElementById('empty-state');
    tbody.innerHTML = '';

    if (!items || items.length === 0) {
        emptyState.classList.remove('hidden');
        return;
    }
    emptyState.classList.add('hidden');

    items.forEach(med => {
        const tr = document.createElement('tr');
        const stockClass = med.status === 'LOW_STOCK' || med.stock <= 0
            ? 'text-danger fw-bold'
            : 'fw-semibold text-dark';

        tr.innerHTML = `
            <td>
                <div class="fw-semibold text-dark">${escapeHtml(med.name)}</div>
            </td>
            <td class="text-secondary">${escapeHtml(med.company || '—')}</td>
            <td>
                <span class="badge bg-light text-secondary border fw-medium">${escapeHtml(med.category || 'General')}</span>
            </td>
            <td>
                <span class="batch-pill">${escapeHtml(med.batchNumber || '—')}</span>
            </td>
            <td class="text-end fw-semibold text-dark">₹${Number(med.price).toFixed(2)}</td>
            <td class="text-center ${stockClass}">${med.stock}</td>
            <td class="text-secondary">${formatDate(med.expiryDate)}</td>
            <td>
                <span class="status-badge ${statusBadgeClasses[med.status] || 'bg-light text-secondary'}">
                    ${escapeHtml(med.statusLabel || med.status)}
                </span>
            </td>
            <td class="text-end">
                <div class="d-inline-flex gap-1">
                    <a href="/medicines/edit/${med.id}" class="btn btn-sm btn-outline-secondary py-1 px-2" title="Edit Medicine">
                        <i class="bi bi-pencil-square me-1"></i>Edit
                    </a>
                    <button type="button" data-id="${med.id}" data-name="${escapeHtml(med.name)}" class="delete-btn btn btn-sm btn-outline-danger py-1 px-2" title="Delete Medicine">
                        <i class="bi bi-trash3"></i>
                    </button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });

    document.querySelectorAll('.delete-btn').forEach(btn => {
        btn.addEventListener('click', () => openDeleteModal(btn.dataset.id, btn.dataset.name));
    });
}

function renderPagination(page) {
    const total = page.totalElements ?? 0;
    const from = total === 0 ? 0 : page.number * page.size + 1;
    const to = Math.min(total, (page.number + 1) * page.size);

    document.getElementById('medicine-count').textContent = `${total} medicine${total === 1 ? '' : 's'}`;
    document.getElementById('pagination-info').textContent =
        total === 0 ? 'No medicines found' : `Showing ${from}–${to} of ${total} medicines`;
    document.getElementById('page-indicator').textContent = `Page ${page.number + 1} of ${Math.max(page.totalPages, 1)}`;

    // Fallback update for Total Medicines stat card when on "all" filter
    const totalEl = document.getElementById('totalMedicines');
    if (totalEl && state.status === 'all' && !state.search && state.category === 'all') {
        totalEl.textContent = total;
    }

    document.getElementById('prev-page').disabled = page.first;
    document.getElementById('next-page').disabled = page.last;
}

function openDeleteModal(id, name) {
    deleteTargetId = id;
    document.getElementById('delete-modal-text').textContent = `This will permanently remove "${name}" from the inventory.`;
    const modal = document.getElementById('delete-modal');
    modal.classList.remove('hidden');
    modal.classList.add('flex');
}

function closeDeleteModal() {
    deleteTargetId = null;
    const modal = document.getElementById('delete-modal');
    modal.classList.add('hidden');
    modal.classList.remove('flex');
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    const typeClass = {
        success: 'alert-success',
        error: 'alert-danger',
        warning: 'alert-warning',
        info: 'alert-info'
    }[type] || 'alert-info';

    toast.className = `alert ${typeClass} shadow-sm border-0 rounded-3 py-2 px-3 mb-0`;
    toast.setAttribute('role', 'alert');
    toast.style.pointerEvents = 'auto';
    toast.style.maxWidth = '100%';
    toast.innerHTML = `<div class="d-flex align-items-center justify-content-between gap-3"><span>${escapeHtml(message)}</span><button type="button" class="btn-close" aria-label="Close" onclick="this.parentElement.parentElement.remove();"></button></div>`;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(10px)';
        toast.style.transition = 'opacity 0.25s ease, transform 0.25s ease';
        setTimeout(() => toast.remove(), 280);
    }, 3200);
}

function showStoredToast() {
    try {
        const raw = sessionStorage.getItem('inventoryToast');
        if (!raw) return;
        const toast = JSON.parse(raw);
        if (toast && toast.message) {
            showToast(toast.message, toast.type || 'success');
        }
    } catch (e) {
        console.error('Could not display saved toast', e);
    } finally {
        sessionStorage.removeItem('inventoryToast');
    }
}

function closeImportModal() {
    const modal = document.getElementById('import-modal');
    modal.classList.add('hidden');
    modal.classList.remove('flex');
    document.getElementById('import-form').reset();
}

function downloadImportTemplate() {
    const csv = [
        'name,company,category,stripPrice,tabletsPerStrip,stripQuantity,looseTablets,batchNumber,expiryDate,lowStockThreshold',
        'Paracetamol 500mg,Sunrise Pharma,Analgesic,25.00,10,3,17,SP-2201,2027-12-31,20',
        'Amoxicillin 250mg,MediCore Labs,Antibiotic,93.00,15,2,5,ML-5590,2027-06-30,15',
        'Metformin 500mg,Sunrise Pharma,Antidiabetic,76.00,20,4,0,SP-2340,2028-05-15,25'
    ].join('\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'medicine-import-template.csv';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

async function handleImport(event) {
    event.preventDefault();
    const input = document.getElementById('import-file');
    const file = input.files[0];

    if (!file) {
        closeImportModal();
        showToast('Please choose a CSV or Excel file first.', 'warning');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);

    const submitBtn = document.getElementById('import-submit');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Importing...';

    try {
        const res = await fetch('/api/medicines/import', {
            method: 'POST',
            body: formData,
        });

        const result = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(result.message || 'Import failed. Please check the file format.');
        }

        closeImportModal();
        showToast(`${result.imported} medicine(s) imported successfully.`, 'success');
        loadSummary();
        loadMedicines();
    } catch (error) {
        console.error(error);
        closeImportModal();
        showToast(error.message || 'The selected file could not be imported.', 'error');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<i class="bi bi-cloud-arrow-up me-1"></i> Upload File';
    }
}

async function confirmDelete() {
    if (!deleteTargetId) return;
    try {
        const res = await fetch(`/api/medicines/${deleteTargetId}`, { method: 'DELETE' });
        if (!res.ok && res.status !== 204) throw new Error('Delete failed');
        closeDeleteModal();
        showToast('Medicine deleted successfully.', 'success');
        loadSummary();
        loadMedicines();
    } catch (err) {
        console.error(err);
        showToast('Could not delete this medicine. Please try again.', 'error');
    }
}

function formatDate(iso) {
    if (!iso) return '—';
    const d = new Date(iso + 'T00:00:00');
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str ?? '';
    return div.innerHTML;
}