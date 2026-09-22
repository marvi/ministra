@param String title
@param gg.jte.Content content
@param boolean wide = false
@param String socialTitle = null
@param String socialDescription = null
@param String socialUrl = null
@param String socialImageUrl = null
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title>${title} · Ministra</title>
    <link rel="icon" href="/images/ministra-mark.svg" type="image/svg+xml">
    <link rel="icon" href="/favicon.ico" sizes="48x48">
    @if(socialUrl != null && socialTitle != null && socialDescription != null && socialImageUrl != null)
        <meta name="description" content="${socialDescription}">
        <meta property="og:title" content="${socialTitle}">
        <meta property="og:description" content="${socialDescription}">
        <meta property="og:type" content="website">
        <meta property="og:url" content="${socialUrl}">
        <meta property="og:site_name" content="Ministra">
        <meta property="og:locale" content="sv_SE">
        <meta property="og:image" content="${socialImageUrl}">
        <meta property="og:image:type" content="image/png">
        <meta property="og:image:width" content="1200">
        <meta property="og:image:height" content="630">
        <meta property="og:image:alt" content="Ministrant med processionsljus bredvid namnet Ministra">
    @endif
    <%-- v=2 bryter gamla cachade svar. Cache-Control kräver omvalidering framöver. --%>
    <link rel="stylesheet" href="/css/ministra.css?v=2">
    <%-- Självhostad, inga externa resurser (AGENTS.md). --%>
    <script src="/js/htmx-2.0.4.min.js" defer></script>
</head>
<%-- Bredden sitter på body, inte på main: annars vidgas innehållet på arbetsbladet
     medan topplisten står kvar och sidan får två vänsterkanter (D-048). --%>
<body class="${wide ? "wide" : ""}">
<header class="topbar">
    <div class="wrap">
        <a class="brand" href="/">
            <img src="/images/ministra-mark.svg" width="56" height="56" alt="">
            <span>Ministra</span>
        </a>
    </div>
</header>
<main class="wrap">
    ${content}
</main>
<footer class="footer">
    <div class="wrap">
        <p>Gjort med ♥ av <a href="https://marvi.io">marvi.io</a>. Frågor och kommentarer:
            <a href="mailto:markus@harnvi.net">markus@harnvi.net</a></p>
    </div>
</footer>
</body>
</html>
