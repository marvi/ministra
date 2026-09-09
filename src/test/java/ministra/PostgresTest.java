package ministra;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Riktig Postgres, inte H2 — annars fångas inte skillnader i datumhantering och
 * constraints (D-001). Versionen matchar produktionens major (D-015).
 *
 * <p>Containern startas i ett statiskt block och lämnas åt Testcontainers att städa bort
 * när JVM:en avslutas. Med {@code @Testcontainers} och {@code @Container} stoppas den i
 * stället efter första testklassen, och nästa klass får vänta ut anslutningspoolens
 * timeout innan den misslyckas.
 *
 * <p>Kräver en körande podman-socket. Se avsnittet Tester i AGENTS.md.
 */
public abstract class PostgresTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
