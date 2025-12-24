import { BrowserRouter, Routes, Route } from "react-router-dom";
import HomePage from "./pages/HomePage";
import WaitingRoom from "./pages/WaitingRoom";
import TicketingEntry from "./pages/TicketingEntry";
import AdminEventControl from "./admin/AdminEventControl";

import { MQTTProvider } from "./mqtt/MQTTProvider";
import { useAuthStore } from "./store/authStore";

export default function RootApp() {
  const visitorToken = useAuthStore(s => s.visitorToken);

  return (
    <BrowserRouter>
      <Routes>

        {/* ===== USER FLOW ===== */}
        <Route
          path="/*"
          element={
            <MQTTProvider visitorToken={visitorToken}>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/waiting" element={<WaitingRoom />} />
                <Route
                  path="/events/:eventId/tickets"
                  element={<TicketingEntry />}
                />
              </Routes>
            </MQTTProvider>
          }
        />

        {/* ===== ADMIN FLOW ===== */}
        <Route
          path="/admin/events"
          element={<AdminEventControl />}
        />

      </Routes>
    </BrowserRouter>
  );
}
