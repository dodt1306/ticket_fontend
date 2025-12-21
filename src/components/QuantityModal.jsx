export default function QuantityModal({ onSelect }) {
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white p-6 rounded-xl w-[420px]">
        <h2 className="font-semibold mb-4">
          How many tickets?
        </h2>

        <div className="grid grid-cols-5 gap-3">
          {[1,2,3,4,5].map(q => (
            <button
              key={q}
              onClick={() => onSelect(q)}
              className="border rounded py-3 hover:bg-black hover:text-white"
            >
              {q}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
