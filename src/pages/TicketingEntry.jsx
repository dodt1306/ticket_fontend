import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import {
  fetchEventListings,
  createHold,
  checkoutBooking,
  confirmPayment,
  fetchTickets,
  releaseHold,
} from "../api/api";

import SeatGeekLayout from "../components/SeatGeekLayout";
import QuantityModal from "../components/QuantityModal";
import ListingPanel from "../components/ListingPanel";
import CheckoutFullPanel from "../components/CheckoutFullPanel";
import StadiumSeatMap from "../components/StadiumSeatMap";
import CheckoutScreen from "../components/CheckoutScreen";
import SuccessScreen from "../components/SuccessScreen";

export default function TicketingEntry() {
  const { state } = useLocation();
  const { event, visitorToken } = state || {};

  const EVENT_ID = event?.eventId;

  // ===== STEP =====
  const [step, setStep] = useState("LISTING");

  // ===== USER PREFERENCE =====
  const [quantity, setQuantity] = useState(null);
  const [filterSectionId, setFilterSectionId] = useState(null);

  // ===== DATA =====
  const [sections, setSections] = useState([]);

  // ===== CHECKOUT SELECTION =====
  const [selectedRow, setSelectedRow] = useState(null);

  // ===== HOLD =====
  const [holdToken, setHoldToken] = useState(null);

  // ===== RESULT =====
  const [successData, setSuccessData] = useState(null);

  // ===== LOAD LISTINGS =====
  useEffect(() => {
    if (!quantity || !EVENT_ID) return;

    fetchEventListings({
      eventId: EVENT_ID,
      quantity,
      adjacent: true,
    }).then(res => {
      setSections(res.sections || []);
    });
  }, [quantity, EVENT_ID]);

  if (!quantity) {
    return <QuantityModal onSelect={setQuantity} />;
  }

  // ===== BUILD ROWS =====
  const rows = sections.flatMap(sec =>
    sec.priceOptions.map(p => ({
      sectionId: sec.sectionId,
      price: p.price,
      quantity,
      adjacent: true,
    }))
  );

  const filteredRows = filterSectionId
    ? rows.filter(r => r.sectionId === filterSectionId)
    : rows;

  async function handleHoldAndCheckout() {
    const res = await createHold({
      eventId: EVENT_ID,
      sectionId: selectedRow.sectionId,
      quantity: selectedRow.quantity,
      price: selectedRow.price,
    });

    if (res?.success) {
      setHoldToken(res.data.holdToken);
      setStep("CHECKOUT");
    }
  }

  async function handlePay() {
    const checkoutRes = await checkoutBooking({ holdToken });
    const bookingId = checkoutRes.data.bookingId;

    await confirmPayment({ bookingId });

    const ticketsRes = await fetchTickets({ bookingId });

    setSuccessData({
      bookingId,
      tickets: ticketsRes.data,
    });

    setStep("SUCCESS");
  }

  if (step === "SUCCESS") {
    return (
      <SuccessScreen
        bookingId={successData.bookingId}
        tickets={successData.tickets}
        row={selectedRow}
      />
    );
  }

  if (step === "CHECKOUT") {
    return (
      <CheckoutScreen
        row={selectedRow}
        holdToken={holdToken}
        onBack={async () => {
          await releaseHold({ holdToken });
          setHoldToken(null);
          setSelectedRow(null);
          setStep("LISTING");
        }}
        onPay={({ bookingId, tickets }) => {
          setSuccessData({ bookingId, tickets });
          setStep("SUCCESS");
        }}
      />
    );
  }

  return (
    <SeatGeekLayout
      left={
        selectedRow ? (
          <CheckoutFullPanel
            row={selectedRow}
            onCheckout={handleHoldAndCheckout}
            onClose={() => setSelectedRow(null)}
          />
        ) : (
          <ListingPanel
            rows={filteredRows}
            quantity={quantity}
            onChangeQuantity={q => {
              setQuantity(q);
              setSelectedRow(null);
            }}
            filterSectionId={filterSectionId}
            onSelectSection={setFilterSectionId}
            onClearFilter={() => setFilterSectionId(null)}
            onSelectRow={row => setSelectedRow(row)}
          />
        )
      }
      right={
        <StadiumSeatMap
          sections={sections}
          selectedSectionId={filterSectionId}
          onSelectSection={id => {
            setFilterSectionId(id);
            setSelectedRow(null);
          }}
        />
      }
    />
  );
}
