package ministra.mail;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEmail, Long> {

    /** Mejl som är mogna för ett nytt försök och inte förbrukat sina chanser. */
    List<OutboxEmail> findByNextAttemptAtBeforeAndAttemptsLessThanOrderByCreatedAtAsc(
            Instant now, int maxAttempts, Limit limit);
}
