import { useEffect, useState } from "react";
import { fetchEvents } from "../api/api";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import AppHeader from "../components/AppHeader";
import { v4 as uuidv4 } from "uuid"; // ✅ thêm dòng này

export default function HomePage() {
  const [events, setEvents] = useState([]);
  const navigate = useNavigate();
  const setVisitorToken = useAuthStore(s => s.setVisitorToken);

  useEffect(() => {
    fetchEvents().then(setEvents);
  }, []);

  function handleBuy(event) {
    // ❌ const visitorToken = crypto.randomUUID();
    const visitorToken = uuidv4(); // ✅ FIX

    setVisitorToken(visitorToken);

    navigate("/waiting", {
      state: {
        event,
      },
    });
  }

  return (
    <div className="min-h-screen bg-gray-100">
      {/* ===== HEADER ===== */}
      <AppHeader title="Các trận đấu sắp diễn ra" />

      {/* ===== CONTENT ===== */}
      <div className="max-w-6xl mx-auto p-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {events.map(ev => {
            const eventTime = new Date(ev.eventTime);
            const day = eventTime.getDate();
            const month = eventTime.toLocaleString("en-US", { month: "short" });
            const time = eventTime.toLocaleTimeString([], {
              hour: "2-digit",
              minute: "2-digit",
            });

            return (
              <div
                key={ev.eventId}
                className="bg-white rounded-xl shadow-sm p-5 flex items-center justify-between"
              >
                {/* DATE */}
                <div className="flex items-center gap-4">
                  <div className="text-center min-w-[48px]">
                    <div className="text-xs text-red-500 uppercase">
                      {month}
                    </div>
                    <div className="text-2xl font-bold">
                      {day}
                    </div>
                    <div className="text-xs text-gray-500">
                      {time}
                    </div>
                  </div>

                  {/* INFO */}
                  <div>
                    <div className="font-semibold text-gray-900">
                      {ev.eventName}
                    </div>
                    <div className="text-sm text-gray-500">
                      {ev.venue || "Stadium"}, {ev.location || "City"}
                    </div>
                  </div>
                </div>

                {/* ACTION */}
                <button
                  onClick={() => handleBuy(ev)}
                  className="bg-green-400 hover:bg-green-500 text-black font-medium px-5 py-2 rounded-lg"
                >
                  Mua vé
                </button>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
