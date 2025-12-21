import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import {
  checkoutBooking,
  confirmPayment,
  fetchTickets,
  releaseHold,
} from "../api/api";

export default function CheckoutScreen({
  holdToken,
  row,
  onBack,
  onPay,   // callback từ TicketingEntry
}) {
  const { state } = useLocation();
  const [seconds, setSeconds] = useState(600); // 10 phút
  const [loading, setLoading] = useState(false);
  const { event, visitorToken } = state || {};
  // ⏱ Countdown
  useEffect(() => {
    const timer = setInterval(() => {
      setSeconds(s => Math.max(0, s - 1));
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  //  Timeout → release hold
  useEffect(() => {
    if (seconds <= 0) {
      releaseHold({ holdToken });
      onBack();
    }
  }, [seconds, holdToken, onBack]);

  const mm = String(Math.floor(seconds / 60)).padStart(2, "0");
  const ss = String(seconds % 60).padStart(2, "0");

  async function handlePay() {
    setLoading(true);

    try {
      // 1️⃣ Checkout
      const checkoutRes = await checkoutBooking({ holdToken });
      const bookingId = checkoutRes.data.bookingId;

      // 2️⃣ Confirm payment
      await confirmPayment({ bookingId });

      // 3️⃣ Get tickets
      const ticketsRes = await fetchTickets({ bookingId });

      // báo ngược lên TicketingEntry
      onPay({
        bookingId,
        tickets: ticketsRes.data,
      });
    } finally {
      setLoading(false);
    }
  }

 return (
  <div className="min-h-screen bg-gray-100">

{/* ===== HEADER ===== */}
      <header className="h-14 bg-purple-800 text-white flex items-center px-4 relative">
        {/* Back button */}
        <button
          onClick={async () => {
            await releaseHold({ holdToken });
            onBack();
          }}
          className="absolute left-4 text-xl font-bold hover:opacity-80"
          title="Quay lại"
        >
          ←
        </button>

        {/* Title */}
        <div className="w-full text-center font-semibold">
          Xác nhận thanh toán
        </div>

    
      </header>
    {/* ===== CONTENT ===== */}
    <main className="max-w-5xl mx-auto p-6">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

        {/* ===== LEFT: ORDER SUMMARY ===== */}
        <div className="bg-white rounded-xl shadow-sm p-6 space-y-4">
          <h2 className="font-semibold text-lg">
            Tóm tắt đơn hàng
          </h2>

          <div className="text-sm space-y-2">
            <div className="flex justify-between">
              <span>Trận đấu:</span>
              <span className="font-medium">
                {row.eventName || "MU vs Liverpool"}
              </span>
            </div>

            <div className="flex justify-between">
              <span>Vé ({row.quantity}):</span>
              <span>
                {row.quantity} × {row.price.toLocaleString()}₫
              </span>
            </div>

            <div className="flex justify-between">
              <span>Ghế:</span>
              <span>
                {row.seats?.join(", ") || `Section ${row.sectionId}`}
              </span>
            </div>

            <div className="flex justify-between">
              <span>Phí dịch vụ:</span>
              <span>30₫</span>
            </div>

            <hr />

            <div className="flex justify-between font-semibold text-base">
              <span>TỔNG CỘNG:</span>
              <span>
                {(row.price * row.quantity + 30).toLocaleString()}₫
              </span>
            </div>
          </div>
        </div>

        {/* ===== RIGHT: PAYMENT ===== */}
        <div className="bg-white rounded-xl shadow-sm p-6 space-y-4">
          <h2 className="font-semibold text-lg">
            Thông tin thanh toán
          </h2>

          <div className="space-y-3">
            <input
              className="w-full border rounded px-3 py-2 text-sm"
              placeholder="Tên chủ thẻ"
              defaultValue="NGUYEN VAN A"
            />

            <input
              className="w-full border rounded px-3 py-2 text-sm"
              placeholder="Số thẻ"
              defaultValue="4000 1234 5678 9010"
            />

            <div className="flex gap-3">
              <input
                className="w-1/2 border rounded px-3 py-2 text-sm"
                placeholder="MM/YY"
              />
              <input
                className="w-1/2 border rounded px-3 py-2 text-sm"
                placeholder="CVV"
              />
            </div>
          </div>

          <button
            disabled={loading}
            onClick={handlePay}
            className="w-full mt-4 bg-green-400 hover:bg-green-500 text-black font-semibold py-3 rounded-lg"
          >
            {loading
              ? "Đang xử lý..."
              : `Thanh toán ${(row.price * row.quantity + 30).toLocaleString()}₫`}
          </button>

          {/* Countdown */}
          <div className="text-center text-sm text-red-600">
            Thời gian giữ vé còn lại: {mm}:{ss}
          </div>
        </div>

      </div>
    </main>
  </div>
);

}
