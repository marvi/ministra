/*
 * Länkarna på skaparens sida (D-016): kopiera-knapp för var och en, och själva adressen
 * dold tills man ber om den. Utan skript står adressen framme, eftersom knappen då inte
 * kan fungera; skriptet sätter klassen js på html-elementet, och CSS:en gör resten.
 */
(function () {
    'use strict';

    document.documentElement.classList.add('js');

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.link-row').forEach(function (row) {
            const url = row.querySelector('.link-url');
            const copy = row.querySelector('.copy-link');
            const reveal = row.querySelector('.reveal-link');
            const status = row.querySelector('.copy-status');
            if (!url) {
                return;
            }
            url.hidden = true;

            if (reveal) {
                reveal.addEventListener('click', function () {
                    url.hidden = !url.hidden;
                    reveal.setAttribute('aria-expanded', String(!url.hidden));
                    reveal.textContent = url.hidden ? 'Visa länken' : 'Dölj länken';
                });
            }

            if (copy) {
                copy.addEventListener('click', function () {
                    copyText(url.textContent.trim()).then(function () {
                        status.textContent = 'Kopierad.';
                    }, function () {
                        url.hidden = false;
                        status.textContent = 'Det gick inte att kopiera. Markera länken och kopiera själv.';
                    });
                });
            }
        });
    });

    function copyText(text) {
        if (navigator.clipboard && window.isSecureContext) {
            return navigator.clipboard.writeText(text);
        }
        // Reserv för äldre webbläsare och http.
        return new Promise(function (resolve, reject) {
            const area = document.createElement('textarea');
            area.value = text;
            area.setAttribute('readonly', '');
            area.style.position = 'fixed';
            area.style.opacity = '0';
            document.body.appendChild(area);
            area.select();
            const ok = document.execCommand && document.execCommand('copy');
            area.remove();
            ok ? resolve() : reject(new Error('copy failed'));
        });
    }
})();
