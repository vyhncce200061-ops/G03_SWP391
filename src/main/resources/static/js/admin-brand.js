(() => {
    'use strict';
    const init = () => {
        const modal = document.getElementById('brandModal');
        if (!modal || !window.bootstrap) return;
        const form = document.getElementById('brandForm');
        const name = form.elements.namedItem('name');
        const description = form.elements.namedItem('description');
        const file = form.elements.namedItem('logoFile');
        const removeLogo = form.elements.namedItem('removeLogo');
        const preview = document.getElementById('brandLogoPreview');
        const previewBox = document.getElementById('brandLogoPreviewBox');
        const removeBox = document.getElementById('brandRemoveLogoBox');
        let storedLogo = preview.dataset.storedSrc || '';
        let previewUrl;
        const updatePreview = () => {
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            previewUrl = file.files[0] ? URL.createObjectURL(file.files[0]) : '';
            const source = previewUrl || (removeLogo.checked ? '' : storedLogo);
            previewBox.hidden = !source;
            if (source) preview.src = source;
            else preview.removeAttribute('src');
            removeBox.hidden = !storedLogo || Boolean(previewUrl);
        };
        file.addEventListener('change', () => {
            if (file.files.length) removeLogo.checked = false;
            updatePreview();
        });
        removeLogo.addEventListener('change', updatePreview);
        modal.addEventListener('show.bs.modal', event => {
            const button = event.relatedTarget;
            // Validation responses retain their submitted values and action.
            if (!button) return;
            form.action = button.dataset.brandAction;
            name.value = button.dataset.brandName || '';
            description.value = button.dataset.brandDescription || '';
            file.value = '';
            removeLogo.checked = false;
            storedLogo = button.dataset.brandLogo || '';
            updatePreview();
            document.getElementById('brandModalTitle').textContent = button.dataset.brandTitle;
            document.getElementById('brandSubmit').textContent = button.dataset.brandSubmit;
            form.querySelectorAll('.is-invalid').forEach(field => field.classList.remove('is-invalid'));
        });
        modal.addEventListener('shown.bs.modal', () => name.focus());
        updatePreview();
        if (modal.dataset.open === 'true') bootstrap.Modal.getOrCreateInstance(modal).show();
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
