@param String title
@param gg.jte.Content content
@param boolean wide = false
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title>${title} · Ministra</title>
    <link rel="stylesheet" href="/css/ministra.css">
    <%-- Självhostad, inga externa resurser (AGENTS.md). --%>
    <script src="/js/htmx-2.0.4.min.js" defer></script>
</head>
<body>
<header class="topbar">
    <a class="wordmark" href="/">Ministra</a>
</header>
<main class="${wide ? "wide" : ""}">
    ${content}
</main>
<footer class="footer">
    <p>Ministra samlar in tillgänglighet inför gudstjänster. Inga konton.
        Förfrågningar raderas automatiskt.</p>
</footer>
</body>
</html>
