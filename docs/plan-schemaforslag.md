# Plan: "Ge förslag på schema"

Status: plan, inte påbörjad. Skriven 2026-09-09 efter samtal med skaparen, och avstämd
samma dag. Alla frågor under "Avgjort" är beslutade.

## Vad som ska byggas

En knapp på skaparens sida (`/a/{token}`), synlig när förfrågan har **tre eller fler
svar**: **"Ge förslag på schema"**. Den leder till en ny sida där appen har fördelat de
som svarat på dagarna, rättvist och utspritt, och där skaparen fritt flyttar om, lägger
till och tar bort tills schemat ser ut som hen vill.

Ingenting sparas på servern. Sidan är ett arbetsblad. Laddas den om börjar man om.
**"Avbryt"** går tillbaka till skaparens sida.

Det är första gången appen gör något med svaren utöver att visa dem. product.md
förutsåg det redan som en tänkbar vidareutveckling ("hjälp åt skaparen att fördela
dagarna"), men README och AGENTS säger fortfarande att appen *inte* fördelar. Beslutet
blir en ny post i beslutsloggen, D-046, och texterna skrivs om: förslaget är ett
hjälpmedel, avgörandet är fortfarande skaparens, och appen lagrar inte resultatet.

## Avgjort

1. **Personer per dag** är en väljare överst på sidan, förval 1, högst 4. Ändrar man den
   räknas förslaget om (ny GET med parametern). Appen vet inte hur många som behövs
   ([README: "Räknar inte"](../README.md)), så skaparen säger det.

2. **Resultatet tas ut med "Kopiera som text"** och en utskriftsstil. Knappen lägger
   schemat på urklipp som rader av typen `sön 4 okt · Tacksägelsedagen: Frida, Ola`;
   `Skriv ut` ger ett rent papper. Ingen PDF, ingen mejlning från servern, ingen
   lagring. Texten byggs i webbläsaren ur det som står på skärmen.

3. **Dubbelsvar hanteras av skaparen.** Enligt D-010 ligger både "Anna" och "Anna Ny"
   kvar som två deltagare, och algoritmen ser två personer. Ett kryss på brickan i
   brickraden tar bort personen från alla dagar och ur raden, för just den här
   sessionen, med ångra.

4. **"Kan inte" finns inte på arbetsbladet.** Avgjort: en person läggs aldrig på en dag
   hen sagt nej till, varken av förslaget eller av skaparen. När en bricka är markerad
   eller dras tonas de dagar personen inte kan ned och tar inte emot släppet. Röda
   brickor förekommer alltså inte. Gula gör det, men bara när förslaget inte hade något
   grönt att ta, eller när skaparen själv lägger dit dem.

5. **Ingen "nytt förslag"-knapp** i första versionen. Algoritmen är deterministisk, så
   samma svar ger samma förslag, och skaparen flyttar själv. Lätt att lägga till senare
   som en rotationsparameter i samma GET.

6. **Bara skaparen ser sidan.** Routen ligger under admin-token.

## Sidan

Route: `GET /a/{token}/schema` med valfri `?perDag=2`. Bara admin-token. Svarslänken
leder inte hit: schemat är skaparens arbete, inte gruppens vy (D-010 handlar om svaren,
inte om vad skaparen gör med dem).

Uppifrån och ned:

1. **Rubrik** med förfrågans titel och raden "Ett förslag, inget är sparat. Dra brickorna
   dit du vill, eller klicka på en bricka och sedan på en dag." Instruktionen står framme
   hela tiden, som i D-013.
2. **Väljaren "Personer per dag"** (fråga 1) och knappen "Räkna om".
3. **Brickraden**: en bricka per person som svarat, i den ordning de svarat. Varje bricka
   visar förnamnet och en siffra: hur många dagar personen har just nu. Siffran
   uppdateras när man flyttar, så att rättvisan syns. Härifrån drar man ner en kopia på
   en dag. Brickan i raden försvinner aldrig när man drar, den är en källa.
4. **Dagarna som ett rutnät**: en ruta per dag med veckodag, datum och kyrkoårsnamn på
   samma sätt som listan, och under det brickorna som ligger på dagen. Rutorna är
   släppytor. En tom dag visar "Ingen" i grått. Rutnätet är två till fyra kolumner på
   skärm och en kolumn på mobil.
5. **Knappar**: "Kopiera som text", "Skriv ut", "Avbryt". Avbryt är en vanlig länk till
   `/a/{token}`. Det finns inget att avbryta på servern.

### Brickan

- **Färg efter personens svar för den dagen**: grön för "kan", gul för "om det behövs".
  Samma toner som i svarsvyn (D-044), och ordet i en `title`/dold text så att färgen
  aldrig bär ensam. Rött finns inte: en dag personen sagt nej till går inte att lägga
  brickan på (fråga 4). Brickan i brickraden är neutral, den hör inte till någon dag.
- **Kryss** på varje bricka på en dag: tar bort den. Kryss på en bricka i brickraden: tar
  bort personen helt från arbetsbladet (fråga 3), med en "Ångra"-rad överst som lägger
  tillbaka allt personen hade.
- Samma person kan inte ligga två gånger på samma dag. Ett släpp på en dag som redan har
  personen gör ingenting.

### Flytta

**Dra och släpp är det man ser.** Det är vad folk är vana vid och det instruktionen
leder med. Det byggs på `pointerdown`/`pointermove`/`pointerup`, inte HTML5:s drag-API:
drag-API:t saknas i Chrome på Android och är lynnigt i Safari på iOS, medan
pekarhändelser fungerar på dator, iPad, iPhone och Android. Under drag följer brickan
pekaren, dagar personen kan lyser upp och dagar hen inte kan tonas ned. Släpp utanför
en dag avbryter. Därför behövs inget krav på dator eller iPad: samma kod fungerar på
alla, och det som skiljer är bara hur trångt det blir.

**Klicka-klicka finns under**, utan att behöva förklaras mer än i bisatsen i
instruktionen. Ett klick på en bricka markerar den och tänder samma dagar som ett drag
skulle; nästa klick på en dag lägger dit den. Klick på samma bricka igen, eller Escape,
avmarkerar. Samma sak händer alltså på skärmen oavsett om man drar eller klickar, så det
lär sig självt. Vägen finns för tre grupper: den som håller fingret stilla en sekund för
länge på en pekskärm och tappar draget, den som använder tangentbord, och den som
använder skärmläsare. Ett drag som slutar på samma ställe det började räknas som ett
klick, så ett fumligt drag blir en markering i stället för ingenting.

Brickorna är `<button>`-element så att fokus, Enter och mellanslag fungerar utan extra
arbete. Dagarna får `aria-label` med datum och kyrkoårsnamn. Ett meddelande i en
`aria-live`-region säger "Frida flyttad till söndag 4 oktober" vid varje flytt.

### Mobil

Rutnätet blir en kolumn, och en rad under rubriken säger "Det här går lättare på en
större skärm" på smala skärmar. Inget spärras: dra och klicka fungerar, det är bara
trångt. Skaparen sitter oftast vid en dator när schemat läggs, och vi kan ställa krav på
skaparen som vi inte ställer på dem som svarar.

## Algoritmen

Ren funktion i Java, `ministra.schedule.Scheduler`, utan Spring och utan slump:

```
propose(days, participants, perDay) -> Map<LocalDate, List<String>>
```

där `participants` bär varje persons svar per dag. Indata är samma vy som listan
(`PollView`), så inget nytt hämtas ur databasen.

Regler, i prioritetsordning:

1. **Aldrig "kan inte".** Hellre en tom plats.
2. **Grönt före gult, alltid.** En gul plats fylls bara när ingen grön finns kvar för
   dagen. Det gäller även om det betyder att samma gröna person får två gudstjänstdagar
   i rad, och även om den gröna redan har flest dagar: en person som sagt "kan" ska tas
   före en som sagt "om det behövs", oavsett rättvisa och spridning.
3. **Rättvist antal** bland de gröna: den som har färst dagar hittills får nästa. Vid
   lika: den som har längst tid sedan sin senaste dag.
4. **Utspritt** bland de gröna: samma person inte två gudstjänstdagar i rad om det går
   att undvika med en annan grön, och i övrigt så långt mellan en persons dagar som
   möjligt. Avståndet väger lika tungt som antalet: två dagar med fem veckor emellan är
   bättre än två dagar i rad. Med få svarande och många dagar går det inte, och då
   viker regeln. Den viker aldrig till gult.
5. **Knappa dagar först**: dagarna fylls i ordning efter hur få gröna som finns, inte
   kronologiskt. Den dag bara Frida kan får Frida innan hennes räknare gör henne
   "dyr" för dagar där tre andra kan. Ordningen i utdata är kronologisk ändå.

Deterministisk: samma indata ger alltid samma förslag. Lika-fall avgörs av
svarsordningen (den som svarade först), aldrig av namn i bokstavsordning, så att inte
"Anna" alltid får mer än "Åke".

Tester på algoritmen, utan databas:

- ingen hamnar på en dag hen sagt nej till
- skillnaden i antal dagar mellan den som har flest och färst är högst ett, när svaren
  tillåter det
- ingen ligger två dagar i rad när det finns en annan grön
- gul används bara när grön saknas, och en grön som har flest dagar och gick i går tas
  ändå före en gul
- en dag utan någon som kan blir tom, inte fel
- perDay=2 ger två per dag där det går, och en där bara en kan
- samma indata två gånger ger samma resultat

## Arkitektur

Servern räknar fram förslaget och renderar det. Allt därefter sker i webbläsaren.

| Del | Fil | Ansvar |
|---|---|---|
| Algoritm | `ministra/schedule/Scheduler.java` | ren funktion, testad |
| Vy-modell | `ministra/schedule/ScheduleView.java` | dagar med brickor, brickraden med räknare, färg per bricka |
| Controller | `PollController.schedule(...)` | `GET /a/{token}/schema`, läser `perDag`, bygger vyn |
| Mall | `src/main/jte/schedule.jte` | sidan, med all data i DOM:en: varje dag bär `data-date`, varje bricka `data-name` och `data-availability` |
| Skript | `static/js/schedule.js` | klicka-klicka, drag med pekarhändelser, spärr mot "kan inte"-dagar, kryss, räknare, ångra, kopiera som text |
| Stil | `ministra.css` | rutnät, brickor i de tre tonerna, markerat läge, utskrift |

Svaren för varje person och dag skickas till sidan som `data-`-attribut på ett dolt
element per person, så att skriptet kan färga en bricka rätt när den landar på en ny dag
utan att fråga servern. Det är förnamn och tre lägen: samma sak som redan står i
listan för alla med länken, ingen ny exponering.

Utan JavaScript visas förslaget som en färdig sida. Man kan läsa och skriva ut det, men
inte flytta. Det är den fungerande vägen som D-003 kräver: förbättring, inte
förutsättning. Knappen "Kopiera som text" göms utan skript, eftersom den inte kan
fungera.

Ingen ny databastabell, inget nytt fält, inget nytt mejl, ingen ny post i D-028. Loggen
får en rad på INFO: "Föreslog schema för förfrågan {id}: {dagar} dagar, {personer}
personer".

## Steg

1. **D-046** i beslutsloggen och omskrivning av README "Vad den inte gör", product.md
   "Vad appen inte gör" och AGENTS "Icke-mål".
2. **Scheduler** med tester. Klar och grön innan något UI rörs.
3. **ScheduleView** och controller-route med test: knappen syns vid tre svar och inte vid
   två, sidan svarar 404 med svarstoken, `perDag` utanför 1–4 blir 1.
4. **schedule.jte** och CSS: sidan renderad utan skript, rutnät, brickor, utskrift.
5. **schedule.js**: klicka-klicka först, kryss, räknare, ångra, kopiera som text.
6. **Drag med pekarhändelser** som sista lager.
7. **Knappen** på skaparens sida, villkorad på antal svar.
8. Manuell genomgång i Safari på iPhone och Chrome på Android: klicka-klicka fungerar,
   drag är trevligt om det fungerar.

Varje steg är en egen commit.

## Utanför

- Att spara schemat, dela det med gruppen eller mejla det. Skaparen tar det vidare
  själv, med texten på urklipp.
- Att veta hur många som behövs per dag utöver väljaren.
- PDF. Utskriftsstilen räcker.
- Att komma ihåg arbetsbladet över en sidladdning. `sessionStorage` vore billigt, men det
  är en annan sak än "stateless", och vi vet inte att det behövs.
