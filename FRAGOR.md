# Frågor från implementationen

Noterade i stället för att avbryta. Öppna frågor som fanns redan innan kodningen ligger i
[docs/product.md](docs/product.md).

## Produkt

Inga öppna. Frågorna om liturgisk färg, passerade söndagar och skaparens namn är besvarade
och inflyttade som D-036, D-037 och ett förtydligande i D-025.

## Teknik

Inga öppna. Språket i CI-filerna är D-038, hastighetsgränsen i minnet är ett förtydligande
i D-017, och Flyway vid uppstart är D-039.

## Fynd under arbetet, redan åtgärdade

**`gg.jte.use-precompiled-templates` saknades.**
Med `development-mode=false` men utan `use-precompiled-templates=true` vägrar jte att
starta över huvud taget. Appen hade alltså inte gått igång i containern. Fångades av
webbtestet och är rättat i `application.properties`.

**`spring-boot-flyway` behövdes som eget beroende.**
I Spring Boot 4 ligger autokonfigurationen i en egen modul. Med bara `flyway-core` kördes
inga migreringar, och schemavalideringen sa "missing table".

**Frånkopplade entiteter smällde utanför transaktionen.**
`@DataJpaTest` håller allt i en enda transaktion, så en förfrågan som lästs i ett anrop var
fortfarande knuten till sessionen i nästa. I appen är den inte det — `open-in-view` är av.
Både svarsinlämning och radering kastade `LazyInitializationException` första gången appen
kördes på riktigt. Rättat med omläsning i tjänsten och en hämtande join i vyfrågan, och
täckt av `PollServiceDetachedTest`, som kör utan omslutande testtransaktion. Det första
försöket till regressionstest var verkningslöst — `entityManager.clear()` inuti en öppen
transaktion räcker inte.

**Raderingen kaskaderade bara i databasen.**
`on delete cascade` fanns i schemat, men JPA saknade motsvarande koppling och båda sidor av
relationen sattes inte. Radering av en förfrågan gick i väggen vid flush. Rättat med
kaskad på `Poll.participants` och `Poll.addParticipant`.

**Hälsokontrollen kollade mejlservern.**
Spring lägger till en hälsokontroll för SMTP. Med den påslagen hade `/actuator/health` gett
503 så fort AhaSend var onåbart, och systemd hade startat om containern i onödan — trots
att outboxen finns just för att överleva mejlavbrott. Avstängd med
`management.health.mail.enabled=false`.

**Utvecklingsdatabasen ligger på 5433.**
Port 5432 var upptagen av en annan Postgres på maskinen, och appen kopplade tyst upp sig
mot fel databas och nekades. Dev-profilen använder 5433 för att undvika det.

**Testcontainers stoppade containern mellan testklasser.**
Med `@Testcontainers` och `@Container` stoppades Postgres efter första klassen, och nästa
klass väntade ut anslutningspoolens 30-sekunderstimeout. Löst med en delad container som
startas i ett statiskt block.

## Fynd vid ändringen till gudstjänstdagar

**Flash-attribut fungerar inte utan session.**
Felmeddelandet vid en avvisad dagändring skickades först som flash-attribut över
omdirigeringen. Appen har ingen session, så det försvann tyst. Ändrat till att rendera
sidan direkt med felet, som resten av controllern gör.

**En gammal instans låg kvar på port 8080.**
`pkill -f "spring-boot:run"` dödar Maven men inte den forkade JVM:en. Hälsokontrollen
svarade från den gamla koden och gav ett missvisande grönt. Använd
`lsof -ti:8080 | xargs kill -9` vid omstart.

## Noterat vid README-skrivningen

**htmx används inte.** *(Åtgärdat.)*
Beroendet låg oanvänt i `pom.xml`. Det används nu i dagvalet, där ett tillagt datum byter
ut listan i stället för att ladda om sidan (D-043). Biblioteket är hämtat och serveras
från appen som `/js/htmx-2.0.4.min.js`.

**Ingen licensangivelse i `pom.xml` eller på avbilden.**
`LICENSE` är AGPL v3, men `pom.xml` har inget `<licenses>`-block och Dockerfilen ingen
`org.opencontainers.image.licenses`-etikett. Lektionarium har det senare. Värt att lägga
till så att avbilden bär sin licens.
