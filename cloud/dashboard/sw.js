self.addEventListener("install", (event) => {
  event.waitUntil(caches.open("pet-guardian-cloud-v1").then((cache) => cache.addAll([
    "./",
    "./index.html",
    "./styles.css",
    "./app.js",
    "./config.js",
    "./manifest.webmanifest"
  ])));
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  event.respondWith(caches.match(event.request).then((cached) => cached || fetch(event.request)));
});
