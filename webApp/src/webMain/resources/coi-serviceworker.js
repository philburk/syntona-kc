/*! coi-serviceworker v0.1.7 (with null-body status fix) - Guido Zuidhof and contributors, licensed under MIT */
let coepCredentialless = false;

if ("undefined" === typeof window) {
    self.addEventListener("install", () => self.skipWaiting());
    self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));

    self.addEventListener("message", (e) => {
        if (e.data) {
            if ("deregister" === e.data.type) {
                self.registration.unregister().then(() => self.clients.matchAll()).then((clients) => {
                    clients.forEach((client) => client.navigate(client.url));
                });
            } else if ("coepCredentialless" === e.data.type) {
                coepCredentialless = e.data.value;
            }
        }
    });

    self.addEventListener("fetch", function (e) {
        const r = e.request;
        if ("only-if-cached" === r.cache && "same-origin" !== r.mode) return;

        const req = (coepCredentialless && "no-cors" === r.mode) ? new Request(r, { credentials: "omit" }) : r;
        e.respondWith(
            fetch(req).then((response) => {
                if (0 === response.status) return response;

                const headers = new Headers(response.headers);
                headers.set("Cross-Origin-Embedder-Policy", coepCredentialless ? "credentialless" : "require-corp");
                if (!coepCredentialless) {
                    headers.set("Cross-Origin-Resource-Policy", "cross-origin");
                }
                headers.set("Cross-Origin-Opener-Policy", "same-origin");

                // Status 204 (No Content), 205 (Reset Content), and 304 (Not Modified) must have a null body per Fetch API spec.
                const isNullBodyStatus = [101, 204, 205, 304].includes(response.status);
                const body = (isNullBodyStatus || !response.body) ? null : response.body;

                return new Response(body, {
                    status: response.status,
                    statusText: response.statusText,
                    headers: headers
                });
            }).catch((err) => {
                console.error("coi-serviceworker fetch error:", err);
                return fetch(r);
            })
        );
    });
} else {
    (() => {
        const reloadedBySelf = window.sessionStorage.getItem("coiReloadedBySelf");
        window.sessionStorage.removeItem("coiReloadedBySelf");

        const isCoepDegrade = "coepdegrade" === reloadedBySelf;
        const coi = {
            shouldRegister: () => !reloadedBySelf,
            shouldDeregister: () => false,
            coepCredentialless: () => true,
            coepDegrade: () => true,
            doReload: () => window.location.reload(),
            quiet: false,
            ...window.coi
        };

        const nav = navigator;
        const controller = nav.serviceWorker && nav.serviceWorker.controller;
        if (controller && !window.crossOriginIsolated) {
            window.sessionStorage.setItem("coiCoepHasFailed", "true");
        }
        const coiCoepHasFailed = window.sessionStorage.getItem("coiCoepHasFailed");

        if (controller) {
            const degrade = coi.coepDegrade() && !(isCoepDegrade || window.crossOriginIsolated);
            nav.serviceWorker.controller.postMessage({
                type: "coepCredentialless",
                value: !(degrade || (coiCoepHasFailed && coi.coepDegrade())) && coi.coepCredentialless()
            });
            if (degrade) {
                if (!coi.quiet) console.log("Reloading page to degrade COEP.");
                window.sessionStorage.setItem("coiReloadedBySelf", "coepdegrade");
                coi.doReload("coepdegrade");
            }
            if (coi.shouldDeregister()) {
                nav.serviceWorker.controller.postMessage({ type: "deregister" });
            }
        }

        if (false === window.crossOriginIsolated && coi.shouldRegister()) {
            if (window.isSecureContext) {
                if (nav.serviceWorker) {
                    nav.serviceWorker.register(window.document.currentScript.src).then(
                        (registration) => {
                            if (!coi.quiet) console.log("COOP/COEP Service Worker registered", registration.scope);
                            registration.addEventListener("updatefound", () => {
                                if (!coi.quiet) console.log("Reloading page to make use of updated COOP/COEP Service Worker.");
                                window.sessionStorage.setItem("coiReloadedBySelf", "updatefound");
                                coi.doReload();
                            });
                            if (registration.active && !nav.serviceWorker.controller) {
                                if (!coi.quiet) console.log("Reloading page to make use of COOP/COEP Service Worker.");
                                window.sessionStorage.setItem("coiReloadedBySelf", "notcontrolling");
                                coi.doReload();
                            }
                        },
                        (err) => {
                            if (!coi.quiet) console.error("COOP/COEP Service Worker failed to register:", err);
                        }
                    );
                } else if (!coi.quiet) {
                    console.error("COOP/COEP Service Worker not registered, perhaps due to private mode.");
                }
            } else if (!coi.quiet) {
                console.log("COOP/COEP Service Worker not registered, a secure context is required.");
            }
        }
    })();
}