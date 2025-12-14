export default function QuantityModal({ onSelect }) {
  return (
    <div className="modal">
      <div className="modal-box">
        <h3>How many tickets are you looking for?</h3>
        {[1,2,3,4].map(q => (
          <button key={q} onClick={() => onSelect(q)}>{q}</button>
        ))}
      </div>
    </div>
  );
}
