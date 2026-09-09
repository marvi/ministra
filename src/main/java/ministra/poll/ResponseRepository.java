package ministra.poll;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResponseRepository extends JpaRepository<Response, Long> {

    /**
     * Svar som ännu inte tagits med i någon daglig sammanfattning, med deltagare och
     * förfrågan hämtade i samma fråga — jobbet går annars i N+1.
     */
    @Query(
            """
            select r from Response r
              join fetch r.participant p
              join fetch p.poll
            where r.notifiedAt is null
            order by p.id, r.serviceDate
            """)
    List<Response> findUnnotified();
}
