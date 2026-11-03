(() => {
    'use strict';

    const cartElement = document.getElementById('shopping-cart');
    if (!cartElement) return;

    const checkout = document.getElementById('cart-checkout');
    const status = document.getElementById('cart-status');
    const errorBox = document.getElementById('cart-error');
    const retry = document.getElementById('cart-retry');
    const rows = new Map(Array.from(cartElement.querySelectorAll('[data-cart-item]'))
        .map(row => [row.dataset.cartItem, row]));
    const drafts = new Map();
    const money = new Intl.NumberFormat('vi-VN', {maximumFractionDigits: 2});
    let sequence = 0;
    let processing = false;
    let refreshing = false;
    let uncertain = true;
    let lastCart = null;

    const formatMoney = value => `${money.format(Number(value) || 0)} đ`;
    const keyFor = (itemId, field) => `${itemId}:${field}`;
    const isCart = value => value && Array.isArray(value.items) && typeof value.checkoutAllowed === 'boolean';
    const busy = () => processing || refreshing || drafts.size > 0;

    function showError(message) {
        errorBox.textContent = message || '';
        errorBox.hidden = !message;
    }

    function itemError(itemId, message) {
        const row = rows.get(itemId);
        if (!row) return;
        row.querySelector('[data-cart-item-error]').textContent = message || '';
        row.querySelector('[data-cart-quantity]').setAttribute('aria-invalid', message ? 'true' : 'false');
    }

    function updateState() {
        checkout.disabled = uncertain || busy() || checkout.dataset.serverAllowed !== 'true';
        cartElement.setAttribute('aria-busy', String(processing || refreshing));
        retry.hidden = !uncertain || refreshing || processing;
        status.textContent = refreshing ? 'Đang kiểm tra giỏ hàng…'
            : processing ? 'Đang lưu thay đổi…'
            : uncertain ? 'Chưa xác nhận được trạng thái giỏ hàng. Vui lòng tải lại trạng thái.'
            : drafts.size ? 'Vui lòng nhập số lượng hợp lệ; thay đổi sẽ được lưu tự động.'
            : 'Đã đồng bộ giỏ hàng. Chỉ các sản phẩm được chọn sẽ được thanh toán.';
        cartElement.querySelectorAll('[data-cart-navigation] button').forEach(button => {
            button.disabled = busy();
        });
    }

    function renderCart(cart) {
        lastCart = cart;
        const itemIds = new Set(cart.items.map(item => String(item.id)));
        rows.forEach((row, itemId) => {
            // A product removed in another request must not remain editable locally.
            if (!itemIds.has(itemId)) {
                for (const field of ['quantity', 'selected']) {
                    const key = keyFor(itemId, field);
                    clearTimeout(drafts.get(key)?.timer);
                    drafts.delete(key);
                }
                row.remove();
                rows.delete(itemId);
            }
        });
        cart.items.forEach(item => {
            const itemId = String(item.id);
            const row = rows.get(itemId);
            if (!row) return;
            const quantity = row.querySelector('[data-cart-quantity]');
            const quantityDraft = drafts.get(keyFor(itemId, 'quantity'));
            const selectionDraft = drafts.get(keyFor(itemId, 'selected'));
            quantity.value = quantityDraft ? quantityDraft.value : item.quantity;
            quantity.max = item.maxStock;
            // An overstocked saved line remains editable so its quantity can be reduced.
            quantity.disabled = Number(item.maxStock) <= 0;
            row.querySelector('[data-cart-selection]').checked = selectionDraft ? selectionDraft.value : item.isSelected === true;
            row.querySelector('[data-cart-price]').textContent = formatMoney(item.unitPrice);
            row.querySelector('[data-cart-line-total]').textContent = formatMoney(item.lineTotal);
            row.querySelector('[data-cart-availability]').textContent = item.availabilityMessage || '';
        });

        const list = document.getElementById('cart-selected-items');
        list.replaceChildren();
        cart.items.filter(item => item.isSelected === true).forEach(item => {
            const entry = document.createElement('li');
            entry.className = 'mb-2';
            const name = document.createElement('div');
            name.textContent = [item.productName, item.variantName].filter(Boolean).join(' — ');
            const details = document.createElement('div');
            details.className = 'd-flex justify-content-between gap-2';
            const quantity = document.createElement('span');
            quantity.textContent = `Số lượng: ${item.quantity}`;
            const total = document.createElement('span');
            total.textContent = formatMoney(item.lineTotal);
            details.append(quantity, total);
            entry.append(name, details);
            list.append(entry);
        });
        document.getElementById('cart-no-selection').hidden = cart.selectedItemCount > 0;
        document.getElementById('cart-subtotal').textContent = formatMoney(cart.subtotal);
        document.getElementById('cart-shipping').textContent = formatMoney(cart.shippingFee);
        document.getElementById('cart-total').textContent = formatMoney(cart.totalAmount);
        document.getElementById('cart-items').hidden = cart.items.length === 0;
        document.getElementById('cart-empty').hidden = cart.items.length !== 0;
        document.querySelectorAll('.cart-badge').forEach(badge => {
            badge.textContent = cart.totalQuantity;
        });
        checkout.dataset.serverAllowed = String(cart.checkoutAllowed);
        updateState();
    }

    async function request(url, fields) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);
        try {
            const headers = {'Accept': 'application/json'};
            const options = {credentials: 'same-origin', headers, signal: controller.signal, cache: 'no-store'};
            if (fields) {
                options.method = 'POST';
                options.body = new URLSearchParams(fields);
                if (cartElement.dataset.csrfHeader && cartElement.dataset.csrfToken) {
                    headers[cartElement.dataset.csrfHeader] = cartElement.dataset.csrfToken;
                }
            }
            const response = await fetch(url, options);
            const contentType = response.headers.get('content-type') || '';
            if (!contentType.includes('application/json')) {
                throw new Error('Không thể xác nhận giỏ hàng. Vui lòng kiểm tra kết nối hoặc đăng nhập lại.');
            }
            const payload = await response.json();
            return {ok: response.ok, payload};
        } finally {
            clearTimeout(timeout);
        }
    }

    async function readCart() {
        const response = await request(cartElement.dataset.apiUrl);
        if (!response.ok || !isCart(response.payload)) throw new Error('Không thể tải trạng thái giỏ hàng.');
        return response.payload;
    }

    async function pump() {
        if (processing || refreshing || uncertain) return;
        const action = Array.from(drafts.values()).filter(draft => draft.ready)
            .sort((left, right) => left.version - right.version)[0];
        if (!action) {
            updateState();
            return;
        }
        processing = true;
        updateState();
        let cart = null;
        let failure = '';
        try {
            const fields = {itemId: action.itemId, [action.field]: action.field === 'quantity' ? String(Number(action.value)) : action.value};
            const url = action.field === 'quantity' ? cartElement.dataset.quantityUrl : cartElement.dataset.selectionUrl;
            const response = await request(url, fields);
            if (response.ok && isCart(response.payload)) {
                cart = response.payload;
            } else {
                failure = response.payload.message || 'Không thể lưu thay đổi giỏ hàng.';
                if (isCart(response.payload.cart)) cart = response.payload.cart;
            }
        } catch (error) {
            failure = error.name === 'AbortError' ? 'Lưu giỏ hàng quá thời gian chờ. Đang kiểm tra lại trạng thái.'
                : error.message || 'Không thể lưu giỏ hàng. Vui lòng thử lại.';
        }
        // A failed connection can occur after the server has committed. Read again before enabling checkout.
        if (!cart) {
            try {
                cart = await readCart();
            } catch (error) {
                uncertain = true;
            }
        }
        const key = keyFor(action.itemId, action.field);
        if (drafts.get(key)?.version === action.version) {
            drafts.delete(key);
            if (failure) itemError(action.itemId, failure);
        }
        if (failure) showError(failure);
        if (cart) {
            uncertain = false;
            renderCart(cart);
        } else if (lastCart) {
            renderCart(lastCart);
        }
        processing = false;
        updateState();
        // Each request uses the latest draft. An older response never replaces a newer typed value.
        void pump();
    }

    function quantityError(input) {
        if (!input.value || !Number.isSafeInteger(Number(input.value)) || Number(input.value) < 1) {
            return 'Số lượng phải là số nguyên từ 1 trở lên.';
        }
        if (Number(input.value) > Number(input.max)) return `Số lượng tối đa hiện có: ${input.max}.`;
        return '';
    }

    function saveQuantity(itemId, immediate) {
        const input = rows.get(itemId).querySelector('[data-cart-quantity]');
        const key = keyFor(itemId, 'quantity');
        clearTimeout(drafts.get(key)?.timer);
        const action = {itemId, field: 'quantity', value: input.value, version: ++sequence, ready: false};
        drafts.set(key, action);
        itemError(itemId, '');
        showError('');
        updateState();
        const validate = () => {
            if (drafts.get(key) !== action) return;
            const message = quantityError(input);
            itemError(itemId, message);
            if (message) {
                if (immediate) {
                    drafts.delete(key);
                    if (lastCart) renderCart(lastCart);
                }
                updateState();
                return;
            }
            action.ready = true;
            void pump();
        };
        if (immediate) validate();
        else action.timer = setTimeout(validate, 250);
    }

    rows.forEach((row, itemId) => {
        const quantity = row.querySelector('[data-cart-quantity]');
        quantity.addEventListener('input', () => saveQuantity(itemId, false));
        quantity.addEventListener('change', () => saveQuantity(itemId, true));
        row.querySelector('[data-cart-selection]').addEventListener('change', event => {
            drafts.set(keyFor(itemId, 'selected'), {
                itemId, field: 'selected', value: event.target.checked, version: ++sequence, ready: true
            });
            showError('');
            itemError(itemId, '');
            updateState();
            void pump();
        });
    });

    cartElement.querySelectorAll('[data-cart-navigation]').forEach(form => {
        form.addEventListener('submit', event => {
            if (busy()) {
                event.preventDefault();
                showError('Vui lòng chờ lưu giỏ hàng hoặc sửa số lượng chưa hợp lệ trước khi tiếp tục.');
            } else if (form.hasAttribute('data-cart-clear') && !window.confirm('Bạn muốn xóa toàn bộ sản phẩm khỏi giỏ hàng?')) {
                event.preventDefault();
            }
        });
    });
    document.getElementById('cart-checkout-form').addEventListener('submit', event => {
        if (checkout.disabled || uncertain || busy()) event.preventDefault();
    });

    async function refresh() {
        if (processing || refreshing) return;
        refreshing = true;
        updateState();
        try {
            const cart = await readCart();
            uncertain = false;
            showError('');
            renderCart(cart);
        } catch (error) {
            uncertain = true;
            showError('Không thể tải giỏ hàng. Vui lòng kiểm tra kết nối hoặc đăng nhập lại, rồi tải lại trạng thái.');
        } finally {
            refreshing = false;
            updateState();
        }
        void pump();
    }

    retry.addEventListener('click', () => void refresh());
    void refresh();
})();
