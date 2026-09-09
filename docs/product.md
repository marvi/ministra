# Ministra — vad vi bygger och varför

## Problemet

Jag ansvarar för söndagarnas förbedjare. Fem personer, en leder förbönen varje söndag.
Jag har använt Doodle för att ta reda på vilka söndagar de närmaste månaderna var och en
kan. Min fru har samma problem med sina tio textläsare, där två behövs varje söndag.
De som planerar för ministranter, kyrkvärdar och lovsångsledare har samma problem.

Doodle har blivit värdelöst.

Jag har dessutom redan ett Java-API som räknar ut söndagar framåt utifrån Svenska
kyrkans kyrkoår. Det gör att listan kan visa *"Tjugonde söndagen efter trefaldighet"*
och inte bara *"18 oktober"*.

Målet är en användarvänlig applikation. Det skall vara svårt att göra fel. Dra ner på
antalet val.

Den skall fungera lika bra på mobil som på skärm.

## Flödet

### 1. Någon fyller i uppgifterna

Går in, väljer första och sista dagen — högst ett halvår. Anger en titel, som är det
enda som säger vad förfrågan gäller: "Sakristaner fram till påsk". Dessutom en valfri
kommentar.

Anger sitt förnamn och sin e-postadress. Adressen är vägen tillbaka till förfrågan.
Förnamnet behövs för att skaparen nästan alltid själv är med i gruppen och ska kunna
fylla i sin tillgänglighet direkt när förfrågan är skapad.

"Giltig till" förväljs till en månad, med möjlighet att välja två.

Ingenting sparas i det här steget.

### 2. Och väljer dagar

Nästa skärm visar alla dagar i perioden, förkryssade. Ingen församling bemannar allt — vi
använder inte förbedjare på Alla helgons dag eller under fastan, utom på Jungfru Marie
bebådelsedag, och extra helgdagar som ingen ska ta förvirrar bara den som ska svara.
Skaparen klickar bort dem som inte ska med; de dämpas och stryks över, och går att klicka
tillbaka.

Här går det också att **lägga till egna datum**. En församling kan fira ett lokalt helgon
eller en egen högtid som inte finns i Svenska kyrkans kyrkoår. Datumet måste ligga inom
perioden. En sådan dag har inget namn från kyrkoåret och visar bara sitt datum — det
behövs ingen titel.

Först vid **Spara** skapas förfrågan.

### 3. En länk skapas

Två länkar skapas: **svarslänken** som delas ut, och **administrationslänken** som är
skaparens egen. Båda visas med kopiera-knapp, och båda skickas dessutom i ett mejl till
skaparen — annars finns ingen väg tillbaka om sidan stängs.

Skaparen landar på administrationsvyn, som är samma lista som deltagarna ser, med
länkarna, mailto-knappen och raderaknappen ovanpå. Namnrutan är förifylld, så
tillgängligheten kan fyllas i direkt.

Dagarna går inte att ändra här — de valdes i steget innan. Blev det fel raderas förfrågan
och görs om; länken har inte hunnit skickas ut.

En raderaknapp finns, med varning om att förfrågan och alla svar försvinner.

Länken skickas ut manuellt, via skaparens eget e-postprogram, till alla som ska svara.
Appen förbereder en `mailto:`-länk med ämnesrad och färdig brödtext — titel, kommentar,
själva länken och en kort förklaring. **Skaparens egen adress ligger i To-fältet**, så
att fältet inte står tomt. Deltagarnas adresser fyller skaparen själv i, i sitt
e-postprogram — appen har dem inte.

Detta är avsiktligt omständligt. Jag vill inte lagra personuppgifter som e-postadresser
mer än nödvändigt, och därför ska deltagarna aldrig hamna i databasen som adresser.

### 4. Deltagarna svarar

Den som får mejlet klickar på länken och ser en enkel vy med alla dagar i perioden — inte
bara söndagar, utan varje dag kyrkoåret firar med gudstjänst: juldagen, annandagarna,
Långfredagen, Kristi himmelsfärds dag, Annandag pingst och de andra. Varje rad visar dagens
namn och datum. Tre val per dag, som ett trafikljus:

| Val | Färg | Betydelse |
|---|---|---|
| Kan | grön | Jag kan ta den här dagen |
| Om det behövs | gul | "Jag kommer hem från semestern och är kanske lite groggig, men om ingen annan kan så tar jag den" |
| Kan inte | röd | Jag kan inte |

De tre valen förklaras med **synlig text på sidan**, inte i en hover — det är ju precis
det man ombeds göra, och en så central sak ska stå framme. Texten lyder:

> Ange för varje dag om du **kan** (grön), **kan om det behövs** (gul) eller
> **inte kan** (röd). Mittenvalet betyder att du helst avstår, men ställer upp om ingen
> annan kan.

Överst finns en obligatorisk ruta där man skriver sitt namn. Förnamn räcker.

**Namn måste vara unika inom en förfrågan.** Om någon skriver ett namn som redan finns
visas en varning, och personen får lägga till en bokstav — "Anna J".

**Svar kan inte ändras i efterhand.** Det finns inga konton och därmed ingen väg tillbaka
till just ditt svar. Den som svarat fel fyller i på nytt under ett särskiljande namn —
"Anna Ny". Samma sak som i Framadate, och det har fungerat bra där. Det gamla svaret
ligger kvar i listan; skaparen får själv se att det är ersatt.

Alla deltagare ser allas svar. Det är avsiktligt — hela poängen är att se var luckorna
finns.

Hela listan syns direkt när man öppnat länken — man ska se vad man ger sig in i, och se
om ens eget namn redan står där.

Alla dagar måste besvaras. Formuläret går inte att skicka med luckor, så det finns inget
"obesvarat" att förklara i listan.

### 5. Skaparen får veta

Vid dagens slut går ett samlat mejl till skaparen: vilka som har svarat på vilka
förfrågningar. Inte ett mejl per svar — med tio textläsare blir det tio mejl på en kväll.

### 6. Förfrågan går ut och raderas

Nattjobbet raderar förfrågan med alla svar när "Giltig till" har passerat. Fram tills
jobbet kör fungerar länken som vanligt — "Giltig till" är alltså ungefärligt, och
gränssnittet ska inte lova en exakt tidpunkt.

Ingen förvarning skickas. Själva planeringen håller inte på mer än en vecka eller två —
schemat ska bli klart och skickas ut — så en till två månader är gott om tid. Ett
påminnelsemejl skulle bara störa.

## Vad appen inte gör

Appen **fördelar inte** personer på dagarna. Den samlar in tillgänglighet. Att välja
vem som faktiskt läser texten den 18 oktober gör en människa, med listan framför sig.

Inte i denna version, men tänkbara vidareutvecklingar:

- Hjälp åt skaparen att fördela dagarna utifrån svaren.
- En layoutad PDF för utskrift.

Se även avsnittet Icke-mål i [AGENTS.md](../AGENTS.md).

## Öppna frågor

Dessa är inte avgjorda. Gissa dig inte förbi dem — fråga.

### Produkt

Inga öppna.

### Teknik och drift

- **Slutgiltigt domännamn.** `ministra.marvi.work` duger till test och skarp post, men är
  inte tänkt som permanent. Se D-031.
- **Vem lägger DNS-posterna** för SPF, DKIM och DMARC på underdomänen.
