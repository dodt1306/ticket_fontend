import { useEffect, useState } from "react";
import { polygonToPath } from "../utils/geojsonToPath";

export default function StadiumSeatMap({
  sectionsWithTickets,
  hoverSection,
  selectedSection,
  onSelect
}) {
  const [geojson, setGeojson] = useState(null);

  useEffect(() => {
    fetch("/seatmap/stadium.geojson")
      .then(res => {
        if (!res.ok) throw new Error("Failed to load GeoJSON");
        return res.json();
      })
      .then(setGeojson)
      .catch(err => console.error("GeoJSON load error", err));
  }, []);

  if (!geojson) {
    return <div style={{ padding: 20 }}>Loading seat map…</div>;
  }

  const hasAvailability = id => sectionsWithTickets.includes(id);

  return (
    <svg viewBox="0 0 400 400" className="map stadium-map">
      {/* Field */}
      <ellipse cx="200" cy="200" rx="70" ry="100" fill="#1b5e20" />

      {geojson.features.map(feature => {
        const id = feature.properties.sectionId;
        const path = polygonToPath(feature.geometry.coordinates);
        const active = hoverSection === id || selectedSection === id;
        const available = hasAvailability(id);

        return (
          <path
            key={id}
            d={path}
            fill={
              active
                ? "#2e7d32"
                : available
                ? "#66bb6a"
                : "#ddd"
            }
            stroke="#333"
            strokeWidth="1"
            onClick={() => available && onSelect(id)}
            style={{ cursor: available ? "pointer" : "not-allowed" }}
          />
        );
      })}
    </svg>
  );
}
