package ministra.poll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * En deltagares svar för en enskild gudstjänstdag.
 *
 * <p>Raden bär sitt eget datum. Det finns ingen dagtabell att peka på (D-018).
 *
 * <p>{@code notifiedAt} sätts när svaret tagits med i en daglig sammanfattning. Det sätts
 * i samma transaktion som outbox-raden skrivs, vilket är hela poängen med outboxen — utan
 * den blir det två skrivningar mot olika system och svar kan tappas tyst (D-030).
 */
@Entity
@Table(name = "response")
public class Response {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false, updatable = false)
    private Participant participant;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Availability availability;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    protected Response() {
        // för JPA
    }

    public Response(Participant participant, LocalDate serviceDate, Availability availability) {
        this.participant = participant;
        this.serviceDate = serviceDate;
        this.availability = availability;
    }

    public void markNotified(Instant when) {
        this.notifiedAt = when;
    }

    public Long getId() {
        return id;
    }

    public Participant getParticipant() {
        return participant;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public Availability getAvailability() {
        return availability;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }
}
