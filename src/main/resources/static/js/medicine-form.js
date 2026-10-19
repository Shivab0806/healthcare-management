document.addEventListener('DOMContentLoaded', () => {
    const idField = document.getElementById('medicine-id');
    const id = idField.value;

    bindStockModeEvents();

    if (typeof MODE !== 'undefined' && MODE === 'edit' && id) {
        document.getElementById('page-title').textContent = 'Edit Medicine';
        document.getElementById('page-subtitle').textContent = "Update this medicine's details";
        document.getElementById('submit-btn').textContent = 'Save Changes';
        loadMedicine(id);
    }

    document.getElementById('medicine-form').addEventListener('submit', handleSubmit);
});

function bindStockModeEvents() {
    const selectors = document.querySelectorAll('input[name="stockMode"]');
    const stockInput = document.getElementById('stock');
    const stripPriceInput = document.getElementById('stripPrice');
    const priceInput = document.getElementById('price');
    const tabletsPerStripInput = document.getElementById('tabletsPerStrip');
    const stripQuantityInput = document.getElementById('stripQuantity');
    const extraSingleTabletsInput = document.getElementById('extraSingleTablets');
    const singleGroup = document.getElementById('single-stock-group');
    const stripGroup = document.getElementById('strip-stock-group');
    const calculatedStockLabel = document.getElementById('calculated-stock');
    const unitPriceDisplay = document.getElementById('unit-price-display');

    const syncUnitPrice = () => {
        const tabletsPerStrip = Number(tabletsPerStripInput.value) || 0;
        const stripPrice = Number(stripPriceInput.value) || 0;
        const unitPrice = tabletsPerStrip > 0 ? stripPrice / tabletsPerStrip : stripPrice;
        const roundedPrice = Number.isFinite(unitPrice) ? unitPrice : 0;
        priceInput.value = roundedPrice.toFixed(2);
        unitPriceDisplay.textContent = `₹${roundedPrice.toFixed(2)}`;
    };

    const syncStripStock = () => {
        const tabletsPerStrip = Number(tabletsPerStripInput.value) || 0;
        const stripQuantity = Number(stripQuantityInput.value) || 0;
        const extraSingleTablets = Number(extraSingleTabletsInput.value) || 0;
        const stripTotal = tabletsPerStrip > 0 ? tabletsPerStrip * stripQuantity : 0;
        const total = stripTotal + extraSingleTablets;

        if (document.querySelector('input[name="stockMode"]:checked')?.value !== 'single') {
            stockInput.value = total;
            calculatedStockLabel.textContent = total;
        }
    };

    const updateStockMode = () => {
        const mode = document.querySelector('input[name="stockMode"]:checked')?.value || 'single';
        const isSingleMode = mode === 'single';
        const isMixedMode = mode === 'mixed';

        singleGroup.classList.toggle('hidden', !isSingleMode);
        stripGroup.classList.toggle('hidden', isSingleMode);

        if (isSingleMode) {
            stockInput.setAttribute('required', 'required');
            stockInput.value = stockInput.value || 0;
            calculatedStockLabel.textContent = stockInput.value || 0;
            return;
        }

        stockInput.removeAttribute('required');
        syncStripStock();

        if (isMixedMode) {
            stockInput.value = Number(stockInput.value || 0);
        }
    };

    selectors.forEach((radio) => radio.addEventListener('change', updateStockMode));
    tabletsPerStripInput.addEventListener('input', () => {
        syncStripStock();
        syncUnitPrice();
    });
    stripQuantityInput.addEventListener('input', syncStripStock);
    extraSingleTabletsInput.addEventListener('input', syncStripStock);
    stripPriceInput.addEventListener('input', syncUnitPrice);
    stockInput.addEventListener('input', () => {
        const mode = document.querySelector('input[name="stockMode"]:checked')?.value || 'single';
        if (mode === 'single') {
            calculatedStockLabel.textContent = stockInput.value || 0;
        }
    });

    syncUnitPrice();
    updateStockMode();
}

