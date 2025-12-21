export default function ListingPanel({
  rows,
  selectedSectionId,
  onClearFilter,
  onSelectRow
}) {
  return (
    <div className="h-full flex flex-col">

      {selectedSectionId && (
        <div className="flex items-center justify-between px-4 py-2 bg-gray-100 border-b">
          <span className="text-sm">
            Filter: Section {selectedSectionId}
          </span>
          <button
            onClick={onClearFilter}
            className="text-xl font-bold"
          >
            ×
          </button>
        </div>
      )}

      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {rows.map((r, i) => (
          <div
            key={i}
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
              Qty {r.quantity} ·{" "}
              {r.adjacent ? "Adjacent" : "Split"}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
