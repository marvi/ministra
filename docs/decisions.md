# Beslut

Kort logg över arkitekturval. Ett beslut per post: vad, varför, och vad som förkastades.
Lägg till nya poster längst ned. Ändra ett beslut genom att markera det som ersatt och
skriva en ny post — radera inte historik.

---

## D-001 — PostgreSQL med Spring Data JPA
**2026-09-09 · Gäller**

Persistens sker med PostgreSQL och `spring-boot-starter-data-jpa`, som redan ligger i
`pom.xml`.

Datamängden är liten och gallras löpande, så alternativet var handskriven SQL via
`JdbcClient`, eller till och med en SQLite-fil. JPA valdes ändå: det är den väg som ger
minst friktion för schemamigreringar och relationer, och prestanda är inte en faktor vid
den här skalan.

**Målversion är PostgreSQL 16** — en befintlig instans i produktion, `postgresql16-server
16.15` från PGDG, som körs på värden och delas med annat. Se
D-015 för vad det innebär.

Testa mot riktig Postgres via Testcontainers, inte H2 — annars fångas inte skillnader i
datumhantering och constraints. Pinna imagen till samma major: `postgres:16`. Testa inte
mot 17 eller 18.

## D-002 — Drift på egen server som Podman-quadlet under systemd
**2026-09-09 · Gäller**

Appen körs i en container på egen server, startad av systemd via en quadlet
(`ministra.container` i `/etc/containers/systemd/` eller `~/.config/containers/systemd/`
vid rootless drift). Ingen PaaS, ingen lös jar, ingen `docker compose`.

Konsekvenser att arbeta efter:

- **Deployerbar artefakt är en OCI-image**, inte en jar. Bygget måste producera en image.
- **Konfiguration via miljövariabler ur en env-fil.** Quadleten pekar ut den med
  `EnvironmentFile=`; `Environment=`-rader i unit-filen används inte. Se D-014.
- Läs aldrig konfiguration från en fil i arbetskatalogen, och lägg ingen
  `application-prod.properties` i imagen.
- **Loggar till stdout/stderr**, så att `journalctl` fungerar. Skriv inte till loggfiler.
- **Hälsokontroll** via Spring Boot Actuator, kopplad till quadletens `HealthCmd=`.
- Databasen driftas av oss och EU-placering är enkel att garantera. Bind inte in
  plattformsspecifika beroenden (managed-tjänsters SDK:er, leverantörsspecifika
  secret stores).

Följdbesluten är fattade: avbilden byggs enligt D-032, Podman körs rootless enligt D-034,
och vägen till värdens Postgres beskrivs i D-015.

## D-003 — Server-renderad HTML med jte och htmx
**2026-09-09 · Gäller**

Ingen SPA, inget JS-byggsteg, ingen npm. jte-mallar i `src/main/jte`, htmx för partiella
uppdateringar, handskriven CSS.

Appen har fyra vyer och ingen klientstate värd namnet. Ett frontendramverk skulle kosta
mer i underhåll än det ger.

