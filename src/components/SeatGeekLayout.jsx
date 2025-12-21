import AppHeader from "../components/AppHeader";
export default function SeatGeekLayout({ left, right }) {
  return (
    <div className="h-screen flex flex-col bg-gray-100">

     <AppHeader  />

      <div className="flex flex-1 overflow-hidden">

        <aside className="w-[380px] bg-white border-r flex flex-col overflow-hidden">
          {left}
        </aside>

        <main className="flex-1 relative bg-gray-50">
          {right}
        </main>

      </div>
    </div>
  );
}
