/**
 * Product Form - Handles image preview, deletion, and loading state.
 */
document.addEventListener('DOMContentLoaded', function () {
    const imageInput = document.getElementById('imageInput');
    const previewContainer = document.getElementById('previewContainer');
    const previewLabel = document.getElementById('previewLabel');
    const productForm = document.getElementById('productForm');
    const loadingOverlay = document.getElementById('loadingOverlay');

    // Store selected files in an array to manage deletion
    let selectedFiles = [];

    // 1. HANDLE IMAGE PREVIEW & DELETION
    if (imageInput) {
        imageInput.addEventListener('change', function (event) {
            const newFiles = Array.from(event.target.files);

            // Append new valid files to our list
            newFiles.forEach(file => {
                if (file.type.startsWith('image/')) {
                    selectedFiles.push(file);
                }
            });

            // Update the actual input files using DataTransfer
            updateInputFiles();
            renderPreviews();
        });
    }

    function updateInputFiles() {
        const dataTransfer = new DataTransfer();
        selectedFiles.forEach(file => dataTransfer.items.add(file));
        imageInput.files = dataTransfer.files;
    }

    function renderPreviews() {
        previewContainer.innerHTML = ''; // Clear old previews

        if (selectedFiles.length > 0) {
            previewLabel.classList.remove('d-none');
        } else {
            previewLabel.classList.add('d-none');
        }

        selectedFiles.forEach((file, index) => {
            const reader = new FileReader();
            reader.onload = function (e) {
                const div = document.createElement('div');
                div.className = 'position-relative';
                div.style.width = '85px';
                div.style.height = '85px';

                // Create image element
                const img = document.createElement('img');
                img.src = e.target.result;
                img.className = 'img-thumbnail rounded shadow-sm fade-in w-100 h-100';
                img.style.objectFit = 'cover';

                // Create delete button (X)
                const btnDelete = document.createElement('button');
                btnDelete.type = 'button';
                btnDelete.className = 'btn btn-danger btn-sm rounded-circle position-absolute d-flex align-items-center justify-content-center';
                btnDelete.innerHTML = '<i class="bi bi-x"></i>';
                btnDelete.style.top = '-8px';
                btnDelete.style.right = '-8px';
                btnDelete.style.width = '24px';
                btnDelete.style.height = '24px';
                btnDelete.style.padding = '0';
                btnDelete.style.zIndex = '10';

                // Handle delete click
                btnDelete.onclick = function() {
                    selectedFiles.splice(index, 1); // Remove file from array
                    updateInputFiles();             // Update input
                    renderPreviews();               // Re-render
                };

                div.appendChild(img);
                div.appendChild(btnDelete);
                previewContainer.appendChild(div);
            };
            reader.readAsDataURL(file);
        });
    }

    // 2. HANDLE LOADING STATE ON SUBMIT
    if (productForm) {
        productForm.addEventListener('submit', function (event) {
            if (productForm.checkValidity()) {
                loadingOverlay.classList.remove('d-none');
                loadingOverlay.classList.add('d-flex');
            }
        });
    }
});