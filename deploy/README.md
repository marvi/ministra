# Drift

Ministra provisioneras av **vps-deploy**, Ansible-repot som äger servern. Där är ministra en
tjänst av typen `container` i `ansible/group_vars/all/services.yml`, precis som lektionarium:
avbilden körs som en Podman-quadlet under systemd, Caddy terminerar TLS på
`ministra.marvi.work` under wildcard-certet och proxar till containern på loopback, och
databasen är en Postgres 18 på värden som vps-deploy också sköter. Se
[D-045](../docs/decisions.md) och [D-014](../docs/decisions.md).

Det här repot innehåller ingen unit-fil. Quadleten genereras av vps-deploy ur posten i
services.yml. Här finns dokumentationen av alla inställningar och mallen för de hemligheter
som fylls i för hand.

## Konfiguration

Alla inställningar är miljövariabler, lästa av `application.properties`. Quadleten läser tre
filer i ordning, och en senare fil vinner vid krock.

| Fil | Skrivs av | Innehåll |
|---|---|---|
| `/etc/ministra.env` | Ansible, ur `environment:` i services.yml. Skrivs över vid varje körning. | allt som inte är hemligt |
| `/etc/ministra.db.env` | Ansible, genererat på servern första gången. | `MINISTRA_DB_PASSWORD` |
| `/etc/ministra.local.env` | Du, en gång, på servern. Ansible rör den aldrig. | SMTP-inloggning, tillfälliga överstyrningar |

| Variabel | Var | Betydelse |
|---|---|---|
| `MINISTRA_BASE_URL` | services.yml | Publik adress utan avslutande snedstreck. Alla länkar i mejlen byggs från den (D-022). |
| `MINISTRA_DB_URL` | services.yml | JDBC-URL. Värdens Postgres nås från containern på `host.containers.internal`: `jdbc:postgresql://host.containers.internal:5432/ministra`. |
| `MINISTRA_DB_USER` | services.yml | Databasrollen `ministra`, som äger databasen med samma namn. |
| `MINISTRA_DB_PASSWORD` | db.env | Genereras av vps-deploy. Fylls aldrig i för hand. |
| `MINISTRA_MAIL_FROM` | services.yml | Avsändaradress på en domän med SPF och DKIM. |
| `MINISTRA_CREATES_PER_HOUR_PER_IP` | services.yml | Tak för skapade förfrågningar per IP och timme (D-017). |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | services.yml | SMTP-servern (D-029). |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, `..._AUTH` | services.yml | `true` för AhaSend. |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | local.env | SMTP-inloggning. Se [ministra.local.env.example](ministra.local.env.example). |
| `SERVER_PORT` | genereras | Sätts av vps-deploy till containerns port på värden. Appen lyssnar på 8080 inuti containern; vps-deploy mappar `port` till `container_port`. |

`JAVA_TOOL_OPTIONS` sätts i avbilden till `-XX:MaxRAMPercentage=75.0`, och minnesgränsen
står i services.yml (`memory: 512m`). Andra JVM-flaggor läggs under `environment:`.

En ny inställning i koden följer mönstret: `${NAMN:default}` i `application.properties`, en
rad i tabellen ovan, och värdet i services.yml om det inte är hemligt, annars i
[ministra.local.env.example](ministra.local.env.example).

## Första gången

Allt sker i vps-deploy, från katalogen `ansible/`.

1. Posten `ministra` finns i `services.yml` med en ledig port. Domänen ligger under
   `marvi.work` och får vhost och certifikat automatiskt.
2. Postgres på värden, om den inte redan är uppsatt:

   ```bash
   ansible-playbook site.yml --tags postgres
   ```

3. Tjänsten. Skapar roll och databas, genererar lösenordet, skriver env-filerna och
   quadleten, och startar containern:

   ```bash
   ansible-playbook site.yml --tags services -e only=ministra
   ```

4. Fyll i SMTP-inloggningen på servern och starta om:

   ```bash
   $EDITOR /etc/ministra.local.env       # enligt ministra.local.env.example
   systemctl restart ministra
   journalctl -u ministra -f
   ```

Flyway lägger på schemat vid första starten. Rollen äger databasen, så inga rättigheter
behöver ges för hand (D-039).

## Släppa en ny version

```bash
tools/release.sh
```

Skriptet släpper versionen pom-filen står på, taggar `vX.Y.Z` och höjer sedan pom-filen
ett patchsteg. Ska X eller Y höjas anger man versionen: `tools/release.sh --version 1.3`.
Taggen får GitHub Actions att bygga, testa och publicera avbilden
till `ghcr.io/marvi/ministra`, trigga `podman auto-update` på servern över tailnetet, och
sedan vänta tills `/actuator/health` svarar UP genom Caddy. Startar den nya avbilden inte
rullar podman tillbaka och flödet blir rött.

Flödet behöver i repots inställningar på GitHub: secrets `TS_OAUTH_CLIENT_ID` och
`TS_OAUTH_SECRET` samt variabeln `DEPLOY_HOST`. Samma värden som lektionarium använder,
se vps-deploy:s README under "GitHub Actions över Tailscale".

Utan flödet hämtar `podman-auto-update.timer` nya avbilder en gång om dygnet. Manuellt:

```bash
ssh deploy@<DEPLOY_HOST> 'sudo /usr/bin/podman auto-update'
```

## Databasen

Postgres 18 kör på värden, nås från containern på `host.containers.internal` och från
tailnetet med `psql -h <server> -U ministra ministra`. Lösenordet står i
`/etc/ministra.db.env` på servern. Det externa interfacet släpper aldrig in någon.

vps-deploy dumpar alla databaser varje natt till `/var/lib/pgsql/backups/`. Återställning:

```bash
sudo -u postgres pg_restore -d ministra --clean --if-exists /var/lib/pgsql/backups/ministra-<datum>.dump
```

Testerna kör `postgres:18` via Testcontainers för att matcha major-versionen. Lokalt behövs
en podman-maskin och `DOCKER_HOST` som pekar på dess socket.

Avveckling sker med `retire-service.yml` i vps-deploy. Databasen lämnas alltid kvar; den
tas bort för hand när du är säker.

## Reverse proxy

Caddy sätter `X-Forwarded-For`, som hastighetsgränsen läser (D-017). Appen bygger däremot
aldrig länkar från proxyns headers. Den publika adressen kommer från `MINISTRA_BASE_URL`
(D-022).
