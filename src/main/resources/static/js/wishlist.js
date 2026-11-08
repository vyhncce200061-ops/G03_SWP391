(() => {
    const form = document.getElementById('wishlistForm');
    if (!form) return;
    const button = form.querySelector('button');
    const icon = button.querySelector('i');

    // Match main-layout's alert appearance, keeping wishlist behavior local.
    function showWishlistMessage(message, type = 'success') {
        const container = document.getElementById('alertContainer');
        if (!container) return;
        document.getElementById('wishlistAlert')?.remove();
        const alert = document.createElement('div');
        alert.id = 'wishlistAlert';
        alert.className = `alert alert-${type} alert-dismissible fade show`;
        alert.setAttribute('role', 'alert');
        const symbol = document.createElement('i');
        symbol.className = `bi ${type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-triangle-fill'} me-2`;
        symbol.setAttribute('aria-hidden', 'true');
        const text = document.createElement('span');
        text.textContent = message;
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'btn-close';
        close.setAttribute('data-bs-dismiss', 'alert');
        close.setAttribute('aria-label', 'Đóng thông báo');
        alert.append(symbol, text, close);
        container.append(alert);
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (button.disabled) return;
        const removing = button.getAttribute('aria-pressed') === 'true';
        if (removing && !window.confirm('Bạn có muốn gỡ sản phẩm này khỏi danh sách yêu thích?')) return;
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        try {
            // Include Thymeleaf's hidden CSRF parameter, including its masked value.
            const response = await fetch(removing ? form.dataset.removeUrl : form.action, {
                method: 'POST',
                credentials: 'same-origin',
                headers: {'Accept': 'application/json'},
                body: new URLSearchParams(new FormData(form))
            });
            if (response.redirected || response.status === 401 || response.status === 403) {
                throw new Error('Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập hoặc tải lại trang rồi thử lại.');
            }
            if (!(response.headers.get('content-type') || '').includes('application/json')) {
                throw new Error('Không thể lưu sản phẩm. Vui lòng thử lại.');
            }
            const result = await response.json();
            if (!response.ok || result.saved !== !removing) {
                throw new Error(result.message || 'Không thể lưu sản phẩm.');
            }
            icon.classList.toggle('bi-heart', !result.saved);
            icon.classList.toggle('bi-heart-fill', result.saved);
            button.setAttribute('aria-pressed', String(result.saved));
            button.title = result.saved ? 'Gỡ khỏi yêu thích' : 'Lưu vào yêu thích';
            button.setAttribute('aria-label', button.title);
            showWishlistMessage(result.message);
        } catch (error) {
            showWishlistMessage(error instanceof TypeError
                ? 'Có lỗi kết nối. Vui lòng thử lại.' : error.message, 'danger');
        } finally {
            button.disabled = false;
            button.removeAttribute('aria-busy');
        }
    });
})();
