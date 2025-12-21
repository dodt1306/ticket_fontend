import { apiFetch } from "./apiClient";

/* ======================================================
 * UNAUTHORIZED HANDLER (GLOBAL)
 * ====================================================== */
function handleUnauthorized(res, bodyText) {
  if (
    res?.status === 401 ||
    bodyText?.includes("Unauthorized") ||
    bodyText?.includes("JWR_ERROR")
  ) {
    alert("Phiên làm việc đã hết hạn. Vui lòng quay lại trang chủ.");
    window.location.href = "/";
    return true;
  }
  return false;
}

/* ======================================================
 * PUBLIC APIs – KHÔNG cần access_token
 * ====================================================== */

/**
 * Lấy danh sách sự kiện
 */
export async function fetchEvents() {
  const res = await apiFetch("/getEvents");

  if (!res.ok) {
    const text = await res.text();
    handleUnauthorized(res, text);
    throw new Error(text);
  }

  return res.json();
}

/**
 * Vào hàng đợi (waiting room)
 */
export async function enqueue({ eventId, visitorToken }) {
  const res = await apiFetch("/enqueue", {
    method: "POST",
    body: { eventId, visitorToken },
  });

  if (!res.ok) {
    const text = await res.text();
    handleUnauthorized(res, text);
    throw new Error(text);
  }

  return res.json();
}

/**
 * Check waiting ready
 */
export async function waitingReady({ eventId, visitorToken }) {
  const res = await apiFetch("/waiting/ready", {
    method: "POST",
    body: { eventId, visitorToken },
  });

  if (!res.ok) {
    const text = await res.text();
    handleUnauthorized(res, text);
    throw new Error(text);
  }

  return res.json();
}

/* ======================================================
 * PROTECTED APIs – CẦN access_token
 * ====================================================== */

/**
 * Lấy danh sách listings theo section
 */
export async function fetchEventListings({
  eventId,
  quantity,
  adjacent = true,
}) {
  const res = await apiFetch(
    `/events/${eventId}/listings?quantity=${quantity}&adjacent=${adjacent}`
  );

  if (!res.ok) {
    const text = await res.text();
    if (handleUnauthorized(res, text)) return;
    throw new Error(`LISTINGS_API_FAILED ${res.status}: ${text}`);
  }

  const json = await res.json();

  if (!json.success) {
    throw new Error(json.code || "LISTINGS_API_ERROR");
  }

  return json.data;
}

/**
 * Tạo hold ghế
 */
export async function createHold({
  eventId,
  sectionId,
  quantity,
  price,
}) {
  const res = await apiFetch(`/events/${eventId}/hold`, {
    method: "POST",
    body: {
      eventId,
      sectionId,
      quantity,
      price,
    },
  });

  // ❌ Không ok
  if (!res.ok) {
    const text = await res.text();

    // 401 → xử lý auth như cũ
    if (handleUnauthorized(res, text)) return;

    // 🎯 403 + NO_ADJACENT_SEATS
    if (res.status === 403) {
      try {
        const data = JSON.parse(text);

        if (data.code === "NO_ADJACENT_SEATS") {
          alert("Không còn ghế liền kề phù hợp. Vui lòng chọn số lượng ít hơn hoặc khu khác.");
          return;
        }
      } catch (e) {
        // ignore JSON parse error
      }
    }

    // ❌ lỗi khác
    throw new Error(text);
  }

  return res.json();
}


/**
 * Hủy hold
 */
export async function releaseHold({ eventId, holdToken }) {
  const res = await apiFetch(`/events/${eventId}/releaseHold`, {
    method: "POST",
    body: { holdToken },
  });

  if (!res.ok) {
    const text = await res.text();
    if (handleUnauthorized(res, text)) return;
    throw new Error(text);
  }
}

/**
 * Checkout booking
 */
export async function checkoutBooking({ eventId, holdToken }) {
  const res = await apiFetch(`/events/${eventId}/checkout`, {
    method: "POST",
    body: { holdToken },
  });

  if (!res.ok) {
    const text = await res.text();
    if (handleUnauthorized(res, text)) return;
    throw new Error(text);
  }

  return res.json();
}

/**
 * Xác nhận thanh toán
 */
export async function confirmPayment({ bookingId }) {
  const res = await apiFetch("/events/payment/confirm", {
    method: "POST",
    body: { bookingId },
  });

  if (!res.ok) {
    const text = await res.text();
    if (handleUnauthorized(res, text)) return;
    throw new Error(text);
  }

  return res.json();
}

/**
 * Lấy vé (QR / ticket list)
 */
export async function fetchTickets({ bookingId }) {
  const res = await apiFetch(
    `/events/booking/${bookingId}/tickets`
  );

  if (!res.ok) {
    const text = await res.text();
    if (handleUnauthorized(res, text)) return;
    throw new Error(text);
  }

  return res.json();
}
