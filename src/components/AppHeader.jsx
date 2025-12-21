import { useNavigate } from "react-router-dom";

export default function AppHeader({
  title,
  right, // JSX optional
}) {
  const navigate = useNavigate();

  return (
    <header className="h-14 bg-purple-800 text-white flex items-center px-4 relative">
      
      {/* LOGO / ICON */}
      <button
        onClick={() => navigate("/")}
        className="flex items-center gap-2 font-semibold hover:opacity-90"
        title="Về trang chủ"
      >
        <span className="text-xl">🎟</span>
        <span className="hidden sm:inline">
          Ticket
        </span>
      </button>

      {/* TITLE */}
      <div className="absolute left-1/2 -translate-x-1/2 font-semibold">
        {title}
      </div>

      {/* RIGHT SLOT */}
      <div className="ml-auto">
        {right}
      </div>
    </header>
  );
}
