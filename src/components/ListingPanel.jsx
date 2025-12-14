export default function ListingPanel({ sections, onHover }) {
  return (
    <div className="list">
      {sections.map(sec => (
        <div
          key={sec.sectionId}
          className="section-group"
          onMouseEnter={() => onHover(sec.sectionId)}
          onMouseLeave={() => onHover(null)}
        >
          <h4>Section {sec.sectionId}</h4>
          {sec.listings.map(lst => (
            <div key={lst.listingId} className="listing">
              <div>{lst.label}</div>
              <div>€{lst.pricePerSeat} × {lst.quantity}</div>
              <small>Total €{lst.totalPrice}</small>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
