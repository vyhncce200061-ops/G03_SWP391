(() => {
    'use strict';
    const form = document.getElementById('checkout-form');
    if (!form) return;
    const voucher = form.querySelector('[name="voucherCode"]');
    const submit = document.getElementById('place-order');
    const status = document.getElementById('quote-status');
    const message = document.getElementById('quote-message');
    const formatMoney = amount => new Intl.NumberFormat('vi-VN', {maximumFractionDigits: 2}).format(amount) + ' đ';
    let version = 0;
    let timer;
    let pending = false;
    let allowed = !submit.disabled;

    async function refreshQuote(revision) {
        pending = true;
        try {
            const headers = {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'};
            headers[form.dataset.csrfHeader] = form.dataset.csrfToken;
            const response = await fetch(form.dataset.quoteUrl, {
                method: 'POST', credentials: 'same-origin', redirect: 'error',
                headers,
                body: new URLSearchParams({code: voucher.value})
            });
            if (!response.ok) throw new Error('Không thể kiểm tra tổng tiền. Vui lòng thử lại hoặc tải lại trang.');
            const quote = await response.json();
            if (revision !== version) return;
            // Selection may have changed in another tab. Reload instead of keeping a stale item list.
            const oldRows = form.querySelectorAll('[data-quote-item]');
            const ids = Array.from(oldRows).map(row => row.dataset.quoteItem).join(',');
            const newIds = quote.items.map(item => String(item.id)).join(',');
            if (ids !== newIds || oldRows.length !== quote.items.length) {
                window.location.reload();
                return;
            }
            allowed = quote.checkoutAllowed;
            document.getElementById('quote-subtotal').textContent = formatMoney(quote.subtotal);
            document.getElementById('quote-shipping').textContent = formatMoney(quote.shippingFee);
            document.getElementById('quote-discount').textContent = '-' + formatMoney(quote.discountAmount);
            document.getElementById('quote-total').textContent = formatMoney(quote.totalAmount);
            quote.items.forEach((item, index) => {
                oldRows[index].querySelector('[data-item-quantity]').textContent = 'SL: ' + item.quantity + ' x ' + formatMoney(item.unitPrice);
                oldRows[index].querySelector('[data-item-total]').textContent = formatMoney(item.lineTotal);
            });
            message.textContent = quote.message || '';
            message.hidden = !quote.message;
            status.textContent = 'Đã cập nhật tổng tiền.';
        } catch (error) {
            if (revision !== version) return;
            allowed = false;
            message.textContent = error.message;
            message.hidden = false;
            status.textContent = '';
        } finally {
            if (revision === version) {
                pending = false;
                submit.disabled = !allowed;
            }
        }
    }
    voucher.addEventListener('input', () => {
        version++;
        clearTimeout(timer);
        submit.disabled = true;
        pending = true;
        status.textContent = 'Đang kiểm tra mã giảm giá…';
        const revision = version;
        timer = setTimeout(() => refreshQuote(revision), 300);
    });
    form.addEventListener('submit', event => {
        if (pending || !allowed) {
            event.preventDefault();
            return;
        }
        if (form.checkValidity()) {
            submit.disabled = true;
            status.textContent = 'Đang tạo đơn hàng…';
        }
    });
})();