async function loadMedicine(id) {
    try {
        const res = await fetch(`/api/medicines/${id}`);
        if (!res.ok) throw new Error('Not found');
        const med = await res.json();

        document.getElementById('name').value = med.name;
        document.getElementById('company').value = med.company;
        document.getElementById('category').value = med.category;
        document.getElementById('stripPrice').value = med.price;
        document.getElementById('price').value = med.price;
        document.getElementById('stock').value = med.stock;
        document.getElementById('lowStockThreshold').value = med.lowStockThreshold;
        document.getElementById('batchNumber').value = med.batchNumber;
        document.getElementById('expiryDate').value = med.expiryDate;
        const unitPriceDisplay = document.getElementById('unit-price-display');
        if (unitPriceDisplay) {
            unitPriceDisplay.textContent = `₹${Number(med.price).toFixed(2)}`;
        }
    } catch (err) {
        showError('Could not load this medicine.');
    }
}

async function handleSubmit(e) {
    e.preventDefault();
    hideError();

    const selectedMode = document.querySelector('input[name="stockMode"]:checked')?.value || 'single';
    const stockInput = document.getElementById('stock');
    const stripPrice = Number(document.getElementById('stripPrice').value || 0);
    const tabletsPerStrip = Number(document.getElementById('tabletsPerStrip').value || 0);
    const computedUnitPrice = tabletsPerStrip > 0 ? stripPrice / tabletsPerStrip : stripPrice;
    document.getElementById('price').value = computedUnitPrice.toFixed(2);

    let totalStock = Number(stockInput.value || 0);

    if (selectedMode === 'strip' || selectedMode === 'mixed') {
        const tabletsPerStrip = Number(document.getElementById('tabletsPerStrip').value || 0);
        const stripQuantity = Number(document.getElementById('stripQuantity').value || 0);
        const extraSingleTablets = Number(document.getElementById('extraSingleTablets').value || 0);

        if (selectedMode === 'strip' && (tabletsPerStrip <= 0 || stripQuantity < 0)) {
            showError('Please enter a valid tablets-per-strip value and strip quantity.');
            return;
        }

        if (selectedMode === 'mixed' && (tabletsPerStrip <= 0 || stripQuantity < 0 || extraSingleTablets < 0)) {
            showError('Please enter valid strip and loose tablet values for the mixed stock entry.');
            return;
        }

        const stripTotal = tabletsPerStrip > 0 ? tabletsPerStrip * stripQuantity : 0;
        totalStock = stripTotal + extraSingleTablets;
        stockInput.value = totalStock;
    }

    if (totalStock < 0) {
        showError('Stock cannot be negative.');
        return;
    }

    const id = document.getElementById('medicine-id').value;
    const payload = {
        name: document.getElementById('name').value.trim(),
        company: document.getElementById('company').value.trim(),
        category: document.getElementById('category').value.trim(),
        price: parseFloat(document.getElementById('price').value),
        stock: totalStock,
        lowStockThreshold: parseInt(document.getElementById('lowStockThreshold').value, 10),
        batchNumber: document.getElementById('batchNumber').value.trim(),
        expiryDate: document.getElementById('expiryDate').value,
    };

    const isEdit = typeof MODE !== 'undefined' && MODE === 'edit' && id;
    const url = isEdit ? `/api/medicines/${id}` : '/api/medicines';
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            const message = err.fieldErrors
                ? Object.values(err.fieldErrors).join(', ')
                : (err.message || 'Something went wrong. Please check the form and try again.');
            showError(message);
            return;
        }

        const successMessage = isEdit ? 'Medicine updated successfully.' : 'Medicine added successfully.';
        sessionStorage.setItem('inventoryToast', JSON.stringify({ message: successMessage, type: 'success' }));
        window.location.href = '/medicines';
    } catch (err) {
        showError('Could not reach the server. Please try again.');
    }
}

function showError(message) {
    const box = document.getElementById('form-error');
    box.textContent = message;
    box.classList.remove('hidden');
}

function hideError() {
    document.getElementById('form-error').classList.add('hidden');
}
