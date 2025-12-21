import { useEffect, useState, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import { enqueue, waitingReady } from "../api/api";
import { useMQTTClient } from "../mqtt/MQTTProvider";
import AppHeader from "../components/AppHeader";

/* ===============================
 * STATUS BADGE
 * =============================== */
function StatusBadge({ status }) {
  if (!status) return null;

  const map = {
    LOCKED: {
      text: "Chưa mở bán",
      cls: "bg-gray-200 text-gray-700",
    },
    WAITING: {
      text: "Đang xếp hàng",
      cls: "bg-yellow-100 text-yellow-800",
    },
    SELLING: {
      text: "Đang mở bán",
      cls: "bg-green-100 text-green-800",
    },
  };

  const cfg = map[status];
  if (!cfg) return null;

  return (
    <span
      className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-medium ${cfg.cls}`}
    >
      {cfg.text}
    </span>
  );
}

export default function WaitingRoom() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const client = useMQTTClient();

  const { event } = state || {};
  const eventId = event?.eventId;

  const visitorToken = useAuthStore(s => s.visitorToken);
  const setAccessToken = useAuthStore(s => s.setAccessToken);

  // ===== LOCAL STATE =====
  const [status, setStatus] = useState(event?.status); // LOCKED | WAITING | SELLING
  const [queuePosition, setQueuePosition] = useState(null);
  const [seconds, setSeconds] = useState(0);

  // ===== TIME SOURCE FOR LOCKED =====
  const saleStartAtMs = event?.saleStartAt
    ? new Date(event.saleStartAt).getTime()
    : null;

  // ===== GUARDS =====
  const enqueueDoneRef = useRef(false);
  const readySentRef = useRef(false);

  /* =====================================================
   * MQTT: EVENT_STATUS + ACCESS_GRANTED
   * ===================================================== */
  useEffect(() => {
    if (!client || !visitorToken || !eventId) return;

    const visitorTopic = `visitor/${visitorToken}`;
    const eventTopic = `event/${eventId}`;

    client.subscribe(visitorTopic);
    client.subscribe(eventTopic);

    const onMessage = (_, payload) => {
      try {
        const msg = JSON.parse(payload.toString());

        if (msg.type === "EVENT_STATUS" && msg.eventId === eventId) {
          setStatus(msg.status);
          return;
        }

        if (msg.type === "ACCESS_GRANTED") {
          setAccessToken(msg.accessToken);
          navigate(`/events/${eventId}/tickets`, {
            state: { event, visitorToken },
          });
        }
      } catch (e) {
        console.error("MQTT parse error", e);
      }
    };

    client.on("message", onMessage);
    return () => client.off("message", onMessage);
  }, [client, visitorToken, eventId, navigate, setAccessToken, event]);

  /* =====================================================
   * LOCKED COUNTDOWN
   * ===================================================== */
  useEffect(() => {
    if (status !== "LOCKED") return;
    if (!saleStartAtMs) return;

    const tick = () => {
      const diff = Math.max(
        0,
        Math.floor((saleStartAtMs - Date.now()) / 1000)
      );
      setSeconds(diff);
    };

    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [status, saleStartAtMs]);

  /* =====================================================
   * ENQUEUE (WAITING / SELLING – 1 LẦN)
   * ===================================================== */
  useEffect(() => {
    if (status !== "WAITING" && status !== "SELLING") return;
    if (!visitorToken || !eventId) return;
    if (enqueueDoneRef.current) return;

    enqueueDoneRef.current = true;

    enqueue({ eventId, visitorToken })
      .then(res => {
        setQueuePosition(res.queuePosition);
        if (typeof res.ttlSeconds === "number") {
          setSeconds(res.ttlSeconds);
        }
      })
      .catch(err => {
        console.error("enqueue failed", err);
      });
  }, [status, visitorToken, eventId]);

  /* =====================================================
   * READY SIGNAL (SAU ENQUEUE – 1 LẦN)
   * ===================================================== */
  useEffect(() => {
    if (status !== "WAITING" && status !== "SELLING") return;
    if (!visitorToken || !eventId) return;
    if (readySentRef.current) return;
    if (!enqueueDoneRef.current) return;

    readySentRef.current = true;

    waitingReady({ visitorToken, eventId }).catch(err => {
      console.error("waitingReady failed", err);
    });
  }, [status, visitorToken, eventId]);

  /* =====================================================
   * UI
   * ===================================================== */
  const eventDate = event?.eventTime
  ? new Date(event.eventTime)
  : null;

const month = eventDate
  ? eventDate.toLocaleString("en-US", { month: "short" })
  : "";

const day = eventDate
  ? eventDate.getDate()
  : "";

const weekday = eventDate
  ? eventDate.toLocaleString("en-US", { weekday: "short" })
  : "";
  /* =====================================================
 * UI
 * ===================================================== */
return (
  <div className="min-h-screen bg-gray-100">
    {/* ===== GLOBAL HEADER ===== */}
    <AppHeader title="Phòng chờ mua vé" />

    <div className="flex items-center justify-center px-4 py-8">
      <div className="bg-white w-full max-w-4xl rounded-xl shadow-md p-6 space-y-6">

        {/* ===== EVENT HEADER ===== */}
        <div className="flex gap-4 items-start">
          <div className="bg-red-500 text-white rounded-lg px-3 py-2 text-center">
            <div className="text-xs uppercase">
              {month}
            </div>
            <div className="text-xl font-bold">
              {day}
            </div>
            <div className="text-xs">
              {weekday}
            </div>
          </div>

          <div className="flex-1">
            <div className="flex items-center gap-2">
              <div className="font-semibold text-gray-900">
                {event?.eventName || "Event name"}
              </div>
              <StatusBadge status={status} />
            </div>

            <div className="text-sm text-gray-500">
              {event?.venue || "Venue"}
            </div>
            <div className="text-sm text-gray-500">
              {event?.location || "City, Country"}
            </div>
          </div>
        </div>

        <hr />

        {/* ===== WAITING CONTENT ===== */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">
            You're in the waiting room!
          </h2>

          <p className="text-sm text-gray-600 leading-relaxed">
            Vé cho sự kiện này chưa được mở bán. Khi sự kiện bắt đầu,
            bạn sẽ tự động được xếp vào hàng đợi. Điều này đảm bảo quyền
            truy cập công bằng cho tất cả mọi người. Cảm ơn sự kiên nhẫn
            của bạn.
          </p>

          {queuePosition != null && (
            <div className="text-sm text-gray-500">
              Vị trí trong hàng đợi: <b>#{queuePosition}</b>
            </div>
          )}

          {status === "LOCKED" && (
            <div className="text-sm text-gray-500">
              Thời gian còn lại: {seconds}s
            </div>
          )}

          {status !== "LOCKED" && (
            <div className="text-sm text-gray-500">
              Thời gian ước tính: {seconds}s
            </div>
          )}

          <div className="flex items-center gap-3 text-sm text-gray-600 pt-2">
            <span className="w-5 h-5 border-2 border-gray-300 border-t-red-500 rounded-full animate-spin" />
            Đang kết nối…
          </div>
        </div>
      </div>
    </div>
  </div>
);

}
