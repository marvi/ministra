# Ministra — arbetsinstruktioner

Webbapp för att samla in **tillgänglighet** från frivilliga inför kyrkoårets gudstjänster.
Produktvisionen finns i [docs/product.md](docs/product.md). Fattade beslut med motivering
finns i [docs/decisions.md](docs/decisions.md). **Läs decisions.md innan du föreslår
arkitekturändringar** — flera saker som ser konstiga ut är medvetna val.

## Kommandon

```bash
# Databas för lokal utveckling
podman run --rm -d --name ministra-db -p 5433:5432 \
  -e POSTGRES_DB=ministra -e POSTGRES_USER=ministra -e POSTGRES_PASSWORD=ministra \
  docker.io/library/postgres:18

./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # http://localhost:8080
./mvnw test                                             # alla tester
./mvnw test -Dtest=KlassNamn#metodNamn                  # ett enskilt test
./mvnw verify                                           # måste vara grön innan "klart"
./mvnw clean generate-sources                           # bygger om jte-mallarna
```

Java 25 styrs av `mise.toml`. Använd alltid `./mvnw`, aldrig ett globalt `mvn`.

Utan `-Dspring-boot.run.profiles=dev` startar appen med produktionsinställningar och
saknar databasadress. Porten är 5433 med flit — 5432 är ofta upptagen av en annan
Postgres, och då kopplar appen tyst upp sig mot fel databas.

## Stack

| Val                                 | Detalj                                                                                        |
|-------------------------------------|-----------------------------------------------------------------------------------------------|
| Spring Boot 4.0.8, Java 25          | `spring-boot-starter-webmvc`                                                                  |
| **jte 3.2.4**                       | server-renderade mallar i `src/main/jte`, kompileras av `jte-maven-plugin`                    |
| **htmx 5.1.0**                      | `io.github.wimdeblauwe:htmx-spring-boot`                                                      |
| PostgreSQL **18** + Spring Data JPA | central instans på värden, egen roll och databas — se [D-001](docs/decisions.md), [D-045](docs/decisions.md) |
| `spring-boot-starter-mail`          | utgående post via SMTP till **AhaSend** — se [D-029](docs/decisions.md)                       |
| `io.marvi:lektionarium-api:2.7`     | kyrkoårskalender, från `https://maven.marvi.work`                                             |

### Vyer

Det finns fyra: skapa-formuläret, dagvalet, svarsvyn och admin-vyn. Admin-vyn **är**
svarsvyn med länkar, mailto-knapp, raderaknapp och förifylld namnruta ovanpå
([D-027](docs/decisions.md)) — bygg inte två listmallar.

Skapandet sker i två steg och sparar först i det andra ([D-042](docs/decisions.md)).
Dagvalets kryssrutor växlar med ren CSS; htmx används bara för att lägga till ett eget
datum, och även det fungerar utan ([D-043](docs/decisions.md)). Svarsvyn visar hela listan
direkt, utan att kräva namn först ([D-023](docs/decisions.md)).

**Två fällor i den här stacken.** `gg.jte.development-mode=false` kräver att
`gg.jte.use-precompiled-templates=true` också sätts — annars vägrar jte att starta.
Och i Spring Boot 4 ligger Flyways autokonfiguration i modulen `spring-boot-flyway`;
enbart `flyway-core` kör inga migreringar.

**Detta är inte en SPA.** All HTML renderas på servern med jte; htmx sköter partiella
uppdateringar. htmx är självhostat som `/js/htmx-2.0.4.min.js` — ladda aldrig något från
ett CDN. **Varje htmx-användning måste ha en fungerande väg utan JavaScript**
([D-003](docs/decisions.md)); htmx är en förbättring, aldrig en förutsättning. Föreslå inte React, Vue eller ett JS-byggsteg. Handskriven CSS, inget
ramverk, ingen npm. Behövs mer interaktivitet skrivs Javascript eller möjligen inkluderas 
mindre JS-bibliotek.

## Design och UX

Målet är inte minsta möjliga app utan en app där det är **svårt att göra fel** — se
[D-008](docs/decisions.md). Användarna är församlingsmedlemmar, inte teknikvana.

- **Mobil och skärm är likvärdiga.** Inte "fungerar även på mobil". Bygg mobilvyn först.
- **Ingen information får finnas bara vid hover.** Det existerar inte på pekskärm.
  Hjälptexter ska vara synliga eller nåbara med ett tryck. Innebörden av grönt, gult och
  rött står som brödtext ovanför listan, med den ordalydelse som anges i
  [D-013](docs/decisions.md) — den är beslutad, formulera inte om den på egen hand.
