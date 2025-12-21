export default function ListingPanel({
  rows,
  quantity,
  onChangeQuantity,
  filterSectionId,
  onSelectSection,
  onClearFilter,
  onSelectRow
}) {
  return (
    <div className="h-full flex flex-col">

      {/* ===== CONTROLS ===== */}
      <div className="px-4 py-3 border-b flex items-center gap-3">
        <span className="text-sm font-medium">
          Quantity
        </span>

        <select
          value={quantity}
          onChange={e => onChangeQuantity(Number(e.target.value))}
          className="border rounded px-2 py-1 text-sm"
        >
          {[1, 2, 3, 4, 5, 6].map(q => (
            <option key={q} value={q}>
              {q}
            </option>
          ))}
        </select>

        {filterSectionId && (
          <button
            onClick={onClearFilter}
            className="ml-auto text-sm text-blue-600 hover:underline"
          >
            Clear Section {filterSectionId}
          </button>
        )}
      </div>

      {/* ===== LISTINGS ===== */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {rows.map((r, i) => (
          <div
            key={`${r.sectionId}-${r.price}-${i}`}
            onClick={() => onSelectRow(r)}
            className="border rounded-lg p-3 cursor-pointer hover:border-gray-400"
          >
            <div className="flex justify-between">
              <div className="font-medium">
                Section {r.sectionId}
              </div>
              <div className="font-semibold">
                {r.price.toLocaleString()}₫
              </div>
            </div>

            <div className="text-sm text-gray-500">
              Qty {r.quantity} · {r.adjacent ? "Adjacent" : "Split"}
            </div>
          </div>
        ))}

        {rows.length === 0 && (
          <div className="text-center text-sm text-gray-500 py-10">
            No listings available
          </div>
        )}
      </div>
    </div>
  );
}
