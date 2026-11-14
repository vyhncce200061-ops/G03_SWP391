(() => {
    'use strict';
    const form = document.getElementById('checkout-form');
    if (!form) return;
    const voucher = form.querySelector('[name="voucherCode"]');
    const walletVoucher = document.getElementById('wallet-voucher');
    const savedAddress = document.getElementById('saved-address');
    const submit = document.getElementById('place-order');
    const status = document.getElementById('quote-status');
    const message = document.getElementById('quote-message');
    const formatMoney = amount => new Intl.NumberFormat('vi-VN', {maximumFractionDigits: 0}).format(amount) + ' đ';
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


    function syncWalletSelection() {
        if (!walletVoucher) return;

        const enteredCode = voucher.value.trim().toUpperCase();

        const matchingOption = Array.from(walletVoucher.options).find(
            option => option.value !== '' && !option.disabled && option.value.trim().toUpperCase() === enteredCode
        );

        walletVoucher.value = matchingOption ? matchingOption.value : '';
    }

    if (walletVoucher) {
        walletVoucher.addEventListener('change', () => {
            // Chọn mã từ ví, hoặc bỏ mã khi chọn option rỗng.
            voucher.value = walletVoucher.value;

            // Dùng lại logic kiểm tra voucher và cập nhật tiền đang có.
            voucher.dispatchEvent(new Event('input', {bubbles: true}));
        });

        // Khi gõ mã thủ công, đồng bộ lại dropdown.
        voucher.addEventListener('input', syncWalletSelection);

        // Khôi phục lựa chọn khi server hiển thị lại form có dữ liệu.
        syncWalletSelection();
    }

    // Chọn địa chỉ đã lưu và điền vào form giao hàng.
    if (savedAddress) {
        const addressFieldNames = ['recipientName', 'recipientPhone', 'shippingProvince', 'shippingDistrict', 'shippingWard', 'shippingAddressLine'];

        const addressInputs = Object.fromEntries(addressFieldNames.map(name => [name, form.querySelector(`[name="${name}"]`)]));

        function normalizeAddressValue(value) {
            return (value || '').trim();
        }

        // Chỉ đồng bộ nhãn dropdown với dữ liệu đang có.
        // Không điền đè dữ liệu khi trang vừa mở hoặc form bị lỗi.
        function syncAddressSelection() {
            const matchingOption = Array.from(savedAddress.options).find(
                option => option.value !== '' && option.value !== '__new'
                    && addressFieldNames.every(
                        name => normalizeAddressValue(option.dataset[name]) === normalizeAddressValue(addressInputs[name].value)));

            savedAddress.value = matchingOption ? matchingOption.value : '';
        }

        savedAddress.addEventListener('change', () => {
            const selectedOption = savedAddress.selectedOptions[0];

            if (!selectedOption) return;

            // Chọn chế độ tùy chỉnh: giữ nguyên thông tin hiện tại.
            if (selectedOption.value === '') {
                return;
            }

            // Khách chủ động chọn nhập địa chỉ mới.
            if (selectedOption.value === '__new') {
                addressFieldNames.forEach(
                    name => {addressInputs[name].value = '';
                    });

                savedAddress.value = '';
                addressInputs.recipientName.focus();
                return;
            }

            // Điền thông tin từ địa chỉ đã lưu.
            addressFieldNames.forEach(name => {addressInputs[name].value = selectedOption.dataset[name] || '';});
        });

        // Sửa tay thì cập nhật lại lựa chọn:
        // khớp địa chỉ đã lưu → chọn địa chỉ đó;
        // không khớp → hiển thị "Địa chỉ tùy chỉnh bên dưới".
        addressFieldNames.forEach(name => {
            addressInputs[name].addEventListener('input', syncAddressSelection);

            addressInputs[name].addEventListener('change', syncAddressSelection);
        });

        // Backend đã điền mặc định hoặc giữ dữ liệu form lỗi.
        // JS chỉ tìm lựa chọn tương ứng, không thay dữ liệu đó.
        syncAddressSelection();
    }

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
