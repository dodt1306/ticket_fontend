import { QRCodeCanvas } from "qrcode.react";
import AppHeader from "../components/AppHeader";
import { useNavigate,useLocation } from "react-router-dom";
export default function SuccessScreen({
  bookingId,
  tickets,
  row,
  onHome, // optional callback
}) {
  const ticketList = tickets?.tickets ?? [];
  const firstTicket = ticketList[0];
  const { state } = useLocation();
  const { event,sections } = state || {};
  // Giả lập info (có thể map từ backend sau)
  const eventName = event.eventName;
  const eventDate = row.sectionId;
  const seats = ticketList.map(t => t.seatId).join(", ");
  const venue = event.venue;
  const navigate = useNavigate();
  return (
    <div className="min-h-screen bg-gray-100">
      {/* ===== HEADER ===== */}
      <AppHeader title="Thanh toán thành công" />

      {/* ===== CONTENT ===== */}
      <div className="flex items-center justify-center px-4 py-10">
        <div className="bg-white rounded-xl shadow-md w-full max-w-md p-8 text-center space-y-6">

          {/* SUCCESS ICON */}
          <div className="flex justify-center">
            <div className="w-14 h-14 rounded-full bg-green-100 flex items-center justify-center">
              <span className="text-3xl text-green-600">✓</span>
            </div>
          </div>

          {/* TITLE */}
          <div>
            <h2 className="text-xl font-semibold">
              Thanh toán thành công!
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Vé điện tử của bạn đã được tạo. Vui lòng kiểm tra email
               và sử dụng mã QR dưới đây để vào sân.
            </p>
          </div>

          {/* EVENT INFO */}
          <div className="border rounded-lg p-4 text-left text-sm space-y-1">
            <div>
              <span className="font-medium">Trận:</span>{" "}
              <span className="text-purple-700 font-semibold">
                {eventName}
              </span>
            </div>
            <div>Khán Đài: {eventDate}</div>
            <div>Ghế: {seats || "A-10-05, A-10-06"}</div>
            <div>{venue}</div>
            <div className="text-xs text-gray-400 mt-1">
              Booking ID: {bookingId}
            </div>
          </div>

          {/* QR */}
          <div className="space-y-2">
            <div className="text-sm font-medium">
              Mã QR Vé Của Bạn (Quét để kiểm tra)
            </div>

            <div className="flex justify-center">
              {firstTicket?.qrCode ? (
                <QRCodeCanvas
                  value={firstTicket.qrCode}
                  size={180}
                  level="H"
                  includeMargin
                />
              ) : (
                <div className="w-40 h-40 border rounded flex items-center justify-center text-xs text-gray-400">
                  QR Code
                </div>
              )}
            </div>
          </div>

          {/* ACTION */}
          <button
            onClick={() => navigate("/")}
            className="mt-4 bg-gray-100 hover:bg-gray-200 text-sm px-4 py-2 rounded"
          >
            Về trang chủ
          </button>
        </div>
      </div>
    </div>
  );
}
