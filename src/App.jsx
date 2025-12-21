import { BrowserRouter, Routes, Route } from "react-router-dom";
import HomePage from "./pages/HomePage";
import WaitingRoom from "./pages/WaitingRoom";
import TicketingEntry from "./pages/TicketingEntry";

import { MQTTProvider } from "./mqtt/MQTTProvider";
import { useAuthStore } from "./store/authStore";

export default function RootApp() {
  // visitorToken sống suốt flow (enqueue → waiting → ticketing)
  const visitorToken = useAuthStore(s => s.visitorToken);

  return (
    <BrowserRouter>
      {/* 🔑 MQTT connect 1 lần ở app-level */}
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
    </BrowserRouter>
  );
}
