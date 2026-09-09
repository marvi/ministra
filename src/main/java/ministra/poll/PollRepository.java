package ministra.poll;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollRepository extends JpaRepository<Poll, Long> {

    Optional<Poll> findByResponseToken(String responseToken);

    Optional<Poll> findByAdminToken(String adminToken);

    /** Förfrågningar vars giltighetstid gått ut och som gallringsjobbet ska radera. */
    List<Poll> findByValidUntilBefore(LocalDate date);
}
