import { useState } from "react";
import ListingPanel from "./components/ListingPanel";
import StadiumSeatMap from "./components/StadiumSeatMap";
import { listingsBySection } from "./data/listings";

export default function App() {
  // ===== STATE =====
  const [step, setStep] = useState(1);
  const [quantity, setQuantity] = useState(null);
  const [hoverSection, setHoverSection] = useState(null);
  const [selectedSection, setSelectedSection] = useState(null);

  // ===== 1. SECTIONS HỢP LỆ THEO QUANTITY (MAP + LIST DÙNG CHUNG) =====
  const availableSections = listingsBySection.filter(
    sec =>
      quantity >= sec.requestedMinQty &&
      quantity <= sec.requestedMaxQty
  );

  // ===== 2. LISTING BỊ FILTER THEO SECTION (MAP KHÔNG) =====
  const filteredSections = selectedSection
    ? availableSections.filter(sec => sec.sectionId === selectedSection)
    : availableSections;

  // ===== 3. MAP CHỈ CẦN BIẾT SECTION NÀO CÓ VÉ =====
  const availableSectionIds = availableSections.map(
    sec => sec.sectionId
  );

  // ===== 4. CLICK SECTION – LOGIC LINH ĐỘNG (SEATPICK CHUẨN) =====
  const onSelectSection = (sectionId) => {
    setSelectedSection(prev => {
      // Click lại chính section đang chọn → bỏ chọn
      if (prev === sectionId) return null;

      // Click section khác → chuyển thẳng
      return sectionId;
    });
  };

  // ===== RENDER =====
  return (
    <>
      {/* ================= SCREEN 1: CHỌN QUANTITY ================= */}
      {step === 1 && (
        <div className="modal">
          <div className="modal-box">
            <h3>How many tickets are you looking for?</h3>
            <div className="qty-grid">
              {[1, 2, 3, 4].map(q => (
                <button
                  key={q}
                  onClick={() => {
                    setQuantity(q);
                    setStep(2);
                  }}
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ================= SCREEN 2: LIST + MAP ================= */}
      {step === 2 && (
        <div className="layout">
          {/* ===== LEFT: LISTING ===== */}
          <ListingPanel
            sections={filteredSections}
            onHover={setHoverSection}
            onSelectSection={onSelectSection}
            selectedSection={selectedSection}
          />

          {/* ===== RIGHT: SEAT MAP ===== */}
          <StadiumSeatMap
            sectionsWithTickets={availableSectionIds}
            hoverSection={hoverSection}
            selectedSection={selectedSection}
            onSelect={onSelectSection}
          />
        </div>
      )}
    </>
  );
}
