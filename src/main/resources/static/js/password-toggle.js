(() => {
    'use strict';

    const ready = () => {
        let sequence = 0;
        for (const input of document.querySelectorAll('input[type="password"]')) {
            if (input.dataset.passwordToggleReady) continue;

            const container = input.parentElement;
            const label = input.labels[0] || container.querySelector('label');
            const fieldName = label?.textContent.trim().replace(/\s+/g, ' ') || 'mật khẩu';
            if (!input.id) {
                let id;
                do { id = 'password-field-' + ++sequence; }
                while (document.getElementById(id));
                input.id = id;
            }
            if (label && !label.htmlFor) label.htmlFor = input.id;

            let group = container;
            if (!container.classList.contains('input-group')) {
                group = document.createElement('div');
                group.className = 'input-group';
                container.insertBefore(group, input);
                group.append(input);
                // Keep Bootstrap field errors next to their input after wrapping.
                const feedback = group.nextElementSibling;
                if (feedback?.matches('.invalid-feedback, .valid-feedback')) group.append(feedback);
            }
            group.classList.add('password-field');

            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'btn btn-outline-secondary password-toggle';
            button.disabled = input.disabled;
            button.setAttribute('aria-controls', input.id);
            const icon = document.createElement('i');
            icon.setAttribute('aria-hidden', 'true');
            button.append(icon);
            input.insertAdjacentElement('afterend', button);
            if (group.querySelector('.invalid-feedback, .valid-feedback')) group.classList.add('has-validation');

            const setVisible = visible => {
                input.type = visible ? 'text' : 'password';
                const description = (visible ? 'Ẩn ' : 'Hiển thị ') + fieldName.toLocaleLowerCase('vi-VN');
                button.setAttribute('aria-label', description);
                button.setAttribute('aria-pressed', String(visible));
                button.title = description;
                icon.className = visible ? 'bi bi-eye-slash' : 'bi bi-eye';
            };
            button.addEventListener('click', () => {
                if (!input.disabled) setVisible(input.type === 'password');
            });
            input.form?.addEventListener('reset', () => setVisible(false));
            input.form?.addEventListener('submit', () => setVisible(false));
            window.addEventListener('pageshow', () => setVisible(false));
            setVisible(false);
            input.dataset.passwordToggleReady = 'true';
        }
    };

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, {once: true});
    else ready();
})();
