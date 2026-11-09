(() => {
    'use strict';
    const init = () => {
        const modal = document.getElementById('brandModal');
        if (!modal || !window.bootstrap) return;
        const form = document.getElementById('brandForm');
        const name = form.elements.namedItem('name');
        const description = form.elements.namedItem('description');
        modal.addEventListener('show.bs.modal', event => {
            const button = event.relatedTarget;
            // Validation responses retain their submitted values and action.
            if (!button) return;
            form.action = button.dataset.brandAction;
            name.value = button.dataset.brandName || '';
            description.value = button.dataset.brandDescription || '';
            document.getElementById('brandModalTitle').textContent = button.dataset.brandTitle;
            document.getElementById('brandSubmit').textContent = button.dataset.brandSubmit;
            form.querySelectorAll('.is-invalid').forEach(field => field.classList.remove('is-invalid'));
        });
        modal.addEventListener('shown.bs.modal', () => name.focus());
        if (modal.dataset.open === 'true') bootstrap.Modal.getOrCreateInstance(modal).show();
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
