export const listingsBySection = [
  {
    eventId: "EVT001",
    sectionId: "A",
    requestedMinQty: 1,
    requestedMaxQty: 4,
    listings: [
      {
        listingId: "lst-A-A-1-3-3",
        rowId: "A",
        startSeat: 1,
        endSeat: 3,
        quantity: 3,
        totalPrice: 1500,
        pricePerSeat: 500,
        seatIds: ["A-A-1","A-A-2","A-A-3"],
        label: "Row A, seats 1–3"
      }
    ]
  },
  {
    eventId: "EVT001",
    sectionId: "B",
    requestedMinQty: 2,
    requestedMaxQty: 4,
    listings: [
      {
        listingId: "lst-B-C-10-13-4",
        rowId: "C",
        startSeat: 10,
        endSeat: 13,
        quantity: 4,
        totalPrice: 2000,
        pricePerSeat: 500,
        seatIds: ["B-C-10","B-C-11","B-C-12","B-C-13"],
        label: "Row C, seats 10–13"
      }
    ]
  }
];
