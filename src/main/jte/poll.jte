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

@template.layout(title = view.poll().getTitle(), content = @`
    <h1>${view.poll().getTitle()}</h1>
    <p class="meta">
        ${Dates.format(view.poll().getStartDate())} – ${Dates.format(view.poll().getEndDate())}
        · ${view.days().size()} dagar
        @if(view.participantCount() == 1)
            · 1 svar
        @else
            · ${view.participantCount()} svar
        @endif
    </p>

    @if(view.poll().hasComment())
        <p class="comment">${view.poll().getComment()}</p>
    @endif

    @if(error != null)
        <p class="error">${error}</p>
    @endif

    @if(admin)
        <div class="admin-panel">
            <h2>Dina länkar</h2>
            <div class="link-row">
                <strong>Skicka den här till dem som ska svara</strong>
                <code>${responseUrl}</code>
                <a class="button secondary" href="${mailto}">Skriv mejlet i din e-post</a>
            </div>
            <div class="link-row">
                <strong>Den här är din egen — spara den</strong>
                <code>${adminUrl}</code>
                <p class="meta">Med den ser du svaren och kan radera förfrågan.
                    Båda länkarna har också mejlats till dig.</p>
            </div>
        </div>
    @endif

    <%-- Ordalydelsen är beslutad i D-013 och står alltid framme, aldrig i en hover. --%>
    <p class="instructions">Ange för varje dag om du <strong>kan</strong> (grön),
        <strong>kan om det behövs</strong> (gul) eller <strong>inte kan</strong> (röd).
        Mittenvalet betyder att du helst avstår, men ställer upp om ingen annan kan.</p>

    <form method="post" action="${submitPath}">
        <div class="field">
            <label class="field-label" for="name">Ditt förnamn
                <span class="hint">Räcker med förnamn. Finns namnet redan får du lägga till en bokstav.</span>
            </label>
            <input type="text" id="name" name="name" maxlength="80" required
                   autocomplete="given-name" value="${prefilledName}">
        </div>

        <fieldset class="days">
            @for(ServiceDayView row : view.days())
                <fieldset class="day">
                    <legend>
                        <span class="day-date">${Dates.formatShort(row.day().date())}</span>
                        @if(row.day().hasName())
                            <span class="day-name">${row.day().name()}</span>
                        @endif
                    </legend>

                    <div class="choices">
                        <input type="radio" required
                               id="${row.day().date().toString()}-can"
                               name="d-${row.day().date().toString()}" value="CAN">
                        <label class="can" for="${row.day().date().toString()}-can">
                            <span class="mark" aria-hidden="true">●</span> Kan
                        </label>

                        <input type="radio" required
                               id="${row.day().date().toString()}-maybe"
                               name="d-${row.day().date().toString()}" value="IF_NEEDED">
                        <label class="maybe" for="${row.day().date().toString()}-maybe">
                            <span class="mark" aria-hidden="true">◐</span> Om det behövs
                        </label>

                        <input type="radio" required
                               id="${row.day().date().toString()}-cannot"
                               name="d-${row.day().date().toString()}" value="CANNOT">
                        <label class="cannot" for="${row.day().date().toString()}-cannot">
                            <span class="mark" aria-hidden="true">○</span> Kan inte
                        </label>
                    </div>

                    @if(row.hasAnswers())
                        <div class="answers">
                            @if(!row.can().isEmpty())
                                <p class="can"><span class="mark" aria-hidden="true">●</span>
                                    <span><span class="sr-only">Kan: </span>${String.join(", ", row.can())}</span></p>
                            @endif
                            @if(!row.ifNeeded().isEmpty())
                                <p class="maybe"><span class="mark" aria-hidden="true">◐</span>
                                    <span><span class="sr-only">Om det behövs: </span>${String.join(", ", row.ifNeeded())}</span></p>
                            @endif
                            @if(!row.cannot().isEmpty())
                                <p class="cannot"><span class="mark" aria-hidden="true">○</span>
                                    <span><span class="sr-only">Kan inte: </span>${String.join(", ", row.cannot())}</span></p>
                            @endif
                        </div>
                    @endif
                </fieldset>
            @endfor
        </fieldset>

        <div class="actions">
            <button type="submit">Skicka mitt svar</button>
        </div>
    </form>

    @if(admin)
        <h2>Radera förfrågan</h2>
        <p class="meta">Förfrågan raderas automatiskt efter
            ${Dates.format(view.poll().getValidUntil())}. Du kan också göra det nu.</p>
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
