@import java.time.LocalDate
@import java.util.Set
@import ministra.poll.CreatePollForm
@param CreatePollForm form
@param Set<LocalDate> added

<%-- Ingenting är sparat förrän i sista steget, så uppgifterna från första formuläret
     följer med som dolda fält genom varje anrop (D-042). Båda formulären på dagvalssidan
     behöver dem, och de ska inte kunna glida isär. --%>
<input type="hidden" name="title" value="${form.title()}">
<input type="hidden" name="comment" value="${form.comment() == null ? "" : form.comment()}">
<input type="hidden" name="startDate" value="${form.startDate().toString()}">
<input type="hidden" name="endDate" value="${form.endDate().toString()}">
<input type="hidden" name="validMonths" value="${form.validMonths()}">
<input type="hidden" name="creatorName" value="${form.creatorName()}">
<input type="hidden" name="creatorEmail" value="${form.creatorEmail()}">
@for(LocalDate extra : added)
    <input type="hidden" name="added" value="${extra.toString()}">
@endfor
