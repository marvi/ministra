@import ministra.calendar.Dates
@import ministra.schedule.ScheduleView
@param ScheduleView view
@param String adminBase

@template.layout(title = "Förslag på schema · " + view.poll().getTitle(), content = @`
    <h1>${view.poll().getTitle()}</h1>
    <p class="lede">Ett förslag, inget är sparat. Dra brickorna dit du vill, eller klicka
        på en bricka och sedan på en dag.</p>

    <p class="meta">${view.perDay()} per dag · ${view.people().size()} personer</p>

    @for(ScheduleView.Day day : view.days())
        <section class="schedule-day">
            <h2>
                <span class="day-date">${Dates.formatWithWeekday(day.day().date())}</span>
                @if(day.day().hasName())
                    <span class="day-name">${day.day().name()}</span>
                @endif
            </h2>
            @if(day.isEmpty())
                <p class="meta">Ingen</p>
            @else
                <p>
                    @for(ScheduleView.Tile tile : day.tiles())
                        <span class="tile">${tile.name()}</span>
                    @endfor
                </p>
            @endif
        </section>
    @endfor

    <div class="actions">
        <a class="button secondary" href="${adminBase}">Avbryt</a>
    </div>
`)
