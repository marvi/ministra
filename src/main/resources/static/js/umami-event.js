(function () {
    "use strict";

    var script = document.currentScript;
    var event = script && script.getAttribute("data-event");
    if (event && window.umami) {
        window.umami.track(event);
    }

    // Omladdning av tacksidan ska inte räknas som ett nytt inskickat svar.
    if (window.location.search === "?tack") {
        window.history.replaceState(null, "", window.location.pathname + window.location.hash);
    }
}());