- **Träffytor minst 44×44 px.** Trafikljusknapparna trycks med tumme i kyrkbänken.
- **Färg får aldrig bära information ensam.** Grön, gul och röd behöver också etikett
  eller symbol — färgblindhet är vanligare än man tror i en grupp på tio.
- Färre val per skärm. Tydliga förval. Inga inställningar som kräver att användaren
  förstår datamodellen.
- Om Google Fonts, ikoner, Javascriptbibliotek eller externa CSS-bibliotek används skall 
  de tas hem och servas från appen.
- Varje komponent skall vara viktig för användaren. Fråga: behövs den verkligen.
- Layout skall inte vara "stock-bootstrap". Arbeta med färgpalett och fonter så att det 
  blir aptitligt. Samtidigt: användarvänlighet och funktion över estetik.


## Drift

Appen körs som en Podman-quadlet under systemd på egen server — se [D-002](docs/decisions.md).
Tre konsekvenser som påverkar koden direkt:

- All konfiguration läses från miljövariabler, som kommer från **env-filer** via
  `EnvironmentFile=` i quadleten — inte från `Environment=`-rader i unit-filen.
  Ingen konfigurationsfil inuti imagen, inget `application-prod.properties`. En ny
  inställning får `${NAMN:default}` i `application.properties` och en rad i
  [deploy/README.md](deploy/README.md).
