package ministra.calendar;

import java.time.LocalDate;

/**
 * En dag med gudstjänst: datum och, om kyrkoåret känner till dagen, dess namn.
 *
 * <p>Heter {@code ServiceDay} och inte {@code HolyDay} med flit. Vårt begrepp är "en dag
 * som behöver bemannas", inte en liturgisk kategori — och namnet skulle dessutom krocka
 * med {@code lectio.cal.HolyDay}, som betyder något annat: en dag med egna texter.
 *
 * <p>{@code name} kan saknas. Det är <em>inte</em> samma sak som att dagen är av en annan
 * sort, och därför är det här ingen förseglad hierarki med en namnlös variant: lektionarium
 * ska med tiden kunna namnge även vardagar — "Tisdagen i första påskveckan" — och då ska
 * de dagarna få sina namn utan att någon typ behöver ändras (D-043).
 *
 * <p>Beräknas alltid från lektionarium-API:t och persisteras aldrig. Se D-018.
 */
public record ServiceDay(LocalDate date, String name) {

    public ServiceDay(LocalDate date) {
        this(date, null);
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }
}