htmx används på ett ställe: att lägga till ett eget datum i dagvalet byter bara ut listan
i stället för att ladda om sidan ([D-043](#d-043--skaparen-får-lägga-till-egna-datum)).
Varje sådan användning ska ha en fungerande väg utan JavaScript — htmx är en förbättring,
aldrig en förutsättning.

Biblioteket serveras från appen som `/js/htmx-2.0.4.min.js`. Inga externa resurser laddas
vid körning, och filnamnet bär versionen så att en uppgradering blir en synlig ändring.

## D-004 — Identifierare på engelska, kommentarer och gränssnitt på svenska
**2026-09-09 · Gäller. Preciserat samma dag.**

Se avsnittet Språk i [AGENTS.md](../AGENTS.md). Motivet är att koden ska följa
Java-konventioner medan användarna är svensktalande församlingsmedlemmar.

> **Preciserat 2026-09-09.** Beslutet sa ursprungligen "kod på engelska" och räknade upp
> kommentarer och testnamn. Koden som skrevs följde det inte: javadoc och kommentarer blev
> svenska, och 78 av 98 testmetoder fick svenska namn med å, ä och ö. Inkonsekvensen
> löstes åt två håll.
>
> **Kommentarer och javadoc är svenska.** Det är dokumentation, och all annan
> dokumentation — beslutsloggen, produkttexten, AGENTS.md — är svensk. Domänord som
> "förbedjare" och "kyrkoåret" och hänvisningar som "D-013" ska inte behöva översättas
> mitt i koden. Regeln ändrades.
>
> **Testnamn är engelska.** De är identifierare: de skrivs på kommandorad
> (`-Dtest=Klass#metod`), hamnar i CI-rapporter och lever i klassfiler. Java tillåter
> å, ä och ö där, och bygget är UTF-8 — men ett tyst beroende på att varje verktyg som
> någonsin rör koden talar UTF-8 är inget att bära när alternativet är gratis. Koden
> ändrades: alla 98 döptes om. Svenska utan prickar — `inte_ar_sondagar` — övervägdes
> och avvisades som det sämsta av båda.
>
> Gränsen är alltså: **det kompilatorn ser är engelskt och ASCII; det människor läser
> är svenskt.** Samma princip som [D-038](#d-038--svenska-i-ci-filerna) för CI-filerna.

## D-005 — En roll, inte fem
**2026-09-09 · Gäller**

Förbedjare, textläsare, ministranter, kyrkvärdar och lovsångsledare modelleras inte var
för sig. De är samma sak: en grupp människor som ska täcka gudstjänster. Vilken sorts uppgift
det gäller framgår enbart av förfrågans titel — "Sakristaner fram till påsk" — och inte
av någon typ i modellen. Det finns ingen `Role`, och det ska inte tillkomma någon.

**Appen vet inte hur många som behövs per dag, och inte heller hur många som fått
länken.** Det avgör skaparen utanför appen. Det finns inget fält för det, och det ska
inte läggas till.

Följden är att appen inte kan räkna ut om en dag är täckt eller om alla har svarat.
Bygg därför aldrig:

- förloppsindikator eller "3 av 5 har svarat"
- varning för otäckta dagar
- något som helst mått på om en förfrågan är "klar"

Appen visar de svar som kommit in. Att avgöra om de räcker är en mänsklig bedömning.

## D-006 — Länkar är capability-URL:er med separata tokens
**2026-09-09 · Gäller**

Åtkomst sker enbart via länk, utan konton. Varje förfrågan har två oberoende tokens: ett
för att svara och ett för att administrera.

Att slå ihop dem vore enklare, men då kan varje deltagare radera hela förfrågan. Se
avsnittet Integritet och säkerhet i [AGENTS.md](../AGENTS.md) för de fullständiga
kraven på tokengenerering och skydd mot uppräkning.

## D-007 — Kyrkoårsdata endast från lektionarium-api
**2026-09-09 · Gäller**

Söndagsnamn, datum och liturgisk färg hämtas alltid från `io.marvi:lektionarium-api`.
Ingen egen påskberäkning, inga hårdkodade namnlistor.

API:t levereras från det privata repot `https://maven.marvi.work` och saknar javadoc.
Den verifierade API-ytan är dokumenterad i [AGENTS.md](../AGENTS.md).

## D-008 — Användarvänlig före minimal
**2026-09-09 · Gäller**

Målet är inte minsta möjliga app utan en app där det är **svårt att göra fel**. Färre val
per skärm, tydliga förval, ingen funktion som kräver att användaren förstår modellen
bakom.

Mobil och skärm är likvärdiga. Inte "fungerar även på mobil" — lika bra. Det utesluter
lösningar som bara finns vid hover eller högerklick, och kräver träffytor som fungerar
med tumme.

Detta ersätter den tidigare formuleringen "superenkel", som i praktiken lästes som
"få funktioner" i stället för "lätt att använda".

## D-009 — Namn är unika inom en förfrågan
**2026-09-09 · Gäller**

Deltagare identifieras enbart med förnamn, och namnet måste vara unikt inom förfrågan.
Vid kollision visas en varning och personen får själv särskilja sig — "Anna J".

Unik-constraint i databasen på `(poll_id, name)`, inte bara validering i koden.
Jämförelsen ska vara skiftlägesokänslig och trimma blanksteg, annars slinker "anna" och
"Anna " igenom.

Alternativet vore ett genererat deltagar-id, men det kräver att deltagaren håller reda
på något — och hela poängen är att man bara klickar på en länk och skriver sitt namn.

## D-010 — Svar är oföränderliga, och alla ser alla
**2026-09-09 · Gäller**

Ett inlämnat svar kan inte redigeras. Utan konton finns ingen säker väg tillbaka till
just ditt svar, och att låta vem som helst med länken redigera vems svar som helst vore
sämre.

Den som svarat fel lämnar ett nytt svar under ett särskiljande namn — "Anna Ny".
Framadate fungerar likadant och det har fungerat bra i praktiken. Det gamla svaret ligger
kvar i listan, och det är accepterat: skaparen ser att det är ersatt. Bygg alltså inte
någon mekanism för att städa bort eller markera överspelade rader.

Kollisionsvarningen från [D-009](#d-009--namn-är-unika-inom-en-förfrågan) står kvar —
den är poängen, inte ett hinder. Den är det som får Anna att skriva "Anna Ny" i stället
för att undra varför det inte går att spara.

Alla deltagare ser samtliga svar med namn. Det är avsiktligt — syftet är att se var
luckorna finns. Konsekvensen är att den som har länken ser hela gruppens tillgänglighet,
och det är en accepterad exponering, inte en bugg.

## D-011 — Två schemalagda jobb
**2026-09-09 · Gäller**

> **Ändrat av D-030:** jobben är numera tre. Ett tömningsjobb för outbox tillkom.

Appen har exakt två återkommande jobb:

1. **Daglig sammanfattning** vid dagens slut. Ett mejl per skapare, som listar vilka som
   svarat på vilka av hens förfrågningar sedan förra utskicket. Aldrig ett mejl per svar.
2. **Gallring.** När "Giltig till" passeras upphör svarsmöjligheten och förfrågan
   raderas, med alla svar. **Ingen förvarning skickas.** Planeringen tar en vecka eller
   två, så en till två månader är gott om tid, och ett påminnelsemejl vore bara störande.

Båda kör i tidszonen `Europe/Stockholm` — "dagens slut" är inte UTC. Båda ska ha test.
Sammanfattningen måste vara idempotent: ett omstartat jobb får inte mejla samma svar två
gånger.

## D-012 — `mailto:` med skaparen som mottagare
**2026-09-09 · Gäller**

Appen genererar en `mailto:`-länk med ämnesrad och färdig brödtext — titel, kommentar,
svarslänken och en kort förklaring. **Skaparens egen adress ligger i To-fältet.**
Deltagarnas adresser fyller skaparen själv i, i sin e-postklient — vi har dem inte och
vill inte ha dem.

Skaparens adress i To gör att fältet inte står tomt, vilket annars ser ut som ett fel.
Att skicka till sig själv och lägga övriga i Bcc är dessutom det som bäst skyddar
deltagarnas adresser från varandra, men det är inget appen kan tvinga fram.

Procentkoda body korrekt, inklusive radbrytningar som `%0A`. Håll texten kort och lägg
svarslänken tidigt — vissa klienter kapar långa `mailto:`-URL:er.

## D-013 — De tre valen förklaras med synlig text
**2026-09-09 · Gäller**

Innebörden av grönt, gult och rött står som synlig brödtext ovanför listan — inte i en
hover, inte bakom en info-ikon. Det är precis det deltagaren ombeds göra, och hover
existerar inte på pekskärm ([D-008](#d-008--användarvänlig-före-minimal)).

Texten:

> Ange för varje dag om du **kan** (grön), **kan om det behövs** (gul) eller
> **inte kan** (röd). Mittenvalet betyder att du helst avstår, men ställer upp om ingen
> annan kan.

Etiketterna står kvar vid knapparna. Färgen är en förstärkning, aldrig den enda bäraren
av innebörden.

> **Ändrat 2026-09-09 av [D-040](#d-040--alla-gudstjänstdagar-inte-bara-söndagar):**
> texten sa tidigare "för varje söndag". Listan innehåller även jul, påsk och andra
> helgdagar, så ordet är utbytt mot "dag".

## D-014 — Konfiguration i en env-fil, inte i unit-filen
**2026-09-09 · Gäller**

All körtidskonfiguration — databas-URL, databaslösenord, SMTP-uppgifter, appens publika
bas-URL — ligger i en env-fil på servern, som quadleten läser med `EnvironmentFile=`.
Inga `Environment=`-rader i unit-filen.

Skälen: konfigurationen kan ändras utan att unit-filen redigeras, unit-filen kan
versionshanteras medan env-filen inte kan det, och hemligheter hamnar inte i något som
`systemctl cat` skriver ut.

Praktiska krav:

- Filen ägs av root med läge `0600` (rootful) respektive av tjänsteanvändaren (rootless).
- Den ligger **aldrig** i repot. Checka in en `ministra.env.example` med tomma värden
  som dokumentation av vilka variabler som finns.
- Spring läser miljövariabler direkt, så `SPRING_DATASOURCE_PASSWORD` mappar till
  `spring.datasource.password` utan extra kod. Uppfinn ingen egen konfigurationsläsare.
- Formatet är `NYCKEL=värde`, en per rad. systemd tolkar inte skalsyntax — inga
  `$OTHER_VAR`, inga kommandosubstitutioner, och citattecken blir en del av värdet om de
  inte omsluter hela värdet.

Känd avvägning: värdena syns i containerns miljö och kan läsas av root via
`podman inspect` eller `/proc/<pid>/environ`. `podman secret` eller systemd credentials
skulle vara snävare. Det är accepterat på en server vi själva driftar — går kraven upp är
det databaslösenordet och SMTP-lösenordet som flyttas först.

## D-015 — Befintlig Postgres 16 på värden, delad instans
**2026-09-09 · Gäller**

Appen använder en **redan körande** PostgreSQL-instans i produktion:
`postgresql16-server 16.15` (PGDG), installerad på värden — inte i en container, inte
en instans vi äger ensamma.

Det är en delad produktionsdatabas. Konsekvenser som är krav, inte råd:

- **Egen databas och egen roll** för Ministra. Appens roll får rättigheter bara på sitt
  eget schema. Aldrig superuser, aldrig `postgres`-rollen.
- **`spring.jpa.hibernate.ddl-auto=validate`**, aldrig `update` och aldrig `create`.
  Hibernate får inte ändra schemat i en delad produktionsinstans.
- **Schemaändringar sker med Flyway** (D-033), versionerade i repot och körda av appen
  vid uppstart (D-039). Rollen måste därför få ändra sitt eget schema — men ingenting
  annat i den delade instansen.
- Gallringsjobbet raderar bara Ministras egna tabeller. Inga `DROP`, inga
  `TRUNCATE` mot något annat.
- Testcontainers kör `postgres:16` för att matcha major-versionen.

Containern når värden via `host.containers.internal`, satt med `AddHost=` i quadleten
(D-034). Det kräver att `listen_addresses` och `pg_hba.conf` på värden släpper in
podman-nätets adress — en ändring i en produktionsdatabas som görs medvetet, inte av
misstag. Se `deploy/README.md`.

## D-016 — Skaparen får länkarna både på skärmen och per mejl
**2026-09-09 · Gäller**

Efter att en förfrågan skapats visas **båda** länkarna på skärmen med kopiera-knapp:
svarslänken som ska delas ut, och administrationslänken som är skaparens egen. Samma två
länkar skickas dessutom i ett mejl till skaparens adress.

Mejlet gör e-postadressen till det den utges för att vara — vägen tillbaka — och
validerar adressen på köpet: kommer inget mejl fram är den felskriven.

Följden är att skapandet är en oautentiserad utgång som skickar mejl till en
användarangiven adress. Den ska därför alltid hastighetsbegränsas, se D-017.
Mejlet får inte innehålla mer fritext än titeln — ju mindre attackerarstyrd text som går
ut från vår avsändardomän, desto bättre för leveransbarheten.

## D-017 — Hastighetsgräns per IP på skapandet
**2026-09-09 · Gäller**

Skapa-sidan är öppen — ingen inloggning, ingen lösenfras — men antalet skapade
förfrågningar per IP och tidsenhet begränsas. Riktvärde: tio per timme.

Det räcker eftersom missbrukspotentialen är liten. Deltagarnas adresser finns aldrig i
systemet, det finns inga lösenord att stjäla, tokens är ogissbara och förfrågningar
raderar sig själva. Kvar finns bara två saker: att belasta servern, och att använda vår
avsändardomän för att mejla en godtycklig adress via D-016. Båda stoppas av en gräns som
ingen verklig användare någonsin märker.

Gränsen ska räknas i appen, inte förutsätta en reverse proxy. Bakom proxy måste
klientens IP läsas ur `X-Forwarded-For` — annars ser alla ut att komma från samma adress
och gränsen slår mot alla på en gång.

Räkneverket ligger **i minnet**, aldrig i databasen — vi sparar inga IP-adresser
([D-028](#d-028--personuppgifter-vi-lagrar)). En omstart nollställer det, och det är
accepterat. Skulle appen någon gång köras i mer än en instans måste det tänkas om, men
någon sådan plan finns inte.

## D-018 — Dagarna härleds, svar bär sitt datum
**2026-09-09 · Gäller**

Det finns **ingen tabell för dagarna**. Förfrågan sparar startdatum och slutdatum.
Listan räknas fram från lektionarium-API:t vid varje rendering, och varje svarsrad bär
sitt eget datum. Undantagen i [D-041](#d-041--skaparen-får-ta-bort-dagar-innan-någon-svarat)
är det enda som lagras.

`ServiceDay` är alltså en record som beräknas — inte en entitet. Persistera den inte,
cacha den inte i databasen.

Vid inlämning måste varje inskickat datum valideras: det ska vara en dag som ingår i
förfrågan. Annars kan vilket datum som helst postas in.

Avvägningen är att ett framtida API-byte kan ändra en dags *namn* mitt i en pågående
förfrågan. Datumen och svaren påverkas inte, och en förfrågan lever bara en till två
månader. Det är accepterat och väger lättare än en extra tabell.

## D-019 — Alla dagar måste besvaras
**2026-09-09 · Gäller**

Ett svar kan inte skickas in med luckor. Varje dag i listan måste ha ett av de tre valen.

Därför finns exakt tre tillstånd i modellen. Inget "obesvarat", ingen `null`, ingen
fjärde färg att förklara i gränssnittet. Den som tittar på listan vet att en tom ruta
inte finns — allt som visas är någon som tagit ställning.

Konsekvens: en lång period blir jobbig att fylla i, eftersom varje rad kräver ett aktivt
val. Det är ett argument både för taket på periodens längd och för att skaparen ska kunna
ta bort dagar ([D-041](#d-041--skaparen-får-ta-bort-dagar-innan-någon-svarat)) — se den öppna frågan i
[product.md](product.md).

Valideringen ska ske på servern, inte bara i formuläret.

## D-020 — Perioden får vara högst ett halvår
**2026-09-09 · Gäller**

Från första till sista dagen får det vara högst sex månader, alltså omkring 33
gudstjänstdagar. Valideras på servern.

Ett halvår rymmer en termin, vilket är den naturliga planeringshorisonten i en församling.
Taket behövs eftersom varje dag måste besvaras aktivt ([D-019](#d-019--alla-dagar-måste-besvaras)) —
utan gräns kan någon skapa en lista med hundratals obligatoriska val, och den fylls
aldrig i på en telefon.

Nedre gräns finns också: startdatum får inte ligga före `LiturgicalYear.FIRST_SUPPORTED_YEAR`
(2004). Någon övre årsgräns har API:t inte — år 2200 fungerar — så den kommer enbart
härifrån.

## D-021 — "Giltig till" verkställs av nattjobbet, inte på sekunden
**2026-09-09 · Gäller**

Förfrågan fungerar som vanligt tills gallringsjobbet kör och raderar den. Ingen separat
låsning vid exakt tidpunkt, ingen "utgången"-vy.

Det innebär att "Giltig till" är ungefärligt: en förfrågan kan ta emot svar några timmar
efter datumet. Det är accepterat — enklare kod, och ingen verklig skada.

Konsekvens för gränssnittet: **lova inte en exakt tidpunkt.** Skriv "raderas efter" och
inte "raderas klockan". Formulera det inte så att någon tror att sista svarschansen är på
minuten.

## D-022 — Publik bas-URL konfigureras explicit
**2026-09-09 · Gäller**

Appen står bakom en reverse proxy. Den publika adressen läses ur en miljövariabel i
env-filen, exempelvis `MINISTRA_BASE_URL=https://ministra.example.se`.

Alla absoluta länkar — i mejl och i `mailto:`-texten — byggs från det värdet. Härled dem
inte ur `X-Forwarded-Host` eller `ServletUriComponentsBuilder`: en felkonfigurerad proxy
ger då länkar som pekar på `localhost:8080`, och det upptäcks först när någon klagar på
ett mejl som redan gått ut.

Klientens IP till hastighetsgränsen ([D-017](#d-017--hastighetsgräns-per-ip-på-skapandet))
läses däremot ur `X-Forwarded-For`, och proxyn måste konfigureras att sätta den.

## D-023 — Svarssidan visar hela listan direkt
**2026-09-09 · Gäller**

Den som klickar på svarslänken ser med en gång alla dagar och allas hittills lämnade
svar. Namnrutan står överst, men listan döljs inte bakom den.

Man ska kunna se vad man ger sig in i innan man börjar, och man ser direkt om ens eget
namn redan står där — vilket är precis den situation kollisionsvarningen i
[D-009](#d-009--namn-är-unika-inom-en-förfrågan) finns för.

## D-024 — Ett nattligt jobb, sammanfattning före gallring
**2026-09-09 · Gäller**

De två jobben i [D-011](#d-011--två-schemalagda-jobb) körs i **samma nattliga pass**
klockan 22:00 `Europe/Stockholm`, i denna ordning:

1. Daglig sammanfattning
2. Gallring av utgångna förfrågningar

Ordningen är inte godtycklig: en förfrågan som går ut idag ska ge skaparen en sista
sammanfattning av dagens svar innan den raderas. Omvänd ordning tappar den tyst.

> **Ändrat av D-030:** stycket nedan om att jobbet självt mejlar gäller inte längre.
> Sammanfattningsjobbet skriver en outbox-rad och sätter `notified_at` i samma
> transaktion; ett annat jobb skickar. Ordningen mellan sammanfattning och gallring
> gäller fortfarande.

Idempotens löses med ett `notified_at` på `Response`. Jobbet plockar svar där det är
`null`, mejlar, och sätter det. Ett omstartat jobb mejlar då inte om samma svar.

Har inget nytt kommit in skickas inget mejl. Inga "det hände ingenting"-utskick.

## D-025 — Skaparen anger namn och är själv deltagare
**2026-09-09 · Gäller**

Skapa-formuläret frågar efter **förnamn och e-postadress**. Skaparen är nästan alltid
själv med i gruppen, och ska kunna fylla i sin tillgänglighet direkt när förfrågan är
skapad — utan att gå omvägen via svarslänken.

Modellen: `Poll` bär `creatorName` och `creatorEmail`. Namnet används för att tilltala i
mejlen och för att förifylla namnrutan när skaparen fyller i sin egen tillgänglighet.

Skaparen blir en `Participant` på precis samma sätt som alla andra, **först när hen
faktiskt skickar in ett svar**. Skapa ingen tom deltagarrad vid skapandet — den skulle
bryta mot [D-019](#d-019--alla-dagar-måste-besvaras), som kräver att varje dag är
besvarad.

Skaparens namn omfattas av namnunikheten i [D-009](#d-009--namn-är-unika-inom-en-förfrågan)
som alla andra.

Det förifyllda namnet **får skrivas över**. Gör skaparen det blir hen en annan deltagare,
och `creatorName` är fortfarande ledigt för någon annan. Det är avsiktligt: rutan är ett
förslag, inte en låsning.

## D-026 — Radering bekräftas med ett steg
**2026-09-09 · Gäller**

Raderaknappen visar en tydlig varning med "Radera" och "Avbryt". Ingen inskriven titel,
ingen dubbel bekräftelse.

Bara skaparen har administrationslänken, och förfrågan raderas ändå automatiskt inom en
till två månader. Ett tyngre skydd står inte i proportion.

Varningen ska säga vad som faktiskt försvinner: förfrågan **och alla svar**, utan
möjlighet att få tillbaka dem.

Använd inte webbläsarens `confirm()` — en dialog i sidan, med samma tumvänliga träffytor
som resten ([D-008](#d-008--användarvänlig-före-minimal)).

## D-027 — Admin-vyn är svarsvyn med tillägg
**2026-09-09 · Gäller**

Det finns inte två listvyer. Administrationslänken visar samma sammanställning som
svarslänken, plus:

- de två länkarna med kopiera-knapp
- `mailto:`-knappen ([D-012](#d-012--mailto-med-skaparen-som-mottagare))
- raderaknappen ([D-026](#d-026--radering-bekräftas-med-ett-steg))
- namnrutan förifylld med skaparens namn ([D-025](#d-025--skaparen-anger-namn-och-är-själv-deltagare))

En vy, en mall, ett ställe att ändra. Efter skapandet landar skaparen här och kan fylla i
sin tillgänglighet på en gång.

## D-028 — Personuppgifter vi lagrar
**2026-09-09 · Gäller**

Fullständig förteckning. Tillkommer något ska den här listan uppdateras i samma ändring.

| Var | Uppgift | Varför |
|---|---|---|
| `Poll` | Skaparens förnamn | Tilltal i mejl, förifylld namnruta |
| `Poll` | Skaparens e-postadress | Länkarna vid skapandet, daglig sammanfattning |
| `Participant` | Förnamn | Identifierar svaret i listan |
| `Response` | Datum och tillgänglighet | Själva syftet |
| `outbox_email` | Mottagare och brödtext, med förnamn i | Tillfälligt, tills mejlet gått iväg ([D-030](#d-030--outbox-för-all-utgående-post)) |

Utanför appen: AhaSend är personuppgiftsbiträde för sammanfattningsmejlens innehåll, med
retention satt till noll ([D-029](#d-029--ahasend-som-e-postleverantör-via-smtp)).

Allt raderas när förfrågan gallras ([D-021](#d-021--giltig-till-verkställs-av-nattjobbet-inte-på-sekunden)).
Deltagarnas e-postadresser lagras aldrig. Ingen IP-adress sparas — hastighetsgränsen i
[D-017](#d-017--hastighetsgräns-per-ip-på-skapandet) håller sitt räkneverk i minnet, inte
i databasen.

## D-029 — AhaSend som e-postleverantör, via SMTP
**2026-09-09 · Gäller**

Utgående post går genom **AhaSend** (AhaSend B.V., Nederländerna) över **SMTP**, med
`spring-boot-starter-mail`. Inget REST-API, ingen leverantörs-SDK.

Valet av leverantör är medvetet **inte** ett arkitekturbeslut. Jakarta Mail pratar SMTP,
och alla trovärdiga alternativ — Scaleway TEM, Brevo, Mailjet — erbjuder SMTP-relä. Byte
av leverantör är därför fyra rader i env-filen:

```
SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD
```

Använder du leverantörens API i stället låser du fast dig utan att vinna något: de
statusar och webhooks som API:et ger behöver appen inte.

Varför AhaSend framför de andra: endast transaktionell post, EU-infrastruktur utan
hyperskalare under, tre underbiträden, och **retention per meddelande som kan ställas
till noll**. Det sista är det som avgör — sammanfattningsmejlet innehåller deltagarnas
förnamn, så leverantören blir personuppgiftsbiträde. Med noll retention lagras innehållet
aldrig hos dem. Brevo och Mailjet är marknadsföringsplattformar och drar in ett
kontaktregister vi inte vill ha.

Krav som följer:

- **Verifierad avsändardomän med SPF och DKIM**, och DMARC därtill. Alla leverantörer
  kräver det, och utan det hamnar posten i skräpkorgen.
- **MX behövs inte.** Den är till för att ta emot post, och appen tar aldrig emot något.
  Sätt upp MX enbart om svar på sammanfattningsmejlen ska kunna nå fram i stället för att
  studsa.
- **Personuppgiftsbiträdesavtal** med AhaSend, och retention satt till noll.
- Volymen är försumbar — ett skapelsemejl och i praktiken fem till tio sammanfattningar
  per förfrågan. Fribeloppet på 1 000 per månad räcker med bred marginal.

## D-030 — Outbox för all utgående post
**2026-09-09 · Gäller. Ändrar D-011 och D-024.**

Ingenting i appen skickar mejl direkt. Allt skrivs som en rad i en `outbox_email`-tabell,
och ett eget jobb tömmer kön.

Skälet är inte leveransgarantier — volymen motiverar inte sådant. Skälet är att D-024
annars innehåller en dubbelskrivning: "markera svaren som rapporterade" och "skicka
mejlet" träffar två system utan gemensam transaktion. En krasch mellan dem ger antingen
dubbletter eller, värre, svar som markerats som rapporterade utan att något mejl gick
iväg — och som därför aldrig rapporteras. Med en outbox blir de två skrivningarna
**samma transaktion** och problemet upphör att finnas.

På köpet blockerar skapa-sidan aldrig på ett SMTP-handslag mot en extern tjänst, och
tester kan verifiera en rad i tabellen i stället för att starta en fejkad mejlserver.

Utformning:

- `outbox_email`: mottagare, ämne, brödtext, `created_at`, `attempts`, `last_error`.
- Raden skrivs i samma transaktion som den domänändring som utlöser mejlet.
- **Tömningsjobbet är det enda som får röra `JavaMailSender`.** Kalla den ingen
  annanstans.
- Jobbet kör var minut, med backoff mellan försök och ett tak på antalet försök. Därefter
  lämnas raden och felet loggas — utan mottagaradress i loggen.
- **Raden raderas när mejlet gått iväg.** Brödtexten innehåller förnamn, så den ska inte
  ligga kvar som ett arkiv ([D-028](#d-028--personuppgifter-vi-lagrar)).

Jobben är därmed tre, inte två: tömning av outbox varje minut, samt sammanfattning och
gallring i nattpasset enligt [D-024](#d-024--ett-nattligt-jobb-sammanfattning-före-gallring).

## D-031 — Domän: ministra.marvi.work
**2026-09-09 · Gäller**

Appen kör på `https://ministra.marvi.work` att börja med. Det är en testadress, inte ett
slutgiltigt namn — men den är riktig nog för att sätta upp SPF, DKIM och DMARC på och
för att skicka skarp post.

Adressen sätts i env-filen som `MINISTRA_BASE_URL`
([D-022](#d-022--publik-bas-url-konfigureras-explicit)). Byter domänen namn är det en
env-ändring och en omstart — inget i koden.

DNS-posterna läggs på underdomänen, inte på `marvi.work`. Ett rykte som byggs upp eller
raseras på `ministra.marvi.work` ska inte smitta av sig på annan post från `marvi.work`.

## D-032 — Avbilden byggs av GitHub Actions till ghcr.io
**2026-09-09 · Gäller**

En handskriven `Dockerfile` i repoten, byggd av ett GitHub Actions-flöde som publicerar
till `ghcr.io/marvi/ministra`. Inga buildpacks, ingen jib.

Mönstret finns redan i [marvi/lektionarium](https://github.com/marvi/lektionarium) och
ska följas, inte uppfinnas om:

- Flerstegsbygge: `eclipse-temurin:25-jdk` för bygget, `eclipse-temurin:25-jre` för
  körningen. Notera 25, inte 21 som i lektionarium.
- Pom-filen kopieras in före källkoden, så beroendena hamnar i ett eget lager.
- `java -Djarmode=tools -jar ... extract --layers --launcher` delar upp jaren, och lagren
  kopieras i ordningen minst föränderligt först.
- Egen systemanvändare `10001:10001`, `USER` satt, aldrig root i containern.
- `LABEL org.opencontainers.image.source` mot repot, annars hamnar paketet löst under
  kontot i stället för på projektsidan.
- `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"` så att JVM:en rättar sig efter
  containerns minnesgräns.
- `HEALTHCHECK` med curl mot `/actuator/health`.
- `ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]`.

Skillnader mot lektionarium: Ministra är en enda modul, så inget `-pl web -am`. Ingen
Maven-artefakt publiceras och ingen GitHub-release skapas — bara avbilden. Tailscale-stegen
behövs inte.

Två saker saknas i repot idag och måste till:

- `spring-boot-starter-actuator` i `pom.xml`, annars finns ingen hälsokontroll att peka på.
- `gg.jte.development-mode` står på `true` i `application.properties`. Det får inte följa
  med i avbilden — mallarna ska vara förkompilerade i produktion.

## D-033 — Flyway för schemamigreringar
**2026-09-09 · Gäller**

Flyway med ren SQL i `src/main/resources/db/migration`. Versionerade filer i repot,
aldrig handkörd DDL mot databasen.

Valet är givet: Flyway används redan i andra projekt, och Hibernates `ddl-auto` står på
`validate` mot en delad produktionsinstans
([D-015](#d-015--befintlig-postgres-16-på-värden-delad-instans)).

Migreringen körs av appen vid uppstart, se D-039.

## D-034 — Rootless Podman
**2026-09-09 · Gäller**

Containern körs rootless, som en vanlig tjänsteanvändare.

Konsekvenser:

- Quadleten ligger i `~/.config/containers/systemd/ministra.container`, inte under
  `/etc`. Enheten hanteras med `systemctl --user`.
- `loginctl enable-linger` måste vara påslaget för användaren, annars stoppas tjänsten
  när sessionen tar slut.
- Portar under 1024 kan inte bindas. Appen lyssnar på en hög port och reverse proxyn tar
  443 ([D-022](#d-022--publik-bas-url-konfigureras-explicit)).
- Env-filen ägs av tjänsteanvändaren med läge `0600`
  ([D-014](#d-014--konfiguration-i-en-env-fil-inte-i-unit-filen)).
- Containern når värdens Postgres via `host.containers.internal`, vilket kräver
  `PodmanArgs=--add-host=host.containers.internal:host-gateway` eller motsvarande, samt
  att `pg_hba.conf` släpper in podman-nätets adress
  ([D-015](#d-015--befintlig-postgres-16-på-värden-delad-instans)).
- Testcontainers i utveckling kräver `systemctl --user enable --now podman.socket` och
  `DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock`.

## D-035 — All post i klartext, med fastställda formuleringar
**2026-09-09 · Gäller**

Ingen HTML. Alla mejl skickas som `text/plain`, ingen multipart.

Skälet är vilka som faktiskt får posten. Deltagarna får aldrig något från appen — de får
skaparens eget mejl, skrivet i hans egen klient. Appens enda mottagare är planerarna, en
handfull personer, och mejlen är två till fem rader. HTML skulle ge en knapp i stället för
en länk och kosta mall, inline-CSS, mörkt läge och bildblockering. Från en nystartad
avsändardomän ([D-031](#d-031--domän-ministramarviwork)) är ren text dessutom den form
som lättast tar sig förbi skräpfilter.

Formuleringarna nedan är beslutade på samma sätt som trafikljustexten i
[D-013](#d-013--de-tre-valen-förklaras-med-synlig-text). Skriv inte om dem på egen hand.
`{}` markerar insatta värden.

### 1. Vid skapandet — appen skickar

Ämne:

```
Din förfrågan "{titel}" är skapad
```

Brödtext:

```
Hej {förnamn}!

Din förfrågan "{titel}" är skapad. Du har två länkar.

Skicka den här till dem som ska svara:
{svarslänk}

Den här är din egen. Spara den — med den ser du svaren och kan radera förfrågan:
{adminlänk}

Förfrågan och alla svar raderas automatiskt efter {giltig till}.

Ministra
```

**Kommentarsfältet får inte med.** Skapandet är en oautentiserad utgång som mejlar till
en användarangiven adress ([D-016](#d-016--skaparen-får-länkarna-både-på-skärmen-och-per-mejl)),
och ju mindre fritext som lämnar vår avsändardomän desto bättre. Titeln kortas till 80
tecken.

### 2. Daglig sammanfattning — appen skickar

Ämne vid en förfrågan:

```
Nya svar på "{titel}"
```

Ämne vid flera:

```
Nya svar på dina förfrågningar
```

Brödtext:

```
Hej {förnamn}!

Sedan igår har det kommit nya svar.

{titel}
  {namn, namn, namn}
  {adminlänk}

Förfrågan och alla svar raderas automatiskt efter {giltig till}.

Ministra
```

Blocket upprepas per förfrågan. Har inget nytt kommit in skickas ingenting
([D-024](#d-024--ett-nattligt-jobb-sammanfattning-före-gallring)).

### 3. `mailto:`-texten — skaparen skickar själv

Detta mejl lämnar aldrig vår server, utan komponeras i skaparens egen klient
([D-012](#d-012--mailto-med-skaparen-som-mottagare)). Därför får kommentaren vara med
här: den kan varken påverka vår leveransbarhet eller missbrukas av utomstående.

Ämne:

```
Kan du dessa dagar? - {titel}
```

Brödtext:

```
Hej!

Jag planerar "{titel}" och behöver veta vilka dagar du kan. Fyll i här:

{svarslänk}

{kommentar}

Du markerar Kan, Om det behövs eller Kan inte för varje dag, och skriver ditt
förnamn överst. Det tar ett par minuter. Svara gärna före {giltig till}.

Hälsningar
{skaparens förnamn}
```

Länken ligger tidigt med flit — vissa klienter kapar långa `mailto:`-URL:er.

Body procentkodas som UTF-8: radbrytning blir `%0A`, och å, ä, ö blir `%C3%A5`, `%C3%A4`,
`%C3%B6`. Skriv inte om texten till ren ASCII för att slippa kodningen — svenska
församlingsmedlemmar ska inte få mejl utan prickar. Är kommentaren tom ska den tomma
raden också bort, inte lämna ett hål i texten.

## D-036 — Startdagen måste ligga i framtiden
**2026-09-09 · Gäller**

En förfrågan kan bara skapas för dagar som ännu inte varit. Skapa-formuläret erbjuder
gudstjänstdagar från och med imorgon, och servern avvisar allt annat — formuläret går att
posta förbi.

Skapas förfrågan på en söndag är den söndagen alltså inte med. Det är avsiktligt: att
samla in tillgänglighet för en gudstjänst som börjar om några timmar är inte vad appen är
till för.

Kravet gör kontrollen mot `LiturgicalYear.FIRST_SUPPORTED_YEAR` i tjänsten överflödig — en
dag i framtiden ligger alltid efter 2004 — och den är därför borttagen därifrån.
`ChurchCalendar` vaktar fortfarande gränsen internt, vilket är rätt ställe för den.

## D-037 — Liturgisk färg visas inte
**2026-09-09 · Gäller**

Kyrkoårs-API:t ger `Day.color()` — vit, röd, violett, blå, svart, grön, rosa. Den visas
inte, och `ServiceDay` bär den inte.

Skälet är trafikljuset: en dag märkt violett bredvid gröna, gula och röda svarsknappar
blir förvirrande, och färg får aldrig bära information ensam
([D-013](#d-013--de-tre-valen-förklaras-med-synlig-text)). Den liturgiska färgen skulle
konkurrera med den enda färgkodning sidan har råd med.

## D-038 — Svenska i CI-filerna
**2026-09-09 · Gäller**

Stegnamn och kommentarer i `.github/workflows/` skrivs på svenska, som i
[marvi/lektionarium](https://github.com/marvi/lektionarium).

Det är ett medvetet undantag från [D-004](#d-004--kod-på-engelska-gränssnitt-på-svenska).
YAML-filerna är operatörsnära snarare än kod — de läses när ett bygge gått sönder — och
konsekvens mellan repona väger tyngre än regeln. Java-koden är fortfarande engelsk,
undantagslöst.

## D-039 — Flyway körs av appen vid uppstart
**2026-09-09 · Gäller**

Migreringarna körs av applikationen när den startar, inte som ett eget deploysteg.

Konsekvensen är att appens databasroll måste få ändra sitt eget schema, vilket mjukar upp
minsta-rättighet-principen i [D-015](#d-015--befintlig-postgres-16-på-värden-delad-instans).
Rollen ska fortfarande vara begränsad till sin egen databas och sitt eget schema — aldrig
superuser, aldrig rättigheter på något annat i den delade instansen.

Det enkla som fungerar väger tyngre här: en deploy är att starta om containern, och då ska
schemat följa med utan ett extra manuellt steg som kan glömmas bort.

## D-040 — Alla gudstjänstdagar, inte bara söndagar
**2026-09-09 · Gäller. Ändrar D-013, D-018, D-019, D-020, D-035 och D-036.**

Listan innehåller varje dag kyrkoåret firar med gudstjänst — inte bara söndagar. Juldagen,
annandag jul, Långfredagen, Kristi himmelsfärds dag, Annandag pingst, Midsommardagen,
Alla helgons dag och Trettondedag jul behöver bemanning precis som en söndag. Annandag
pingst är inte allmän helgdag, men firas ändå.

Urvalet görs på typen i lektionarium-API:t: `day instanceof HolyDay`. Filtrera inte på
`DayOfWeek.SUNDAY`. API:t skiljer redan på `HolyDay` och `OrdinaryDay`, och vardagarna i
Stilla veckan är `OrdinaryDay` — de faller därmed bort av sig själva. Varje söndag är en
`HolyDay`; verifierat för 2026 till 2030. Ett år har ungefär 66 gudstjänstdagar, varav 52
söndagar.

Följden är att ordet "söndag" är utbytt mot "dag" i gränssnittet och i mejlen, inklusive
de ordagrant beslutade texterna i [D-013](#d-013--de-tre-valen-förklaras-med-synlig-text)
och [D-035](#d-035--all-post-i-klartext-med-fastställda-formuleringar).

Typen heter fortfarande `ServiceDay`, inte `HolyDay`. Vårt begrepp är "en dag som behöver
bemannas", inte en liturgisk kategori — och namnet skulle dessutom krocka med den
importerade `lectio.cal.HolyDay`, som betyder något annat: en dag med egna texter.
`SundayView` heter numera `ServiceDayView`.

## D-041 — Skaparen får ta bort dagar, innan någon svarat
**2026-09-09 · ERSATT av [D-042](#d-042--dagarna-väljs-innan-förfrågan-skapas).**

> Urvalet flyttades till skapandet i stället för att vara en efterhandsredigering i
> admin-vyn. Motivet nedan gäller fortfarande; mekanismen gör det inte.

Efter att förfrågan skapats kan skaparen ta bort dagar ur listan, och lägga tillbaka dem
igen. Ingen församling bemannar allt: förbedjare används inte på Alla helgons dag eller
under fastan, utom på Jungfru Marie bebådelsedag. Extra helgdagar som ingen ska ta
förvirrar bara den som ska svara.

**Listan går bara att ändra så länge ingen har svarat.** Vid första inlämnade svaret
fryses den, och knapparna försvinner.

Det villkoret är det som gör resten enkel. Så länge det inte finns några svar går varje
ändring att ångra, ingenting går förlorat, och därför behövs varken varning eller
bekräftelse. Efteråt vore det inte sant: en tillagd dag skulle sakna svar hos den som
redan fyllt i, och [D-019](#d-019--alla-dagar-måste-besvaras) säger att det inte finns
något obesvarat tillstånd. Alternativet — att radera svaren för en borttagen dag — vore
en destruktiv åtgärd på en sida full av knappar.

Modellen: en tabell `excluded_day` med `(poll_id, service_date)`. Bara undantagen lagras;
dagarna själva räknas fortfarande fram ur kyrkoåret
([D-018](#d-018--dagarna-härleds-svar-bär-sitt-datum)). Mängden hämtas ivrigt, eftersom
den alltid behövs när listan visas.

Minst en dag måste vara kvar. En tom förfrågan är inte något att skicka ut.

## D-042 — Dagarna väljs innan förfrågan skapas
**2026-09-09 · Gäller. Ersätter D-041.**

Skapandet sker i två steg:

1. **Uppgifterna.** Titel, kommentar, period, namn, e-post och giltighetstid. Ingenting
   sparas.
2. **Dagarna.** Alla gudstjänstdagar i perioden visas förkryssade. Skaparen klickar bort
   dem som inte ska med — de gråmarkeras och stryks över, och går att klicka tillbaka.
   Först vid **Spara** skapas förfrågan.

Därefter landar skaparen som förut på admin-vyn med länkarna och sin egen namnruta
förifylld (D-016, D-027).

Varför före i stället för efter: valet hör hemma där man redan tänker på perioden, inte
som en efterhandsredigering på en sida som annars handlar om att dela ut länken. Och
eftersom förfrågan inte finns ännu finns det ingenting att förlora — därför behövs varken
varning, bekräftelse eller den låsning D-041 krävde när första svaret kommit in.

**Efterhandsredigering finns inte.** Upptäcker skaparen ett misstag efteråt raderas
förfrågan och görs om; länken har inte hunnit skickas ut, och raderaknappen står på samma
sida. Två vägar till samma sak vore ett val för mycket (D-008).

Genomförande:

- Steg två sparar inget, så uppgifterna följer med som dolda fält i formuläret.
- Togglingen är kryssrutor med etiketter, formaterade med CSS. **Ingen JavaScript** —
  sidan fungerar likadant utan.
- Varje rad har en knapp till höger som säger vad ett klick gör: **✕ Ta bort** när dagen
  är med, **+ Lägg till** när den är bortvald. Utan den läses listan som en punktlista och
  ingenting antyder att den går att klicka på.
- Knappen visar alltså *åtgärden*, inte tillståndet. Tillståndet syns på raden i övrigt:
  bortvalt är dämpat och överstruket, så att skillnaden inte bara är färg.
- Knappen är `aria-hidden`. Kryssrutan bär tillståndet för skärmläsare, och namnet ska
  vara dagen — inte ett verb som växlar.
- Hastighetsgränsen (D-017) räknas i steg två, där förfrågan faktiskt skapas.
- Minst en dag måste vara med. Går det inte igenom visas dagsidan igen med felet och
  kryssen kvar som de var.

## D-043 — Skaparen får lägga till egna datum
**2026-09-09 · Gäller**

Utöver kyrkoårets dagar kan skaparen lägga till valfria datum i dagvalet. En församling
kan fira ett lokalt helgon eller en egen högtid som inte finns i Svenska kyrkans kyrkoår.

Datumet måste ligga inom perioden, och en dag som redan finns i listan går inte att lägga
till igen. Båda kontrollerna ger ett meddelande till användaren, inte ett fel.

### Namnet är en uppslagning, inte en typ

Ett tillagt datum saknar oftast kyrkoårsnamn. Det är **inte** modellerat som en egen
variant, och `ServiceDay` är därför ingen förseglad hierarki med en namnlös sort.

Skälet är att lektionarium med tiden ska kunna namnge även vardagar — "Tisdagen i första
påskveckan", "Onsdagen efter trettonde söndagen i Trefaldighet" — och på sikt bära
helgondagar, liturgisk färg för vardagar och dagliga bibelläsningar. Byggde vi in
"tillagd dag = utan namn" i typen vore den fel den dagen API:t lär sig namnen.

`ServiceDay.name` får därför saknas, och `ChurchCalendar.dayAt(date)` slår upp namnet för
vilket datum som helst. Redan idag träffar den dagar som "Tisdag i Stilla veckan". Lär sig
lektionarium fler dyker de upp av sig själva, utan att någon typ ändras.

**Fällan i API:t:** `getCurrentDay` returnerar närmast *föregående* liturgiska dag, inte
den man frågar om — en tisdag i fastan ger tillbaka söndagen före. Datumet i svaret måste
jämföras med det efterfrågade innan namnet används.

### Modellen

Tabellen `extra_day (poll_id, service_date)`, spegelbilden av `excluded_day`. Fortfarande
ingen tabell över dagarna själva ([D-018](#d-018--dagarna-härleds-svar-bär-sitt-datum)).

Tjänstens signatur behövde inte ändras. Dagvalssidan arbetar på en kandidatlista, och
`create` härleder båda mängderna ur den:

```
bortvalda = kyrkodagar − behållna
extra     = behållna − kyrkodagar
```

Datum utanför perioden faller bort tyst, oavsett hur de hamnat i formuläret.

### Gränssnittet

Ett datumfält och en knapp under listan. Med htmx byts bara listan ut, så man står kvar i
den i stället för att kastas till sidans topp — vid trettio dagar är det skillnaden mellan
lätt och irriterande. Utan JavaScript postar formuläret som vanligt och samma mall
renderas om; funktionen är densamma, man hamnar bara överst
([D-003](#d-003--server-renderad-html-med-jte-och-htmx)).

En dag utan namn visar bara sitt datum. Kortet blir kortare, och det är rätt — det finns
inget mer att säga om den.

## D-044 — Svarsvyns utformning
**2026-09-09 · Gäller**

Efter en mockup av svarsvyn på mobil. Det mesta i den är antaget; två saker är avvisade.

**Antaget:**

- **Platt lista med hårlinjer**, inte inramade kort. Trettio kort på en mobil blir tungt;
  avdelare läser som en tidning och sparar höjd.
- **Veckodag och fullt datum som rubrikrad**, kyrkoårsnamnet som den feta raden. Dagarna
  heter "Tacksägelsedagen" i församlingens mun, och folk planerar i veckodagar. En dag
  utan kyrkoårsnamn ([D-043](#d-043--skaparen-får-lägga-till-egna-datum)) bär sig själv
  på datumraden utan att se trasig ut.
- **Trafikljusknapparna är tonade redan i vila** — ljus grön, gul, röd — och blir mörka
  med vit text och en bock när de väljs. Instruktionstexten i
  [D-013](#d-013--de-tre-valen-förklaras-med-synlig-text) säger "kan (grön)"; med
  neutrala knappar syftar ordet på ingenting förrän man klickat. Ljus mot mörk är
  dessutom en kontrastskillnad och inte bara en nyansskillnad, så valet syns för den som
  inte skiljer på färgerna. Bocken gör det otvetydigt i ögonvrån.
- **"Raderas efter …" syns för alla**, inte bara skaparen, och **"Svar kan inte ändras i
  efterhand"** står precis ovanför knappen ([D-010](#d-010--svar-är-oföränderliga-och-alla-ser-alla)).
- Knappen heter **"Spara mitt svar"** — samma verb som i dagvalet.

**Avvisat:**

- **Grönt som märkesfärg.** Mockupens spara-knapp var mörkgrön — samma gröna som en vald
  "Kan". Trafikljuset äger grönt, gult och rött; ingenting annat på sidan får tala det
  språket. Accenten är indigo och ska förbli något utanför de tre.
- **Andras svar per person** ("Anna ● Kan"). Vackert med en deltagare; med tio
  textläsare och trettio dagar blir det trehundra rader. Svaren grupperas per färg —
  högst tre rader per dag — men med mockupens stil: färgad punkt och ordet utskrivet.

**Fonter** är oförändrade: Georgia i rubrikerna, systemets sans i brödtexten, noll byte
att ladda. Vill vi ha ett eget ansikte är Source Serif 4 (SIL OFL) för rubrikerna det
val som stämmer med självhostningskravet; brödtexten stannar i systemets sans.
