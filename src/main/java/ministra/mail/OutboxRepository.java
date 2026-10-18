package ministra.mail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEmail, Long> {

    /** Mejl som är mogna för ett nytt försök och inte förbrukat sina chanser. */
    List<OutboxEmail> findByNextAttemptAtBeforeAndAttemptsLessThanOrderByCreatedAtAsc(
            Instant now, int maxAttempts, Limit limit);

    /**
     * Gallrar mejl som gett upp, men bara när förfrågan de gäller har gått ut. Fram till
     * dess ligger raden kvar som spår av felet (D-030); därefter är den bara personuppgifter
     * (D-028).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OutboxEmail e where e.attempts >= :maxAttempts and e.validUntil < :today")
    int deleteExhaustedExpiredBefore(
            @Param("maxAttempts") int maxAttempts, @Param("today") LocalDate today);
}
