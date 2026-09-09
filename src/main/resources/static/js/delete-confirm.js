/*
 * Bekräftelsen för radering.
 *
 * En dialog i sidan, inte webbläsarens confirm() — den går inte att göra tumvänlig och
 * ser olika ut överallt (D-026). Utan JavaScript visas bekräftelserutan direkt, så
 * knappen fungerar ändå.
 */
document.addEventListener('DOMContentLoaded', function () {
    const start = document.getElementById('delete-start');
    const cancel = document.getElementById('delete-cancel');
    const confirm = document.getElementById('delete-confirm');
    if (!start || !confirm) {
        return;
    }
    start.addEventListener('click', function () {
        confirm.hidden = false;
        start.hidden = true;
        confirm.scrollIntoView({block: 'nearest'});
    });
    if (cancel) {
        cancel.addEventListener('click', function () {
            confirm.hidden = true;
            start.hidden = false;
        });
    }
});
