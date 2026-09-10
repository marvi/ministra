@import java.util.List
@import java.util.Map
@import ministra.calendar.ServiceDay
@import ministra.poll.CreatePollForm
@import ministra.calendar.Dates
@param List<ServiceDay> days
@param CreatePollForm form
@param Map<String, String> errors

<%--
  Hjälptexten ligger som syskon till etiketten, inte inuti den, och knyts till fältet med
  aria-describedby. Det är vad som låter två fält i bredd stå på samma linjer även när
  bara det ena har en hjälptext (D-048).
--%>
@template.layout(title = "Ny förfrågan", content = @`
    <h1>Ny förfrågan</h1>
    <p class="lede">Välj period, ge förfrågan ett namn och skicka länken till dem som ska
        svara.</p>

    <form method="post" action="/skapa">
        <h2>Vad och när</h2>

        <div class="field">
            <label class="field-label" for="title">Vad gäller det?</label>
            <span class="hint" id="title-hint">Till exempel "Sakristaner fram till påsk".</span>
            <input type="text" id="title" name="title" maxlength="120" required
                   aria-describedby="title-hint"
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
                <span class="hint" id="endDate-hint">Högst ett halvår efter första dagen.</span>
                <select id="endDate" name="endDate" required aria-describedby="endDate-hint">
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
            <label class="field-label" for="comment">Kommentar</label>
            <span class="hint" id="comment-hint">Valfri. Följer med i mejlet du skickar ut.</span>
            <textarea id="comment" name="comment" maxlength="1000"
                      aria-describedby="comment-hint">${form.comment() == null ? "" : form.comment()}</textarea>
        </div>

        <h2>Om dig</h2>

        <div class="two-up">
            <div class="field">
                <label class="field-label" for="creatorName">Ditt förnamn</label>
                <input type="text" id="creatorName" name="creatorName" maxlength="80" required
                       value="${form.creatorName() == null ? "" : form.creatorName()}">
                @if(errors.containsKey("creatorName"))
                    <p class="field-error">${errors.get("creatorName")}</p>
                @endif
            </div>
            <div class="field">
                <label class="field-label" for="creatorEmail">Din e-postadress</label>
                <span class="hint" id="creatorEmail-hint">Länkarna skickas dit.</span>
                <input type="email" id="creatorEmail" name="creatorEmail" maxlength="254" required
                       aria-describedby="creatorEmail-hint"
                       value="${form.creatorEmail() == null ? "" : form.creatorEmail()}">
                @if(errors.containsKey("creatorEmail"))
                    <p class="field-error">${errors.get("creatorEmail")}</p>
                @endif
            </div>
        </div>

        <div class="field">
            <label class="field-label" for="validMonths">Spara förfrågan i</label>
            <span class="hint" id="validMonths-hint">Därefter raderas den med alla svar.</span>
            <select id="validMonths" name="validMonths" aria-describedby="validMonths-hint">
                <option value="1" selected="${form.validMonths() != 2}">En månad</option>
                <option value="2" selected="${form.validMonths() == 2}">Två månader</option>
            </select>
        </div>

        <%-- Skapandet sker i två steg och sparar först i det andra (D-042). Det ska stå
             framme innan man trycker, inte upptäckas efteråt. --%>
        <div class="form-end">
            <div class="actions">
                <button type="submit">Välj dagar</button>
            </div>
            <p class="meta">Nästa steg: du bockar av vilka dagar som ska ingå. Förfrågan
                sparas först då.</p>
        </div>
    </form>
`)
