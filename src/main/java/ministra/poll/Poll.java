package ministra.poll;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * En insamling av tillgänglighet för en period av gudstjänstdagar.
 *
 * <p>Vad förfrågan gäller framgår enbart av titeln — "Sakristaner fram till påsk". Det
 * finns ingen roll-typ och ska inte finnas (D-005). Appen vet inte heller hur många som
 * behövs per dag eller hur många som fått länken.
 *
 * <p>Dagarna lagras inte. Bara start- och slutdatum sparas; listan räknas fram från
 * kyrkoårs-API:t vid varje visning (D-018). Det enda som lagras därutöver är skaparens
 * justeringar: bortvalda dagar och tillagda datum (D-042, D-043).
 */
@Entity
@Table(name = "poll")
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Delas ut till dem som ska svara. */
    @Column(name = "response_token", nullable = false, unique = true, updatable = false)
    private String responseToken;

    /** Skaparens egen. Att kunna svara får aldrig innebära att man kan radera (D-006). */
    @Column(name = "admin_token", nullable = false, unique = true, updatable = false)
    private String adminToken;

    @Column(nullable = false)
    private String title;

    @Column(name = "comment_text")
    private String comment;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Efter detta datum raderas förfrågan av nattjobbet (D-021). */
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "creator_name", nullable = false)
    private String creatorName;

    @Column(name = "creator_email", nullable = false)
    private String creatorEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Dagar skaparen valt bort ur listan (D-042).
     *
     * <p>Hämtas ivrigt: mängden är liten, den behövs varje gång listan visas, och en lat
     * koppling skulle smälla utanför transaktionen precis som deltagarsamlingen gjorde.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "excluded_day", joinColumns = @JoinColumn(name = "poll_id"))
    @Column(name = "service_date", nullable = false)
    private Set<LocalDate> excludedDays = new LinkedHashSet<>();

    /**
     * Dagar skaparen lagt till för hand (D-043). Spegelbilden av {@link #excludedDays}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "extra_day", joinColumns = @JoinColumn(name = "poll_id"))
    @Column(name = "service_date", nullable = false)
    private Set<LocalDate> extraDays = new LinkedHashSet<>();

    /**
     * Finns för att raderingen ska kaskadera i JPA och inte bara i databasen. Utan den
     * går flush på en raderad förfrågan i väggen, eftersom deltagarna fortfarande pekar
     * på den i persistenskontexten.
     */
    @OneToMany(
            mappedBy = "poll",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<Participant> participants = new ArrayList<>();

    protected Poll() {
        // för JPA
    }

    public Poll(
            String responseToken,
            String adminToken,
            String title,
            String comment,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate validUntil,
            String creatorName,
            String creatorEmail) {
        this.responseToken = responseToken;
        this.adminToken = adminToken;
        this.title = title;
        this.comment = comment;
        this.startDate = startDate;
        this.endDate = endDate;
        this.validUntil = validUntil;
        this.creatorName = creatorName;
        this.creatorEmail = creatorEmail;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getResponseToken() {
        return responseToken;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public String getTitle() {
        return title;
    }

    public String getComment() {
        return comment;
    }

    public boolean hasComment() {
        return comment != null && !comment.isBlank();
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getCreatorEmail() {
        return creatorEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public Set<LocalDate> getExcludedDays() {
        return Collections.unmodifiableSet(excludedDays);
    }

    public Set<LocalDate> getExtraDays() {
        return Collections.unmodifiableSet(extraDays);
    }

    public boolean isExcluded(LocalDate date) {
        return excludedDays.contains(date);
    }

    /**
     * Sätter skaparens justeringar av listan. Anropas en gång, vid skapandet — dagarna går
     * inte att ändra efteråt (D-042).
     *
     * <p>Fyller de befintliga samlingarna i stället för att byta ut dem, så att Hibernate
     * behåller greppet om instanserna.
     */
    void chooseDays(Set<LocalDate> excluded, Set<LocalDate> extra) {
        excludedDays.clear();
        excludedDays.addAll(excluded);
        extraDays.clear();
        extraDays.addAll(extra);
    }

    /**
     * Kopplar ihop båda sidorna av relationen.
     *
     * <p>Sätts bara den ena sidan ser Hibernate en raderad förfrågan som fortfarande har
     * deltagare som pekar på sig, och flushen går i väggen.
     */
    void addParticipant(Participant participant) {
        participants.add(participant);
    }
}