- All loggning går till stdout/stderr, så `journalctl` fungerar. Skriv aldrig till loggfil.
- Deployerbar artefakt är en OCI-image, byggd av GitHub Actions till
  `ghcr.io/marvi/ministra` från en handskriven `Dockerfile`
  ([D-032](docs/decisions.md)). Mönstret finns i
  [marvi/lektionarium](https://github.com/marvi/lektionarium) — följ det.
- **Servern provisioneras av vps-deploy** ([D-045](docs/decisions.md)). Ministra är en
  `container`-post i dess `services.yml`, och quadleten genereras därifrån — det här repot
  innehåller ingen unit-fil. Databasen är en Postgres 18 på värden, nådd via
  `host.containers.internal`. Rollen äger sin databas och inget annat.
- Schemamigreringar med **Flyway**, ren SQL i `src/main/resources/db/migration`
  ([D-033](docs/decisions.md)). Körs av appen vid uppstart ([D-039](docs/decisions.md)).
  Handkör aldrig DDL.
- Publik bas-URL sätts explicit i env-filen ([D-022](docs/decisions.md)). Bygg absoluta
  länkar från den, aldrig från `X-Forwarded-Host`.
- E-post går över SMTP till AhaSend ([D-029](docs/decisions.md)). Leverantören är fyra
  miljövariabler, inget mer — använd aldrig en leverantörs-SDK eller REST-API, det låser
  fast utan att ge något.
- **Alla mejl är ren text**, aldrig HTML, och formuleringarna är fastställda i
  [D-035](docs/decisions.md). Skriv inte om dem på egen hand.
- Databasen är en **central Postgres 18 på värden** ([D-045](docs/decisions.md)) där
  ministra äger sin egen databas och inget annat. `ddl-auto=validate`, aldrig `update`.
  Schemaändringar sker med versionerade migreringar.

## Språk

- **Identifierare på engelska** — klasser, metoder, fält, testnamn, commit-meddelanden.
  Testnamn är identifierare: de skrivs på kommandorad och hamnar i CI-rapporter, så inga
  å, ä eller ö där ([D-004](docs/decisions.md)). Undantag: stegnamnen i
  `.github/workflows/` är svenska, som i lektionarium ([D-038](docs/decisions.md)).
- **Kommentarer och javadoc på svenska**, som resten av dokumentationen. Domänorden och
  D-hänvisningarna ska inte behöva översättas mitt i koden.
- **UI på svenska** — all text som en användare ser, inklusive validerings- och felmeddelanden.
- Domänbegrepp behåller sin svenska form i UI men översätts i kod: `Förfrågan` → `Poll`,
  `Deltagare` → `Participant`, `Svar` → `Response`.

## Domänordlista

Förbedjare, textläsare, ministranter, kyrkvärdar och lovsångsledare är **samma sak i
modellen**. Det finns ingen roll-typ och ska inte finnas ([D-005](docs/decisions.md)).
Vad förfrågan gäller framgår enbart av dess titel — "Sakristaner fram till påsk".

| Svenska (UI)     | Kod            | Vad det är                                                                                                                                            |
|------------------|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| Förfrågan / plan | `Poll`         | **Entitet.** Titel, kommentar, start- och slutdatum, giltig till, skaparens förnamn och e-post, två tokens                                            |
| Deltagare        | `Participant`  | **Entitet.** Någon som svarat, identifierad enbart med förnamn                                                                                        |
| Svar             | `Response`     | **Entitet.** En rad per dag: deltagare, datum, `Availability`                                                                                         |
| —                | `Availability` | **Enum.** `CAN` / `IF_NEEDED` / `CANNOT` (grön / gul / röd)                                                                                           |
| Gudstjänstdag    | `ServiceDay`   | **Beräknad record**, aldrig persisterad — datum och kyrkoårsnamn ([D-018](docs/decisions.md)). Liturgisk färg visas inte ([D-037](docs/decisions.md)) |

Enum-konstanterna är engelska som all annan kod; översättningen till "Kan", "Om det
behövs" och "Kan inte" sker i vyn.

**Appen vet inte hur många som behövs per dag, eller hur många som fått länken**
([D-005](docs/decisions.md)). Det avgör skaparen utanför appen. Alltså finns ingen
förloppsindikator, inget "3 av 5 har svarat", ingen varning för otäckta dagar och
inget mått på om en förfrågan är klar. Appen visar de svar som kommit in — om de räcker
är en mänsklig bedömning.

Regler som följer av besluten, och som styr datamodellen direkt:

- **Namn är unika inom en förfrågan.** Unik-constraint på `(poll_id, name)` i databasen,
  skiftlägesokänslig och trimmad — inte bara validering i koden.
- **Svar är oföränderliga.** Ingen redigering, inga uppdaterings-endpoints. Den som
  svarat fel lämnar ett nytt svar som "Anna Ny"; det gamla ligger kvar och ska inte
  städas bort eller markeras. Alla deltagare ser allas svar med namn; det är avsiktligt.
- **Alla dagar måste besvaras** ([D-019](docs/decisions.md)). Tre tillstånd, aldrig
  `null`, inget "obesvarat". Validera på servern.
- **Ingen dagstabell** ([D-018](docs/decisions.md)). Listan härleds från API:t;
  `Response` bär sitt datum. Bara skaparens bortvalda dagar lagras, i `excluded_day`
  ([D-042](docs/decisions.md)). Validera vid inlämning att datumet ingår i förfrågan.
- **Dagarna väljs innan förfrågan skapas** ([D-042](docs/decisions.md)) och går inte att
  ändra efteråt. Bygg ingen efterhandsredigering — misstag rättas genom att radera och
  göra om.
- **Skaparen får lägga till egna datum** ([D-043](docs/decisions.md)), lagrade i
  `extra_day`. `ServiceDay.name` får därför saknas — men gör det **inte** till en egen typ:
  lektionarium ska med tiden namnge även vardagar, och då ska de dagarna få sina namn utan
  att någon typ ändras.
- **Perioden får vara högst ett halvår** ([D-020](docs/decisions.md)) och startdagen måste
  ligga i framtiden ([D-036](docs/decisions.md)). Validera på servern — formuläret går att
  posta förbi.
- **Skaparen är deltagare som alla andra** ([D-025](docs/decisions.md)). `Poll` bär
  `creatorName` och `creatorEmail`, men `Participant`-raden skapas först när skaparen
  faktiskt skickar in ett svar — aldrig en tom rad vid skapandet.

## Lektionarium-API:t

Ingen javadoc finns. Verifierad API-yta (paket `lectio.cal`):

```java
var factory = new LiturgicalYearFactory();
SortedMap<LocalDate, Day> days = factory.getDaysOfCalendarYear(2026);
```

- `Day` är ett interface med `name()`, `date()`, `color()`, `memorials()`.
  Implementationer: `HolyDay` och `OrdinaryDay`, båda records.
- `getDaysOfCalendarYear` returnerar **endast liturgiskt märkta dagar** (69 st år 2026),
  inte alla 365.
- **Filtrera på `day instanceof HolyDay`, aldrig på `DayOfWeek.SUNDAY`**
  ([D-040](docs/decisions.md)). Kyrkan firar gudstjänst även juldagen, annandagarna,
  Långfredagen och Alla helgons dag. Vardagarna i Stilla veckan är `OrdinaryDay` och
  faller bort av sig själva. Varje söndag är en `HolyDay` — verifierat 2026–2030, ungefär
  66 gudstjänstdagar per år varav 52 söndagar.
- En period som korsar ett årsskifte kräver att du slår ihop två anrop.
- `LiturgicalYear.FIRST_SUPPORTED_YEAR == 2004`. Validera indata mot det.
- `LiturgicalYearFactory` cachar internt (`LruCache`) — skapa en `@Bean` och återanvänd.
- **`getCurrentDay` ger närmast föregående liturgiska dag**, inte den man frågar om: en
  tisdag i fastan ger söndagen före, med söndagens datum. Jämför datumet i svaret med det
  efterfrågade innan du använder namnet. `ChurchCalendar.dayAt` gör det.

Dagens namn kommer alltid från API:t. Hårdkoda aldrig kyrkoårsnamn eller egen
påskberäkning.

Vår typ heter `ServiceDay`, inte `HolyDay`: begreppet är "en dag som behöver bemannas",
inte en liturgisk kategori, och namnet skulle krocka med `lectio.cal.HolyDay`.

## Integritet och säkerhet — icke förhandlingsbart

Detta är själva anledningen till att appen byggs. Behandla reglerna som krav, inte råd.

1. **Förnamn är personuppgifter.** GDPR gäller även om vi bara lagrar förnamn.
2. **Länkar är capability-URL:er** — den som har länken kommer in. Id:t ska vara minst
   128 bitar från `SecureRandom`, base64url-kodat. Använd inte löpnummer, inte UUIDv7
   (tidsordnad och delvis gissbar), inte UUIDv4-from-`Random`.
3. **Två separata tokens per förfrågan:** ett svarstoken (delas ut) och ett
   administrationstoken (endast skaparen). Att kunna svara får aldrig innebära att man
   kan radera. Båda visas för skaparen efter skapandet och mejlas till hen
   ([D-016](docs/decisions.md)).
4. **`X-Robots-Tag: noindex`** på alla förfrågnings-URL:er, plus en `robots.txt` som
   blockerar allt. Församlingens listor ska inte gå att googla.
5. **Ingen uppräkning.** Okänt eller utgånget token ger samma svar som fel token.
   Inga listnings-endpoints. Ingen sökning.
6. **E-postadresser lagras endast för skaparen**, aldrig för deltagare. Deltagarnas
   adresser existerar bara i skaparens eget e-postprogram. Den genererade
   `mailto:`-länken har skaparens egen adress i To och inga andra mottagare — se
   [D-012](docs/decisions.md). Fullständig förteckning över vad som lagras finns i
   [D-028](docs/decisions.md); tillkommer något ska den listan uppdateras i samma ändring.
7. **Gallring är kod, inte rutin.** När "Giltig till" passeras upphör svarsmöjligheten
   och förfrågan raderas med alla svar. Ett schemalagt jobb, med test.
8. Logga aldrig namn, e-postadresser eller tokens — inte ens på DEBUG.
9. **Att alla deltagare ser allas namn och svar är ett medvetet val** ([D-010](docs/decisions.md)),
   inte ett läckage. Den som har svarslänken ser hela gruppens tillgänglighet. Bygg inte
   bort det, men bygg inte heller ut det — ingen export, ingen delning vidare.
10. **Skapandet hastighetsbegränsas per IP** ([D-017](docs/decisions.md)) — det är en
   oautentiserad utgång som mejlar till en användarangiven adress. Bakom reverse proxy
   måste klientens IP läsas ur `X-Forwarded-For`, annars slår gränsen mot alla samtidigt.

## Schemalagda jobb

Tre stycken ([D-024](docs/decisions.md), [D-030](docs/decisions.md)).

**Varje minut:** tömning av `outbox_email`. Det är det **enda** stället i appen som rör
`JavaMailSender` — ingen annan kod skickar mejl. Backoff mellan försök, tak på antalet,
och raden raderas när mejlet gått iväg eftersom brödtexten innehåller förnamn.

**Nattligt pass 22:00 `Europe/Stockholm`**, två steg i denna ordning:

1. **Daglig sammanfattning:** en outbox-rad per skapare med vilka som svarat på vilka av
   hens förfrågningar. Aldrig ett mejl per svar. `notified_at` på `Response` sätts i
   **samma transaktion** som outbox-raden skrivs — det är hela poängen med outboxen.
   Inget nytt, ingen rad.
2. **Gallring** av utgångna förfrågningar.

Ordningen spelar roll: en förfrågan som går ut idag ska ge en sista sammanfattning innan
den raderas. "Giltig till" verkställs alltså av det här jobbet, inte på sekunden
([D-021](docs/decisions.md)) — lova ingen exakt tidpunkt i gränssnittet.

Lägg inte till fler jobb utan att fråga.

## Java-stil

- Records för DTO:er, värdeobjekt och API-svar. Sealed interfaces där varianterna är kända.
- Pattern matching i `switch`, inte `instanceof`-kedjor.
- `var` när typen framgår av högerledet; annars explicit typ.
- Konstruktorinjektion, aldrig `@Autowired` på fält. Inget Lombok.
- `Optional` som returtyp, aldrig som fält eller parameter.
- **JSpecify.** Varje paket är `@NullMarked` (se `package-info.java`), så icke-null är
  normalfallet. Det som verkligen kan vara null säger det med `@Nullable` — entiteters
  `id` före persistering, `ServiceDay.name`, `Poll.comment`, valfria request-parametrar.
  Sätt aldrig `@Nullable` för att tysta en varning; sätt den för att det är sant.
- **Lita inte blint på IDE:ns inspektioner på JPA-entiteter.** "Field can be final",
  "field can be local variable" och "collection updated but never queried" är fel där:
  Hibernate läser och skriver fälten via reflektion, och `final` bryter proxying.
  Detsamma gäller "cannot resolve MVC view" (IDE:n känner inte jte) och "no beans of
  JavaMailSender" (autokonfigurerad, villkorad på `spring.mail.host`). Se FRAGOR.md.
- `MinistraApplication.main` är avsiktligt package-private instansstil (Java 25) — behåll.
- Paketstruktur efter funktion, inte lager: `ministra.poll`, `ministra.calendar`,
  `ministra.mail` — inte `controller`, `service`, `repository`.

## Tester

- Domänlogik testas som ren JUnit utan Spring-kontext.
- Webblagret med `@WebMvcTest`. Persistens med `@DataJpaTest` + Testcontainers mot
  riktig Postgres, inte H2. Pinna `postgres:18` — produktion kör major 18.
- Ärv `PostgresTest` för allt som behöver databas. Containern startas i ett statiskt block
  och delas mellan testklasser. Använd **inte** `@Testcontainers` och `@Container` — då
  stoppas den efter första klassen och nästa får vänta ut anslutningspoolens timeout.
- `@DataJpaTest` ger bara repositories. Övriga bönor kommer från `TestBeans`, och Flyway
  måste dras in med `@ImportAutoConfiguration(FlywayAutoConfiguration.class)` — annars
  finns inget schema att validera mot.
- `@WebMvcTest` renderar ingenting utan
  `@ImportAutoConfiguration({JteAutoConfiguration.class, ServletJteAutoConfiguration.class})`.
  Symtomet är tomma svar utan felmeddelande.
- `RateLimiter` är en singleton i testkontexten. Ge varje test en egen klientadress med
  `X-Forwarded-For`, annars påverkar de varandra.
- Både produktions- och utvecklingsmiljö kör Podman i stället för Docker. Testcontainers
  behöver då podman-socketen: `systemctl --user enable --now podman.socket` och
  `DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock`. Rootless podman kräver
  oftast även `TESTCONTAINERS_RYUK_DISABLED=true`.
- `@SpringBootTest` endast för genuina end-to-end-fall. Det är långsamt — sprid det inte.
- Varje bugfix får ett test som failar utan fixen.

## Klart betyder klart

Innan du säger att något är färdigt:

1. `./mvnw verify` går grönt.
2. Nya tester finns för ny logik.
3. Ingen ny personuppgift lagras utan att avsnittet ovan följts.
4. Rapportera det som inte gjordes eller inte fungerade — utelämna aldrig ett rött test.

## Arbetssätt

- Committa inte utan att bli ombedd. Pusha aldrig av eget initiativ.
- Uppdatera `docs/decisions.md` när ett arkitekturval fattas eller ändras.
- Frågor som dyker upp mitt i ett arbete noteras i `FRAGOR.md` i stället för att avbryta.
- Öppna produktfrågor finns längst ned i `docs/product.md`. **Bygg inte bort dem genom
  att gissa** — fråga.

## Icke-mål

Scopet är litet även om ambitionen på användbarheten är hög. Detta ska inte byggas
oombett:

- Inloggning, användarkonton, lösenord, OAuth
- Redigering av inlämnade svar ([D-010](docs/decisions.md))
- Admin-gränssnitt, statistik, rapporter
- Mobilapp (en bra webbvy räcker), kalendersynk, ICS-export, push-notiser
- Internationalisering — svenska räcker
- Återkommande förfrågningar, mallar, kopiering av gamla planer

Förslaget på schema ([D-046](docs/decisions.md)) är ett hjälpmedel, inte ett beslut:
appen föreslår, skaparen avgör, och ingenting lagras. Bygg inte ut det till något som
sparar, delar eller mejlar ett schema, eller som visar det för gruppen.

Uttalade v2-kandidater — bra idéer, men inte nu, och inte utan att det efterfrågas:

- Layoutad PDF för utskrift av schema.
- Att spara eller dela ett färdigt schema.
