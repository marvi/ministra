package ministra.poll;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    /**
     * Deltagarna med sina svar hämtade i samma fråga.
     *
     * <p>Både för att slippa N+1 och för att vyn byggs utanför en transaktion: en lat
     * koppling skulle kasta {@code LazyInitializationException} eftersom
     * {@code spring.jpa.open-in-view} är avstängt.
     */
    @Query(
            """
            select distinct p from Participant p
              left join fetch p.responses
            where p.poll = :poll
            order by p.submittedAt
            """)
    List<Participant> findWithResponses(@Param("poll") Poll poll);

    boolean existsByPollAndNameKey(Poll poll, String nameKey);
}
