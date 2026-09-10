@import ministra.poll.Availability
@import ministra.poll.PollView
@import ministra.poll.ServiceDayView
@import ministra.calendar.Dates
@param PollView view
@param boolean admin
@param String prefilledName
@param String error
@param String submitPath
@param String responseUrl = null
@param String adminUrl = null
@param String adminBase = null
@param String mailto = null
@param boolean canPropose = false
@param boolean thanked = false

@template.layout(title = view.poll().getTitle(), content = @`
    <h1>${view.poll().getTitle()}</h1>

    @if(view.poll().hasComment())
        <p class="lede">${view.poll().getComment()}</p>
    @endif

    <p class="meta">
        ${Dates.format(view.poll().getStartDate())} – ${Dates.format(view.poll().getEndDate())}
        · ${view.days().size()} dagar
        @if(view.participantCount() == 1)
            · 1 svar
        @else
            · ${view.participantCount()} svar
        @endif
    </p>
    <%-- Synligt för alla, inte bara skaparen. Ärligt om vad som händer med svaren. --%>
    <p class="meta">Raderas efter ${Dates.format(view.poll().getValidUntil())}.</p>

    @if(thanked)
        <p class="notice" role="status">Tack, ditt svar är sparat.</p>
    @endif
    @if(error != null)
        <p class="error">${error}</p>
    @endif

    @if(admin)
        <%-- Båda länkarna med kopiera-knapp (D-016). Själva adressen visas först på begäran;
             utan skript står den framme, eftersom knappen då inte kan fungera. --%>
        <div class="admin-panel">
            <h2>Dina länkar</h2>
            <div class="link-row">
                <strong>Länk att skicka till dem som skall svara:</strong>
                <div class="actions">
                    <button type="button" class="button secondary needs-js copy-link">Kopiera länken</button>
                    <a class="button secondary" href="${mailto}">Skicka via e-post</a>
                    <button type="button" class="button secondary needs-js reveal-link"
                            aria-expanded="false">Visa länken</button>
                </div>
                <code class="link-url">${responseUrl}</code>
                <p class="meta copy-status" role="status"></p>
            </div>
            <div class="link-row">
                <strong>Din länk för att se resultatet:</strong>
                <div class="actions">
                    <button type="button" class="button secondary needs-js copy-link">Kopiera länken</button>
                    <button type="button" class="button secondary needs-js reveal-link"
                            aria-expanded="false">Visa länken</button>
                </div>
                <code class="link-url">${adminUrl}</code>
                <p class="meta copy-status" role="status"></p>
                <p class="meta">Båda länkarna är också mejlade till dig.</p>
            </div>
            <%-- Förslaget är ett hjälpmedel, avgörandet är skaparens, och inget sparas (D-046).
                 Knappen kommer först när det finns något att fördela. --%>
            <div class="link-row">
                <strong>Förslag på schema</strong>
                @if(canPropose)
                    <div class="actions">
                        <a class="button" href="${adminBase}/schema">Ge förslag på schema</a>
                    </div>
                @else
                    <p class="meta">Går att få när minst tre har svarat.</p>
                @endif
            </div>
        </div>
        <script src="/js/links.js" defer></script>
    @endif

    <form method="post" action="${submitPath}">
        <div class="field">
            <label class="field-label" for="name">Ditt förnamn</label>
            <span class="hint" id="name-hint">Är namnet upptaget, lägg till en bokstav.</span>
            <input type="text" id="name" name="name" maxlength="80" required
                   autocomplete="given-name" aria-describedby="name-hint"
                   value="${prefilledName}">
        </div>

        <%-- Ordalydelsen är beslutad i D-013 och står alltid framme, aldrig i en hover.
             Knapparna nedanför är tonade redan i vila, så "grön", "gul" och "röd" pekar
             på något man ser (D-044). --%>
        <p class="instructions">Ange för varje dag om du <strong>kan</strong> (grön),
            <strong>kan om det behövs</strong> (gul) eller <strong>inte kan</strong> (röd).
            Mittenvalet betyder att du helst avstår, men ställer upp om ingen annan kan.</p>

        <fieldset class="days">
            @for(ServiceDayView row : view.days())
                <fieldset class="day">
                    <legend>
                        <span class="day-date">${Dates.formatWithWeekday(row.day().date())}</span>
                        @if(row.day().hasName())
                            <span class="day-name">${row.day().name()}</span>
                        @endif
                    </legend>

                    <div class="choices">
                        <input type="radio" required
                               id="${row.day().date().toString()}-can"
                               name="d-${row.day().date().toString()}" value="CAN">
                        <label class="can" for="${row.day().date().toString()}-can">
                            <span class="check" aria-hidden="true">✓</span> ${Availability.CAN.label()}
                        </label>

                        <input type="radio" required
                               id="${row.day().date().toString()}-maybe"
                               name="d-${row.day().date().toString()}" value="IF_NEEDED">
                        <label class="maybe" for="${row.day().date().toString()}-maybe">
                            <span class="check" aria-hidden="true">✓</span> ${Availability.IF_NEEDED.label()}
                        </label>

                        <input type="radio" required
                               id="${row.day().date().toString()}-cannot"
                               name="d-${row.day().date().toString()}" value="CANNOT">
                        <label class="cannot" for="${row.day().date().toString()}-cannot">
                            <span class="check" aria-hidden="true">✓</span> ${Availability.CANNOT.label()}
                        </label>
                    </div>

                    <%-- Grupperat per svar, inte per person — det skalar till tio
                         textläsare. Ordet står utskrivet så färgen aldrig bär ensam. --%>
                    @if(row.hasAnswers())
                        <div class="answers">
                            @if(!row.can().isEmpty())
                                <p class="can"><span class="mark" aria-hidden="true">●</span>
                                    <span class="word">${Availability.CAN.label()}</span>${String.join(", ", row.can())}</p>
                            @endif
                            @if(!row.ifNeeded().isEmpty())
                                <p class="maybe"><span class="mark" aria-hidden="true">●</span>
                                    <span class="word">${Availability.IF_NEEDED.label()}</span>${String.join(", ", row.ifNeeded())}</p>
                            @endif
                            @if(!row.cannot().isEmpty())
                                <p class="cannot"><span class="mark" aria-hidden="true">●</span>
                                    <span class="word">${Availability.CANNOT.label()}</span>${String.join(", ", row.cannot())}</p>
                            @endif
                        </div>
                    @endif
                </fieldset>
            @endfor
        </fieldset>

        <%-- Rätt varning i rätt ögonblick (D-010): sista raden innan knappen. --%>
        <div class="form-end">
            <p class="meta">Svar kan inte ändras i efterhand.</p>
            <div class="actions primary">
                <button type="submit">Spara mitt svar</button>
            </div>
        </div>
    </form>

    @if(admin)
        <h2>Radera förfrågan</h2>
        <div class="actions">
            <button type="button" class="button danger" id="delete-start">Radera förfrågan</button>
        </div>
        <div class="confirm" id="delete-confirm" hidden>
            <p class="error">Förfrågan <strong>${view.poll().getTitle()}</strong> och alla
                ${view.participantCount()} svar raderas. Det går inte att ångra.</p>
            <form method="post" action="${adminBase}/radera">
                <div class="actions">
                    <button type="submit" class="button danger">Ja, radera allt</button>
                    <button type="button" class="button secondary" id="delete-cancel">Avbryt</button>
                </div>
            </form>
        </div>
        <script src="/js/delete-confirm.js" defer></script>
    @endif
`)
