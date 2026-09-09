package ministra.poll;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Någon som svarat på en förfrågan, identifierad enbart med förnamn.
 *
 * <p>Namnet är unikt inom förfrågan (D-009). {@code nameKey} är den normaliserade formen
 * som unikheten vilar på, så att "anna" och "Anna " inte slinker igenom.
 *
 * <p>Svaren är oföränderliga (D-010). Den som svarat fel lämnar ett nytt svar under ett
 * särskiljande namn — "Anna Ny" — och det gamla ligger kvar.
 */
@Entity
@Table(name = "participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false, updatable = false)
    private Poll poll;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(name = "name_key", nullable = false, updatable = false)
    private String nameKey;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @OneToMany(
            mappedBy = "participant",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("serviceDate ASC")
    private List<Response> responses = new ArrayList<>();

    protected Participant() {
        // för JPA
    }

    public Participant(Poll poll, String name) {
        this.poll = poll;
        this.name = name.trim();
        this.nameKey = normalize(name);
        this.submittedAt = Instant.now();
    }

    /** Normaliserad form för unikhetskontrollen: trimmad och gemener. */
    public static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public void addResponse(Response response) {
        responses.add(response);
    }

    public Long getId() {
        return id;
    }

    public Poll getPoll() {
        return poll;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public List<Response> getResponses() {
        return responses;
    }
}
