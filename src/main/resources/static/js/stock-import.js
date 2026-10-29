/**
 * Stock Import Form - Dynamic row management.
 * Handles adding/removing item rows and calculating row totals.
 */

let rowIndex = 0;

/**
 * Add a new item row from the hidden template.
 * Clones the <template id="rowTemplate"> and appends it to the items table.
 */
function addRow() {
    const template = document.getElementById('rowTemplate');
    if (!template) {
        console.error('rowTemplate not found');
        return;
    }

    const clone = template.content.cloneNode(true);

    // Replace INDEX placeholder with actual row index
    clone.querySelectorAll('[name]').forEach(el => {
        el.name = el.name.replace('INDEX', rowIndex);
    });

    document.getElementById('itemsBody').appendChild(clone);
    rowIndex++;
}

/**
 * Remove a row and reindex remaining rows.
 */
function removeRow(btn) {
    btn.closest('tr').remove();
    reindexRows();
}

/**
 * Reindex all rows to keep items[0], items[1], ... sequential.
 */
function reindexRows() {
    const rows = document.querySelectorAll('#itemsBody tr');
    rows.forEach((row, i) => {
        row.querySelectorAll('[name]').forEach(el => {
            el.name = el.name.replace(/items\[\d+\]/, 'items[' + i + ']');
        });
    });
    rowIndex = rows.length;
}

/**
 * Recalculate row total when quantity or unit cost changes.
 */
function updateTotal(input) {
    const row = input.closest('tr');
    const qty = parseInt(row.querySelector('input[name*=".quantity"]').value) || 0;
    const cost = parseFloat(input.value) || 0;
    row.querySelector('.rowTotal').textContent = (qty * cost).toLocaleString('vi-VN') + ' đ';
}

/**
 * Initialize: set rowIndex based on existing rows when page loads.
 */
document.addEventListener('DOMContentLoaded', function () {
    rowIndex = document.querySelectorAll('#itemsBody tr').length;
});