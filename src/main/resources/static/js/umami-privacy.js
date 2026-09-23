(function () {
    "use strict";

    var responsePath = /^\/s(?:\/|$)/;
    var adminPath = /^\/a(?:\/|$)/;

    function pathOf(value) {
        try {
            return new URL(value, window.location.origin).pathname;
        } catch (_error) {
            return "";
        }
    }

    window.ministraUmamiBeforeSend = function (_type, payload) {
        var urlPath = typeof payload.url === "string" ? pathOf(payload.url) : "";
        if (adminPath.test(urlPath)) {
            return false;
        }

        var sanitized = responsePath.test(urlPath)
            ? Object.assign({}, payload, {url: "/s/", title: "Svara · Ministra"})
            : payload;
        var referrerPath = typeof payload.referrer === "string" ? pathOf(payload.referrer) : "";
        if (responsePath.test(referrerPath) || adminPath.test(referrerPath)) {
            sanitized = Object.assign({}, sanitized, {referrer: ""});
        }
        return sanitized;
    };
}());
