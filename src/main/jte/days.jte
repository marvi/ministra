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

@template.layout(title = "Välj dagar", content = @`
    <h1>Vilka dagar gäller det?</h1>
    <p class="lede">${form.title()} · ${Dates.format(form.startDate())} –
        ${Dates.format(form.endDate())}</p>
    <p class="lede">Alla dagar kyrkoåret firar med gudstjänst är med från början — inte bara
        söndagar, utan även jul, påsk och andra helgdagar.</p>
    <p class="instructions">Klicka på en dag för att <strong>ta bort</strong> den ur
        listan. Klicka igen för att lägga tillbaka den. Förfrågan skapas först när du
        trycker på Spara.</p>

    @template.dayschoice(form = form, days = days, added = added, kept = kept, error = error)
`)
