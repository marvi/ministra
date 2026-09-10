@import java.time.LocalDate
@import java.util.List
@import java.util.Set
@import ministra.calendar.ServiceDay
@import ministra.poll.CreatePollForm
@import ministra.calendar.Dates
@param CreatePollForm form
@param List<ServiceDay> days
@param Set<LocalDate> added
@param Set<LocalDate> kept
@param String error

<%--
  Hela det interaktiva området, utbrutet så att htmx kan byta ut det i stället för att
  ladda om sidan. Utan htmx postar formuläret nedan som vanligt och samma mall renderas
  igen — sidan fungerar likadant, man hamnar bara överst.
--%>
<div id="day-choice">
    @if(error != null)
        <p class="error">${error}</p>
    @endif

    <form method="post" action="/skapa/spara">
        @template.hiddenform(form = form, added = added)

        <ul class="day-picks">
            @for(ServiceDay day : days)
                <li>
                    <input type="checkbox" id="pick-${day.date().toString()}"
                           name="day" value="${day.date().toString()}"
                           checked="${kept == null || kept.contains(day.date())}">
                    <label for="pick-${day.date().toString()}">
                        <span class="pick-text">
                            <span class="pick-date">${Dates.formatWithWeekday(day.date())}</span>
                            @if(day.hasName())
                                <span class="pick-name">${day.name()}</span>
                            @endif
                        </span>
                        <%-- Kryssrutan bär tillståndet för hjälpmedel, så knappen är bara
                             en visuell markör om vad ett klick gör. --%>
                        <span class="pick-action" aria-hidden="true">
                            <span class="pick-remove"><span class="pick-icon">✕</span> Ta bort</span>
                            <span class="pick-add"><span class="pick-icon">+</span> Lägg till</span>
                        </span>
                    </label>
                </li>
            @endfor
        </ul>

        <div class="form-end">
            <div class="actions">
                <button type="submit">Spara förfrågan</button>
            </div>
            <p class="meta">Då skapas förfrågan och du får länkarna på mejlen.</p>
        </div>
    </form>

    <%-- Egen form, utanför den ovan: ett formulär i ett annat är ogiltig HTML. De
         ikryssade dagarna följer med som dolda fält i stället. --%>
    <h2>Fira något mer?</h2>
    <p class="meta">En egen högtid eller ett lokalt helgon som inte finns i kyrkoåret.</p>
    <%-- Med htmx byts bara det här området ut, så man står kvar i listan i stället för
         att kastas till sidans topp. Utan htmx postar formuläret som vanligt. --%>
    <form method="post" action="/skapa/dagar" class="add-day"
          hx-post="/skapa/dagar" hx-target="#day-choice" hx-swap="outerHTML">
        @template.hiddenform(form = form, added = added)
        <%-- De ikryssade dagarna måste med också, annars nollställs valet av att man
             lägger till ett datum. --%>
        @for(ServiceDay day : days)
            @if(kept == null || kept.contains(day.date()))
                <input type="hidden" name="day" value="${day.date().toString()}">
            @endif
        @endfor

        <label class="field-label" for="extraDate">Datum</label>
        <div class="add-day-row">
            <input type="date" id="extraDate" name="extraDate"
                   min="${form.startDate().toString()}" max="${form.endDate().toString()}">
            <button type="submit" class="button secondary">Lägg till datum</button>
        </div>
    </form>
</div>
