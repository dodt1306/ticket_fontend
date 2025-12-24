import { useEffect, useState } from "react";
import mqtt from "mqtt";
import { ENV } from "../config/env";

/* =====================================================
 * API helpers (ADMIN)
 * ===================================================== */
async function apiFetch(path, options = {}) {
  const res = await fetch(`${ENV.API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
    ...options,
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || "API error");
  }

  return res.json();
}

const adminApi = {
  fetchEvents: () => apiFetch("/admin/events"),

  openWaiting: eventId =>
    apiFetch(`/admin/events/${eventId}/actions/open-waiting`, {
      method: "POST",
    }),

  openSelling: eventId =>
    apiFetch(`/admin/events/${eventId}/actions/open-selling`, {
      method: "POST",
    }),

  closeSelling: eventId =>
    apiFetch(`/admin/events/${eventId}/actions/close-selling`, {
      method: "POST",
    }),

  resetEvent: eventId =>
    apiFetch(`/admin/events/${eventId}/actions/reset-event`, {
      method: "POST",
    }),
};

/* =====================================================
 * Status badge
 * ===================================================== */
function StatusBadge({ status }) {
  const map = {
    LOCKED: "bg-gray-300 text-gray-800",
    WAITING: "bg-yellow-200 text-yellow-900",
    SELLING: "bg-green-300 text-green-900",
    ENDED: "bg-red-300 text-red-900",
  };

  return (
    <span
      className={`px-2 py-1 rounded text-xs font-semibold ${
        map[status] || "bg-gray-200"
      }`}
    >
      {status}
    </span>
  );
}

/* =====================================================
 * Admin Event Control UI
 * ===================================================== */
export default function AdminEventControl() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  /* ================================
     Load events
     ================================ */
  const load = () => {
    setLoading(true);
    adminApi
      .fetchEvents()
      .then(res => setEvents(res.data?.events || []))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  };

  /* ================================
     Initial load + MQTT realtime
     ================================ */
  useEffect(() => {
    load();

    const client = mqtt.connect(ENV.MQTT_URL);

    client.on("connect", () => {
      console.log("[ADMIN MQTT] connected");
      client.subscribe("event/+");
    });

    client.on("message", (_, payload) => {
      try {
        const msg = JSON.parse(payload.toString());
        if (msg.type === "EVENT_STATUS") {
          setEvents(prev =>
            prev.map(ev =>
              ev.eventId === msg.eventId
                ? { ...ev, status: msg.status }
                : ev
            )
          );
        }
      } catch (e) {
        console.error("MQTT parse error", e);
      }
    });

    return () => client.end();
  }, []);

  /* ================================
     Handle admin actions
     ================================ */
  const handleAction = async (action, eventId) => {
    try {
      setLoading(true);

      if (action === "OPEN_WAITING") {
        await adminApi.openWaiting(eventId);
      }

      if (action === "OPEN_SELLING") {
        await adminApi.openSelling(eventId);
      }

      if (action === "CLOSE_SELLING") {
        await adminApi.closeSelling(eventId);
      }

      if (action === "RESET_EVENT") {
        const ok = window.confirm(
          "DEV ONLY: Reset event về LOCKED và xóa toàn bộ queue/session?"
        );
        if (!ok) return;
        await adminApi.resetEvent(eventId);
      }

      await load();
    } catch (e) {
      alert(e.message);
    } finally {
      setLoading(false);
    }
  };

  /* =====================================================
   * UI
   * ===================================================== */
  return (
    <div className="min-h-screen bg-gray-100 p-6">
      <h1 className="text-2xl font-bold mb-4">
        Admin – Event Control
        {ENV.IS_DEV && (
          <span className="ml-2 text-xs text-red-500">(DEV)</span>
        )}
      </h1>

      {error && <div className="text-red-600 mb-2">{error}</div>}

      <div className="bg-white rounded-xl shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="text-left p-3">Event</th>
              <th className="text-left p-3">Event ID</th>
              <th className="text-left p-3">Time</th>
              <th className="text-left p-3">Status</th>
              <th className="text-left p-3">Action</th>
            </tr>
          </thead>

          <tbody>
            {events.map(ev => (
              <tr key={ev.eventId} className="border-t">
                <td className="p-3 font-medium">{ev.eventName}</td>

                <td className="p-3 font-mono text-xs text-gray-600">
                  {ev.eventId}
                </td>

                <td className="p-3">
                  {new Date(ev.eventTime).toLocaleString()}
                </td>

                <td className="p-3">
                  <StatusBadge status={ev.status} />
                </td>

                <td className="p-3 space-x-2">
                  {ev.status === "LOCKED" && (
                    <button
                      onClick={() =>
                        handleAction("OPEN_WAITING", ev.eventId)
                      }
                      className="px-3 py-1 rounded bg-yellow-400 hover:bg-yellow-500"
                    >
                      Open Waiting
                    </button>
                  )}

                  {ev.status === "WAITING" && (
                    <button
                      onClick={() =>
                        handleAction("OPEN_SELLING", ev.eventId)
                      }
                      className="px-3 py-1 rounded bg-green-400 hover:bg-green-500"
                    >
                      Open Selling
                    </button>
                  )}

                  {ev.status === "SELLING" && (
                    <button
                      onClick={() =>
                        handleAction("CLOSE_SELLING", ev.eventId)
                      }
                      className="px-3 py-1 rounded bg-red-400 hover:bg-red-500"
                    >
                      Pause Selling
                    </button>
                  )}

                  {ev.status === "ENDED" && (
                    <button
                      onClick={() =>
                        handleAction("OPEN_SELLING", ev.eventId)
                      }
                      className="px-3 py-1 rounded bg-green-400 hover:bg-green-500"
                    >
                      Resume Selling
                    </button>
                  )}

                  {ENV.IS_DEV && (
                    <button
                      onClick={() =>
                        handleAction("RESET_EVENT", ev.eventId)
                      }
                      className="px-3 py-1 rounded bg-gray-300 hover:bg-gray-400 text-xs"
                    >
                      Reset (DEV)
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {loading && (
          <div className="p-4 text-center text-gray-500">
            Loading…
          </div>
        )}
      </div>
    </div>
  );
}
