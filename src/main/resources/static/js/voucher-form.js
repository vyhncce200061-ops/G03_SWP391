(() => {
    'use strict';
    const type = document.getElementById('discountType');
    const value = document.getElementById('discountValue');
    if (!type || !value) return;
    const updateLimits = () => {
        const percent = type.value === 'PERCENT';
        value.step = percent ? '0.01' : '1';
        value.min = percent ? '0.01' : '1';
        if (percent) value.max = '100';
        else value.removeAttribute('max');
    };
    type.addEventListener('change', updateLimits);
    updateLimits();
})();
