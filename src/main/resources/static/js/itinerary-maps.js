/**
 * Initialises Leaflet maps on the itinerary page — one map per day card.
 *
 * Each .day-map element carries a data-activities attribute with a JSON array:
 *   [{ lat, lng, name, time }, ...]
 *
 * Renders numbered circular markers in chronological order with a dashed
 * polyline tracing the day's route. Auto-fits the viewport to the markers.
 */
(function () {
    'use strict';

    if (typeof L === 'undefined') {
        console.warn('[wyt] Leaflet not loaded; skipping map init');
        return;
    }

    const ROUTE_COLOR = '#c94f2c';   // matches --rust
    const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
    const ATTRIBUTION = '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';

    function init() {
        document.querySelectorAll('.day-map').forEach(initOne);
    }

    function initOne(el) {
        if (el.dataset.initialised === '1') return;
        el.dataset.initialised = '1';

        let activities;
        try {
            activities = JSON.parse(el.dataset.activities || '[]');
        } catch (e) {
            console.warn('[wyt] Bad day-map data on', el.id, e);
            return;
        }

        const valid = activities.filter(a =>
            Number.isFinite(a.lat) && Number.isFinite(a.lng) && a.lat !== 0 && a.lng !== 0);
        if (valid.length === 0) {
            el.style.display = 'none';
            return;
        }

        const map = L.map(el, { zoomControl: true, scrollWheelZoom: false })
            .setView([valid[0].lat, valid[0].lng], 13);

        L.tileLayer(TILE_URL, { attribution: ATTRIBUTION, maxZoom: 19 }).addTo(map);

        valid.forEach((a, i) => {
            const icon = L.divIcon({
                className: 'wyt-marker',
                html: `<div class="wyt-marker-pin">${i + 1}</div>`,
                iconSize: [30, 30],
                iconAnchor: [15, 15]
            });
            L.marker([a.lat, a.lng], { icon })
                .bindPopup(`<strong>${escapeHtml(a.time || '')}</strong>&nbsp;${escapeHtml(a.name || '')}`)
                .addTo(map);
        });

        if (valid.length > 1) {
            L.polyline(valid.map(a => [a.lat, a.lng]), {
                color: ROUTE_COLOR, weight: 3, opacity: 0.7, dashArray: '6,8'
            }).addTo(map);
        }

        const bounds = L.latLngBounds(valid.map(a => [a.lat, a.lng])).pad(0.20);
        map.fitBounds(bounds, { maxZoom: 15 });
    }

    function escapeHtml(s) {
        const div = document.createElement('div');
        div.textContent = String(s ?? '');
        return div.innerHTML;
    }

    // Expose the initializer so the streaming code can re-run it after each
    // new day-card lands in the DOM.
    window.WYT = window.WYT || {};
    window.WYT.initMaps = init;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
