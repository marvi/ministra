@import ministra.calendar.Dates
@import ministra.poll.Availability
@import ministra.schedule.ScheduleView
@param ScheduleView view
@param String adminBase

<%-- Arbetsbladet (D-046). Servern räknar fram förslaget och renderar det; allt därefter
     sker i webbläsaren och ingenting sparas. All data skriptet behöver ligger i DOM:en:
     varje dag bär data-date och data-label, varje bricka data-name och data-availability,
     och varje person i brickraden bär sina svar som data-can och data-if-needed. En dag
     som saknas i båda är "kan inte". Utan skript är sidan en färdig läsbar utskrift;
     det som bara fungerar med skript har klassen needs-js och göms utan. --%>
@template.layout(title = "Förslag på schema · " + view.poll().getTitle(), wide = true, content = @`
    <div class="schedule" id="schedule" data-admin-base="${adminBase}">
        <h1>${view.poll().getTitle()}</h1>
        <p class="lede">Ett förslag, inget är sparat. Dra brickorna dit du vill, eller
            klicka på en bricka och sedan på en dag.</p>
        <p class="meta only-narrow">Det här går lättare på en större skärm.</p>

        <form method="get" action="${adminBase}/schema" class="per-day">
            <label class="field-label" for="perDag">Personer per dag</label>
            <select id="perDag" name="perDag">
                @for(int n = 1; n <= ScheduleView.MAX_PER_DAY; n++)
                    <option value="${n}" selected="${n == view.perDay()}">${n}</option>
                @endfor
            </select>
            <button type="submit" class="button secondary">Räkna om</button>
        </form>

        <%-- Ångra-raden fylls av skriptet när någon tas bort ur brickraden. --%>
        <div class="undo needs-js" id="undo" hidden>
            <span id="undo-text"></span>
            <button type="button" class="button secondary" id="undo-button">Ångra</button>
        </div>

        <section class="tray" aria-labelledby="tray-heading">
            <h2 id="tray-heading">Personer</h2>
            <p class="meta">Siffran är antal dagar.</p>
            <ul class="tiles" id="tray">
                @for(ScheduleView.Person person : view.people())
                    <li class="tile source"
                        data-name="${person.name()}"
                        data-can="${String.join(" ", person.answers().entrySet().stream().filter(e -> e.getValue() == Availability.CAN).map(e -> e.getKey().toString()).sorted().toList())}"
                        data-if-needed="${String.join(" ", person.answers().entrySet().stream().filter(e -> e.getValue() == Availability.IF_NEEDED).map(e -> e.getKey().toString()).sorted().toList())}">
                        <button type="button" class="tile-pick">
                            <span class="tile-name">${person.name()}</span>
                            <span class="count" aria-label="${person.count() == 1 ? "1 dag" : person.count() + " dagar"}">${person.count()}</span>
                        </button>
                        <button type="button" class="tile-remove needs-js"
                                aria-label="Ta bort ${person.name()} från arbetsbladet">×</button>
                    </li>
                @endfor
            </ul>
        </section>

        <h2 id="days-heading">Dagar</h2>
        <ol class="schedule-grid" aria-labelledby="days-heading">
            @for(ScheduleView.Day day : view.days())
                !{var label = Dates.formatShort(day.day().date()) + (day.day().hasName() ? " · " + day.day().name() : "");}
                <li class="schedule-day"
                    data-date="${day.day().date().toString()}"
                    data-label="${label}"
                    aria-label="${Dates.formatWithWeekday(day.day().date())}${day.day().hasName() ? ", " + day.day().name() : ""}">
                    <h3>
                        <span class="day-date">${Dates.formatWithWeekday(day.day().date())}</span>
                        @if(day.day().hasName())
                            <span class="day-name">${day.day().name()}</span>
                        @endif
                    </h3>
                    <ul class="tiles">
                        @for(ScheduleView.Tile tile : day.tiles())
                            <li class="tile ${tile.availability() == Availability.CAN ? "can" : "maybe"}"
                                data-name="${tile.name()}"
                                data-availability="${tile.availability().name()}">
                                <button type="button" class="tile-pick" title="${tile.availability().label()}">
                                    <span class="tile-name">${tile.name()}</span>
                                    <span class="visually-hidden">, ${tile.availability().label().toLowerCase()}</span>
                                </button>
                                <button type="button" class="tile-remove needs-js"
                                        aria-label="Ta bort ${tile.name()} från ${Dates.formatWithWeekday(day.day().date())}">×</button>
                            </li>
                        @endfor
                    </ul>
                    <p class="empty meta" hidden="${!day.isEmpty()}">Ingen</p>
                </li>
            @endfor
        </ol>

        <div class="actions">
            <button type="button" class="needs-js" id="copy">Kopiera som text</button>
            <button type="button" class="button secondary needs-js" id="print">Skriv ut</button>
            <a class="button secondary" href="${adminBase}">Avbryt</a>
        </div>
        <p class="meta needs-js" id="copied" hidden>Schemat ligger på urklipp.</p>

        <%-- Skärmläsare får "Frida flyttad till söndag 4 oktober" vid varje flytt. --%>
        <div class="visually-hidden" id="live" aria-live="polite"></div>
    </div>
    <script src="/js/schedule.js" defer></script>
`)
