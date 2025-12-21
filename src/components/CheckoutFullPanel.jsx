export default function CheckoutFullPanel({
  row,
  onClose,
  onCheckout
}) {
  return (
    <div className="h-full flex flex-col bg-white">

      {/* Header */}
      <div className="h-14 border-b flex items-center justify-between px-4">
        <span className="font-semibold">Checkout</span>
        <button
          onClick={onClose}
          className="text-xl font-bold"
        >
          ×
        </button>
      </div>

      {/* Content */}
      <div className="flex-1 p-4 space-y-3 text-sm">
        <div>
          <div className="text-gray-500">Section</div>
          <div className="font-medium">{row.sectionId}</div>
        </div>

        <div>
          <div className="text-gray-500">Price</div>
          <div className="font-medium">
            {row.price.toLocaleString()}₫
          </div>
        </div>

        <div>
          <div className="text-gray-500">Quantity</div>
          <div className="font-medium">{row.quantity}</div>
        </div>

        <div>
          <div className="text-gray-500">Seating</div>
          <div className="font-medium">
            {row.adjacent ? "Adjacent" : "Split"}
          </div>
        </div>
      </div>

      {/* Action */}
      <div className="border-t p-4">
        <button
          onClick={onCheckout}
          className="w-full bg-black text-white py-3 rounded"
        >
          Checkout
        </button>
      </div>
    </div>
  );
}
