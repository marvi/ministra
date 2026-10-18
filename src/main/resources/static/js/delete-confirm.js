/*
 * Bekräftelsen för radering.
 *
 * En dialog i sidan, inte webbläsarens confirm() — den går inte att göra tumvänlig och
 * ser olika ut överallt (D-026). Utan JavaScript står bekräftelserutan framme i
 * markupen, så att det går att radera ändå (D-003). Först här döljs den bakom knappen.
 */
document.documentElement.classList.add('js');
document.addEventListener('DOMContentLoaded', function () {
    const start = document.getElementById('delete-start');
    const cancel = document.getElementById('delete-cancel');
    const confirm = document.getElementById('delete-confirm');
    if (!start || !confirm) {
        return;
    }
    confirm.hidden = true;
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
