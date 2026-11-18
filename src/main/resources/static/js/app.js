(function () {
    'use strict';

    // ---------- Upload form: file name, drag & drop, submit spinner ----------
    const fileInput = document.querySelector('[data-audio-input]');
    const fileInfo = document.querySelector('[data-file-info]');
    const dropZone = document.querySelector('[data-drop-zone]');

    function formatSize(bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024)) + ' КБ';
        }
        return (bytes / (1024 * 1024)).toFixed(1) + ' МБ';
    }

    function showSelectedFile() {
        if (!fileInfo || !fileInput) {
            return;
        }
        if (fileInput.files && fileInput.files.length > 0) {
            const file = fileInput.files[0];
            fileInfo.textContent = file.name + ' · ' + formatSize(file.size);
            fileInfo.classList.add('has-file');
        } else {
            fileInfo.textContent = 'Файл не выбран';
            fileInfo.classList.remove('has-file');
        }
    }

    if (fileInput) {
        fileInput.addEventListener('change', showSelectedFile);
        showSelectedFile();
    }

    if (dropZone && fileInput) {
        ['dragenter', 'dragover'].forEach(function (eventName) {
            dropZone.addEventListener(eventName, function (event) {
                event.preventDefault();
                event.stopPropagation();
                dropZone.classList.add('dragover');
            });
        });
        ['dragleave', 'drop'].forEach(function (eventName) {
            dropZone.addEventListener(eventName, function (event) {
                event.preventDefault();
                event.stopPropagation();
                dropZone.classList.remove('dragover');
            });
        });
        dropZone.addEventListener('drop', function (event) {
            if (event.dataTransfer && event.dataTransfer.files.length > 0) {
                fileInput.files = event.dataTransfer.files;
                showSelectedFile();
            }
        });
    }

    const uploadForm = document.getElementById('upload-form');
    if (uploadForm) {
        uploadForm.addEventListener('submit', function () {
            const button = uploadForm.querySelector('[data-submit-button]');
            const spinner = uploadForm.querySelector('[data-submit-spinner]');
            if (button) {
                button.disabled = true;
            }
            if (spinner) {
                spinner.classList.remove('d-none');
            }
        });
    }

    // ---------- Meetups table: poll statuses without full page reload ----------
    const busyRows = Array.from(document.querySelectorAll('tr[data-status-url][data-busy="true"]'));
    if (busyRows.length > 0) {
        const timer = window.setInterval(async function () {
            let stillBusy = false;
            for (const row of busyRows) {
                try {
                    const response = await fetch(row.dataset.statusUrl, {
                        headers: {Accept: 'application/json'},
                        cache: 'no-store'
                    });
                    if (!response.ok) {
                        stillBusy = true;
                        continue;
                    }
                    const data = await response.json();
                    const badge = document.getElementById('status-badge-' + data.id);
                    if (badge) {
                        badge.textContent = data.statusLabel;
                        badge.className = 'badge rounded-pill text-bg-' + data.badgeClass;
                    }
                    if (!data.busy) {
                        window.clearInterval(timer);
                        window.location.reload();
                        return;
                    }
                    stillBusy = true;
                } catch (e) {
                    stillBusy = true;
                }
            }
            if (!stillBusy) {
                window.clearInterval(timer);
            }
        }, 3000);
    }

    // ---------- Detail page: refresh when transcription finishes ----------
    const page = document.getElementById('meetup-page');
    if (page && page.dataset.busy === 'true') {
        const timer = window.setInterval(async function () {
            try {
                const response = await fetch(page.dataset.statusUrl, {
                    headers: {Accept: 'application/json'},
                    cache: 'no-store'
                });
                if (!response.ok) {
                    return;
                }
                const data = await response.json();
                const badge = document.getElementById('detail-status-badge');
                if (badge) {
                    badge.textContent = data.statusLabel;
                    badge.className = 'badge fs-6 rounded-pill text-bg-' + data.badgeClass;
                }
                if (!data.busy) {
                    window.clearInterval(timer);
                    window.location.reload();
                }
            } catch (e) {
                // retry on the next tick
            }
        }, 3000);
    }

    // ---------- Copy transcript ----------
    document.querySelectorAll('[data-copy-target]').forEach(function (button) {
        button.addEventListener('click', async function () {
            const target = document.getElementById(button.dataset.copyTarget);
            if (!target) {
                return;
            }
            try {
                await navigator.clipboard.writeText(target.innerText);
                const original = button.innerHTML;
                button.innerHTML = '<i class="bi bi-check2"></i> Скопировано';
                window.setTimeout(function () {
                    button.innerHTML = original;
                }, 1500);
            } catch (e) {
                window.alert('Не удалось скопировать текст. Выделите его вручную.');
            }
        });
    });

    // ---------- Download transcript as .txt ----------
    document.querySelectorAll('[data-download-transcript]').forEach(function (button) {
        button.addEventListener('click', function () {
            const target = document.getElementById('transcript-text');
            if (!target) {
                return;
            }
            const title = (button.dataset.filename || 'transcript')
                .replace(/[\\/:*?"<>|]+/g, '-')
                .trim() || 'transcript';
            const blob = new Blob([target.innerText], {type: 'text/plain;charset=utf-8'});
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = title + '.txt';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        });
    });

    // ---------- Question examples and submit spinner ----------
    document.querySelectorAll('[data-question-example]').forEach(function (button) {
        button.addEventListener('click', function () {
            const textarea = document.getElementById('question');
            if (textarea) {
                textarea.value = button.dataset.questionExample;
                textarea.focus();
            }
        });
    });

    const questionForm = document.querySelector('[data-question-form]');
    if (questionForm) {
        questionForm.addEventListener('submit', function () {
            const button = questionForm.querySelector('[data-assistant-submit]');
            const spinner = questionForm.querySelector('[data-assistant-spinner]');
            if (button) {
                button.disabled = true;
                button.classList.add('disabled');
            }
            if (spinner) {
                spinner.classList.remove('d-none');
            }
        });
    }
})();
