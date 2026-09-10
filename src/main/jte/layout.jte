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
<%-- Bredden sitter på body, inte på main: annars vidgas innehållet på arbetsbladet
     medan topplisten står kvar och sidan får två vänsterkanter (D-048). --%>
<body class="${wide ? "wide" : ""}">
<header class="topbar">
    <div class="wrap">
        <a class="wordmark" href="/">Ministra</a>
    </div>
</header>
<main class="wrap">
    ${content}
</main>
<footer class="footer">
    <div class="wrap">
        <p>Ministra samlar in tillgänglighet inför gudstjänster. Inga konton.
            Förfrågningar raderas automatiskt.</p>
    </div>
</footer>
</body>
</html>
