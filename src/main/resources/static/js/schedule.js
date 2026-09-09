/*
 * Arbetsbladet för förslag på schema (D-046).
 *
 * Servern har räknat fram förslaget och lagt all data i DOM:en. Härifrån sker allt i
 * webbläsaren och ingenting sparas: laddas sidan om börjar man om.
 *
 * Två sätt att flytta, som ser likadana ut på skärmen:
 *
 *  - Klicka-klicka: ett klick på en bricka markerar den och tänder de dagar personen
 *    kan; nästa klick på en dag lägger dit den. Klick på samma bricka igen, eller
 *    Escape, avmarkerar. Fungerar med tangentbord och skärmläsare.
 *  - Dra och släpp med pekarhändelser (pointerdown/move/up), inte HTML5:s drag-API som
 *    saknas i Chrome på Android och är lynnigt i Safari på iOS. Ett drag som slutar där
 *    det började räknas som ett klick.
 *
 * En bricka från brickraden kopieras till dagen; en bricka från en dag flyttas. En person
 * läggs aldrig på en dag hen sagt nej till: sådana dagar tonas ned och tar inte emot.
 */
(function () {
    'use strict';

    // Sådant som bara fungerar med skript visas först nu (klassen needs-js i CSS:en).
    document.documentElement.classList.add('js');

    document.addEventListener('DOMContentLoaded', function () {
        const root = document.getElementById('schedule');
        if (!root) {
            return;
        }
        const tray = document.getElementById('tray');
        const days = Array.from(root.querySelectorAll('.schedule-day'));
        const live = document.getElementById('live');
        const undoRow = document.getElementById('undo');
        const undoText = document.getElementById('undo-text');
        const undoButton = document.getElementById('undo-button');
        const copyButton = document.getElementById('copy');
        const printButton = document.getElementById('print');
        const copied = document.getElementById('copied');

        // ---------- Svaren ----------

        /** Namn -> {can: Set<datum>, ifNeeded: Set<datum>}. Allt annat är "kan inte". */
        const people = new Map();
        tray.querySelectorAll('.tile.source').forEach(function (li) {
            people.set(li.dataset.name, {
                can: new Set(split(li.dataset.can)),
                ifNeeded: new Set(split(li.dataset.ifNeeded))
            });
        });

        function split(value) {
            return (value || '').split(' ').filter(Boolean);
        }

        function availability(name, date) {
            const answers = people.get(name);
            if (!answers) {
                return 'CANNOT';
            }
            if (answers.can.has(date)) {
                return 'CAN';
            }
            if (answers.ifNeeded.has(date)) {
                return 'IF_NEEDED';
            }
            return 'CANNOT';
        }

        function label(availabilityCode) {
            return availabilityCode === 'CAN' ? 'Kan' : 'Om det behövs';
        }

        // ---------- Dagarna ----------

        function dayDate(day) {
            return day.dataset.date;
        }

        function dayName(day) {
            return day.querySelector('.day-date').textContent.trim();
        }

        function tilesOn(day) {
            return Array.from(day.querySelectorAll('.tile'));
        }

        function hasPerson(day, name) {
            return tilesOn(day).some(function (tile) { return tile.dataset.name === name; });
        }

        function refreshEmpty(day) {
            day.querySelector('.empty').hidden = tilesOn(day).length > 0;
        }

        /** Kan ett släpp av personen på dagen göra något? */
        function accepts(day, name) {
            return availability(name, dayDate(day)) !== 'CANNOT' && !hasPerson(day, name);
        }

        // ---------- Brickorna ----------

        function makeTile(name, day) {
            const code = availability(name, dayDate(day));
            const li = document.createElement('li');
            li.className = 'tile ' + (code === 'CAN' ? 'can' : 'maybe');
            li.dataset.name = name;
            li.dataset.availability = code;

            const pick = document.createElement('button');
            pick.type = 'button';
            pick.className = 'tile-pick';
            pick.title = label(code);
            const text = document.createElement('span');
            text.className = 'tile-name';
            text.textContent = name;
            const word = document.createElement('span');
            word.className = 'visually-hidden';
            word.textContent = ', ' + label(code).toLowerCase();
            pick.appendChild(text);
            pick.appendChild(word);

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'tile-remove needs-js';
            remove.setAttribute('aria-label', 'Ta bort ' + name + ' från ' + dayName(day));
            remove.textContent = '×';

            li.appendChild(pick);
            li.appendChild(remove);
            return li;
        }

        function refreshCounts() {
            tray.querySelectorAll('.tile.source').forEach(function (li) {
                const n = root.querySelectorAll('.schedule-day .tile[data-name="' + cssEscape(li.dataset.name) + '"]').length;
                const badge = li.querySelector('.count');
                badge.textContent = String(n);
                badge.setAttribute('aria-label', n === 1 ? '1 dag' : n + ' dagar');
            });
        }

        function cssEscape(value) {
            return window.CSS && CSS.escape ? CSS.escape(value) : value.replace(/"/g, '\\"');
        }

        function announce(message) {
            live.textContent = '';
            // Två skrivningar i rad så att samma text läses upp två gånger om det behövs.
            window.setTimeout(function () { live.textContent = message; }, 30);
        }

        /** Lägger personen på dagen. Gör ingenting om hen inte kan eller redan ligger där. */
        function place(name, day) {
            if (!accepts(day, name)) {
                return false;
            }
            day.querySelector('.tiles').appendChild(makeTile(name, day));
            refreshEmpty(day);
            refreshCounts();
            return true;
        }

        function removeTile(tile) {
            const day = tile.closest('.schedule-day');
            tile.remove();
            refreshEmpty(day);
            refreshCounts();
        }

        // ---------- Markera och lägga (klicka-klicka) ----------

        /** Den markerade brickan: {name, tile, fromDay} eller null. */
        let selected = null;

        function select(tile) {
            clearSelection();
            const fromDay = tile.closest('.schedule-day');
            selected = { name: tile.dataset.name, tile: tile, fromDay: fromDay };
            tile.classList.add('selected');
            lightDays(selected.name);
        }

        function lightDays(name) {
            days.forEach(function (day) {
                const ok = accepts(day, name);
                day.classList.toggle('target', ok);
                day.classList.toggle('blocked', !ok);
                if (ok) {
                    day.setAttribute('tabindex', '0');
                } else {
                    day.removeAttribute('tabindex');
                }
            });
        }

        function clearSelection() {
            if (selected) {
                selected.tile.classList.remove('selected');
            }
            selected = null;
            days.forEach(function (day) {
                day.classList.remove('target', 'blocked', 'over');
                day.removeAttribute('tabindex');
            });
        }

        /** Lägger den markerade (eller dragna) brickan på dagen och avmarkerar. */
        function drop(source, day) {
            const name = source.name;
            const from = source.fromDay;
            if (from === day) {
                clearSelection();
                return;
            }
            if (place(name, day)) {
                if (from) {
                    removeTile(source.tile);
                }
                announce(name + ' flyttad till ' + dayName(day));
            }
            clearSelection();
        }

        root.addEventListener('click', function (event) {
            const removeButton = event.target.closest('.tile-remove');
            if (removeButton) {
                const tile = removeButton.closest('.tile');
                clearSelection();
                if (tile.classList.contains('source')) {
                    removePerson(tile);
                } else {
                    const day = tile.closest('.schedule-day');
                    removeTile(tile);
                    announce(tile.dataset.name + ' borttagen från ' + dayName(day));
                }
                return;
            }

            const pickButton = event.target.closest('.tile-pick');
            if (pickButton) {
                const tile = pickButton.closest('.tile');
                if (selected && selected.tile === tile) {
                    clearSelection();
                } else {
                    select(tile);
                }
                return;
            }

            const day = event.target.closest('.schedule-day');
            if (day && selected) {
                drop(selected, day);
                return;
            }

            // Klick på något annat avmarkerar, utom på knapparna längst ned.
            if (selected && !event.target.closest('button, a, select, label')) {
                clearSelection();
            }
        });

        root.addEventListener('keydown', function (event) {
            const day = event.target.closest('.schedule-day');
            if (day && selected && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault();
                drop(selected, day);
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && selected) {
                clearSelection();
            }
        });

        // ---------- Ta bort en person, med ångra ----------

        /** Borttagna personer, senast överst. */
        const removed = [];

        function removePerson(sourceTile) {
            const name = sourceTile.dataset.name;
            const placements = [];
            days.forEach(function (day) {
                tilesOn(day).forEach(function (tile) {
                    if (tile.dataset.name === name) {
                        placements.push(day);
                        tile.remove();
                    }
                });
                refreshEmpty(day);
            });
            removed.push({ name: name, tile: sourceTile, next: sourceTile.nextElementSibling, placements: placements });
            sourceTile.remove();
            refreshCounts();
            showUndo();
            announce(name + ' borttagen från arbetsbladet');
        }

        function showUndo() {
            if (removed.length === 0) {
                undoRow.hidden = true;
                return;
            }
            const last = removed[removed.length - 1];
            undoText.textContent = last.name + ' är borttagen från arbetsbladet.';
            undoRow.hidden = false;
        }

        undoButton.addEventListener('click', function () {
            const last = removed.pop();
            if (!last) {
                return;
            }
            if (last.next && last.next.parentNode === tray) {
                tray.insertBefore(last.tile, last.next);
            } else {
                tray.appendChild(last.tile);
            }
            last.placements.forEach(function (day) { place(last.name, day); });
            refreshCounts();
            showUndo();
            announce(last.name + ' tillbaka på arbetsbladet');
        });

        // ---------- Kopiera som text och skriva ut ----------

        function asText() {
            const title = root.querySelector('h1').textContent.trim();
            const lines = days.map(function (day) {
                const names = tilesOn(day).map(function (tile) { return tile.dataset.name; });
                return day.dataset.label + ': ' + (names.length ? names.join(', ') : '–');
            });
            return title + '\n\n' + lines.join('\n') + '\n';
        }

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

        let copiedTimer = null;
        copyButton.addEventListener('click', function () {
            copyText(asText()).then(function () {
                copied.textContent = 'Schemat ligger på urklipp.';
                copied.hidden = false;
                window.clearTimeout(copiedTimer);
                copiedTimer = window.setTimeout(function () { copied.hidden = true; }, 4000);
            }, function () {
                copied.textContent = 'Det gick inte att kopiera. Markera texten och kopiera själv.';
                copied.hidden = false;
            });
        });

        printButton.addEventListener('click', function () {
            window.print();
        });

        // Krokar för drag (pekarhändelser) hängs på här i nästa steg.
        root.ministra = { select: select, drop: drop, clearSelection: clearSelection, accepts: accepts, asText: asText };
    });
})();
