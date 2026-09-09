@import java.util.List
@import java.util.Map
@import ministra.calendar.ServiceDay
@import ministra.poll.CreatePollForm
@import ministra.calendar.Dates
@param List<ServiceDay> days
@param CreatePollForm form
@param Map<String, String> errors

@template.layout(title = "Ny förfrågan", content = @`
    <h1>Ny förfrågan</h1>
    <p class="lede">Välj perioden, ge den ett namn och skicka länken till dem som ska svara.
        Du får själv fylla i direkt efteråt.</p>
    <p class="lede">Nästa steg visar alla dagar kyrkoåret firar med gudstjänst i perioden —
        inte bara söndagar, utan även jul, påsk och andra helgdagar. Där väljer du bort dem
        ni inte behöver.</p>

    <form method="post" action="/skapa">
        <div class="field">
            <label class="field-label" for="title">Vad gäller det?
                <span class="hint">Till exempel "Sakristaner fram till påsk".</span>
            </label>
            <input type="text" id="title" name="title" maxlength="120" required
                   value="${form.title() == null ? "" : form.title()}">
            @if(errors.containsKey("title"))
                <p class="field-error">${errors.get("title")}</p>
            @endif
        </div>

        <div class="two-up">
            <div class="field">
                <label class="field-label" for="startDate">Första dagen</label>
                <select id="startDate" name="startDate" required>
                    <option value="">Välj dag</option>
                    @for(ServiceDay day : days)
                        <option value="${day.date().toString()}"
                                selected="${day.date().equals(form.startDate())}">
                            ${Dates.format(day.date())} — ${day.name()}
                        </option>
                    @endfor
                </select>
            </div>
            <div class="field">
                <label class="field-label" for="endDate">Sista dagen</label>
                <select id="endDate" name="endDate" required>
                    <option value="">Välj dag</option>
                    @for(ServiceDay day : days)
                        <option value="${day.date().toString()}"
                                selected="${day.date().equals(form.endDate())}">
                            ${Dates.format(day.date())} — ${day.name()}
                        </option>
                    @endfor
                </select>
            </div>
        </div>
        @if(errors.containsKey("startDate"))
            <p class="field-error">${errors.get("startDate")}</p>
        @endif
        @if(errors.containsKey("endDate"))
            <p class="field-error">${errors.get("endDate")}</p>
        @endif

        <div class="field">
            <label class="field-label" for="comment">Kommentar
                <span class="hint">Valfri. Följer med i mejlet du skickar ut.</span>
            </label>
            <textarea id="comment" name="comment" maxlength="1000">${form.comment() == null ? "" : form.comment()}</textarea>
        </div>

        <h2>Om dig</h2>
        <div class="two-up">
            <div class="field">
                <label class="field-label" for="creatorName">Ditt förnamn
                    <span class="hint">Så att du kan fylla i din egen tillgänglighet.</span>
                </label>
                <input type="text" id="creatorName" name="creatorName" maxlength="80" required
                       value="${form.creatorName() == null ? "" : form.creatorName()}">
                @if(errors.containsKey("creatorName"))
                    <p class="field-error">${errors.get("creatorName")}</p>
                @endif
            </div>
            <div class="field">
                <label class="field-label" for="creatorEmail">Din e-postadress
                    <span class="hint">Dit skickas länkarna. Din väg tillbaka.</span>
                </label>
                <input type="email" id="creatorEmail" name="creatorEmail" maxlength="254" required
                       value="${form.creatorEmail() == null ? "" : form.creatorEmail()}">
                @if(errors.containsKey("creatorEmail"))
                    <p class="field-error">${errors.get("creatorEmail")}</p>
                @endif
            </div>
        </div>

        <div class="field">
            <label class="field-label" for="validMonths">Spara förfrågan i
                <span class="hint">Därefter raderas den med alla svar.</span>
            </label>
            <select id="validMonths" name="validMonths">
                <option value="1" selected="${form.validMonths() != 2}">En månad</option>
                <option value="2" selected="${form.validMonths() == 2}">Två månader</option>
            </select>
        </div>

        <div class="actions">
            <button type="submit">Välj dagar</button>
        </div>
    </form>
`)
