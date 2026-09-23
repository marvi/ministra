# Klientkod

`htmx-2.0.4.min.js` är hämtad från https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js och
serveras härifrån. Inga externa resurser laddas vid körning — se AGENTS.md.

Filnamnet bär versionen så att en uppgradering blir en synlig ändring och inte en tyst
ersättning av ett cachat innehåll.

`umami-2026-09-23.js` är en ögonblicksbild av
https://umami.marvi.work/script.js, hämtad 2026-09-23. Den serveras från Ministra men
skickar statistiken till Umami med `data-host-url`. Datumet i filnamnet gör även den här
leverantörskoden möjlig att granska och uppgradera uttryckligt.

`umami-privacy.js` är vår egen skyddsregel. Den skriver om `/s/{token}` till den generiska
sidvägen `/s/` med en generell titel, stoppar statistik från `/a/` och tar bort en
capability-länk ur refererfältet.

`umami-event.js` skickar en fast, servervald händelse efter ett lyckat svar och tar sedan
bort `?tack` ur adressfältet så att en omladdning inte dubbelräknas. Den läser inga
formulärfält.
