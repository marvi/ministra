# Ministra

Ministra samlar in **tillgänglighet** från frivilliga inför kyrkoårets gudstjänster.

Den som planerar väljer en period, skickar ut en länk, och får tillbaka en lista över
vem som kan när. Inga konton, inga lösenord, ingen app att installera. Förfrågningar
raderar sig själva.

## Problemet

Den som ansvarar för söndagens förbedjare behöver veta vilka söndagar var och en av dem
kan. Samma sak gäller textläsarna, ministranterna, kyrkvärdarna och lovsångsledarna.
Doodle löste det förr, men duger inte längre.

Ministra gör en sak: samlar in svaren. Att sedan bestämma vem som faktiskt läser texten
den 18 oktober gör en människa, med listan framför sig.

## Så fungerar det

**1. Någon skapar en förfrågan.** Väljer period, ger den en titel — "Sakristaner fram
till påsk" — och anger sitt förnamn och sin e-postadress.

**2. Väljer dagar.** Nästa skärm visar alla dagar kyrkoåret firar med gudstjänst i
perioden. Inte bara söndagar: juldagen, annandagarna, Långfredagen, Kristi himmelsfärds
dag, Annandag pingst och de andra finns med. Dagar ni inte bemannar klickar man bort, och
firar församlingen något eget — ett lokalt helgon, en egen högtid — lägger man till det
datumet. Först nu skapas förfrågan.

**3. Två länkar.** En att skicka ut, en att spara själv. Båda visas på skärmen och mejlas
till skaparen. Utskicket sker manuellt, i skaparens eget e-postprogram — appen förbereder
mejlet men fyller aldrig i några mottagare, för deltagarnas adresser ska inte finnas hos
oss.

**4. Deltagarna svarar.** Ett trafikljus per dag: **kan**, **kan om det behövs**, eller
**kan inte**. Man skriver sitt förnamn överst. Man ser vad de andra har svarat — det är
hela poängen, att se var luckorna finns.

**5. Skaparen får veta.** En samlad sammanfattning varje kväll, inte ett mejl per svar.

**6. Allt raderas.** När giltighetstiden gått ut försvinner förfrågan med alla svar.

## Vad den inte gör

Medvetna begränsningar, inte saker som saknas:

- **Fördelar inte personer på dagar.** Appen samlar in tillgänglighet. Schemaläggningen
  gör en människa.
- **Räknar inte.** Den vet inte hur många som behövs per dag eller hur många som fått
  länken, så det finns ingen förloppsindikator och ingen varning för otäckta dagar.
- **Svar går inte att ändra.** Utan konton finns ingen säker väg tillbaka till just ditt
  svar. Den som svarat fel lämnar ett nytt under ett särskiljande namn, som i Framadate.
- **Dagarna går inte att ändra efteråt.** De valdes när förfrågan skapades. Blev det fel
  raderar man och gör om.
- **Inga konton, ingen inloggning, ingen mobilapp, ingen kalendersynk.**

## Integritet

Det är därför appen finns. Den lagrar så lite som möjligt:

| Vad | Varför |
|---|---|
| Skaparens förnamn och e-postadress | Länkarna och den dagliga sammanfattningen |
| Deltagarnas förnamn | Identifierar svaret i listan |
| Datum och tillgänglighet | Själva syftet |

Deltagarnas e-postadresser lagras aldrig — de finns bara i skaparens eget e-postprogram.
Inga IP-adresser sparas. Allt raderas automatiskt när förfrågan går ut.

Länkarna är hemligheter: 128 slumpbitar var, och den som har en kommer in. Sidorna är
märkta `noindex` och `robots.txt` blockerar allt, så listorna hamnar inte i Google.
Svarslänken och administrationslänken är separata — att kunna svara innebär aldrig att
man kan radera.

## Teknik

Spring Boot 4 på Java 25. Server-renderad HTML med [jte](https://jte.gg) och
[htmx](https://htmx.org), handskriven CSS, inget JavaScript-byggsteg och ingen npm.
PostgreSQL 16 med Flyway. Kyrkoåret kommer från
[lektionarium](https://github.com/marvi/lektionarium).

Sidorna klarar sig i huvudsak med vanliga formulär. htmx används där en omladdning skulle
vara irriterande, och varje sådan plats fungerar även utan JavaScript — då blir det en
vanlig sidladdning i stället. Allt som laddas serveras från appen; inga externa
resurser.

Utgående post går över SMTP och köas i en outbox-tabell, så att ett mejlavbrott aldrig
kan tappa ett svar.

## Utveckling

Kräver Java 25 och Podman. Java-versionen styrs av `mise.toml`; har du
[mise](https://mise.jdx.dev) installerat sätts den automatiskt.

```bash
# Databas för utveckling. Port 5433 med flit — 5432 är ofta upptagen av en annan
# Postgres, och då kopplar appen tyst upp sig mot fel databas.
podman run --rm -d --name ministra-db -p 5433:5432 \
  -e POSTGRES_DB=ministra -e POSTGRES_USER=ministra -e POSTGRES_PASSWORD=ministra \
  docker.io/library/postgres:18

./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Appen ligger på http://localhost:8080. Utan `-Dspring-boot.run.profiles=dev` startar den
med produktionsinställningar och saknar databasadress.

Det finns ingen mejlserver i utvecklingsläget. Mejlen hamnar i tabellen `outbox_email`
och går att titta på där.

```bash
./mvnw test                              # alla tester
./mvnw test -Dtest=PollServiceTest       # en klass
./mvnw verify                            # hela bygget
```

Testerna startar en riktig PostgreSQL via Testcontainers, så podman-socketen måste vara
igång:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock
```

På macOS räcker `podman machine start`.

### Kodstruktur

```
src/main/java/ministra/
  calendar/   kyrkoåret, via lektionarium-api
  poll/       domänen: förfrågan, deltagare, svar
  mail/       texterna, outboxen, den dagliga sammanfattningen
  web/        controller, hastighetsgräns, formatering
src/main/jte/           mallarna
src/main/resources/db/  Flyway-migreringar
```

Paketen följer funktion, inte lager. Det finns ingen `controller`- eller `service`-katalog.

## Drift

En OCI-avbild byggs av GitHub Actions vid varje tagg och publiceras till
`ghcr.io/marvi/ministra`. Servern provisioneras av vps-deploy, som kör avbilden som en
Podman-quadlet under systemd bakom Caddy, mot en Postgres 18 på värden. Se
[deploy/README.md](deploy/README.md) för uppsättningen och alla miljövariabler.

All konfiguration kommer från miljövariabler i env-filer. Loggarna går till stdout och
läses med `journalctl`.

## Dokumentation

- [docs/product.md](docs/product.md) — vad appen ska göra och varför
- [docs/decisions.md](docs/decisions.md) — beslutslogg, ett beslut per post med
  motivering och vad som förkastades
- [AGENTS.md](AGENTS.md) — arbetsregler för den som utvecklar, människa eller agent
- [FRAGOR.md](FRAGOR.md) — frågor och fynd från implementationen

Läs beslutsloggen innan du föreslår arkitekturändringar. Flera saker som ser konstiga ut
är medvetna val.

## Licens

GNU Affero General Public License version 3. Se [LICENSE](LICENSE).
