(() => {
    'use strict';
    const ready = () => {
        for (const image of document.querySelectorAll('img[data-review-image]')) {
            const fallback = () => {
                const url = image.dataset.fallbackSrc || '/images/product-placeholder.svg';
                if (image.getAttribute('src') !== url) image.src = url;
            };
            image.addEventListener('error', fallback);
            if (image.complete && !image.naturalWidth) fallback();
        }
        for (const textarea of document.querySelectorAll('textarea[data-feedback-counter]')) {
            const counter = document.getElementById(textarea.dataset.feedbackCounter);
            const update = () => {
                const limit = textarea.maxLength > 0 ? textarea.maxLength : 1000;
                if (counter) counter.textContent = textarea.value.length + ' / ' + limit;
                textarea.setCustomValidity(textarea.value && !textarea.value.trim()
                    ? 'Vui lòng nhập nội dung, không chỉ khoảng trắng.'
                    : textarea.value.length > limit ? 'Nội dung tối đa ' + limit + ' ký tự.' : '');
            };
            textarea.addEventListener('input', update);
            update();
        }
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, {once:true});
    else ready();
})();
