import { useEffect, useState } from "react";
import {
  polygonToPath,
  lineToPath
} from "../utils/geojson";
import FieldBackground from "./FieldBackground";

const VIEW_W = 1188;
const VIEW_H = 1062;

// 👉 padding muốn lùi vào (px theo hệ tọa độ SVG)
const PADDING = 40;

// 👉 scale để nội dung không chạm viền
const SCALE = (VIEW_W - PADDING * 2) / VIEW_W;

export default function StadiumSeatMap({
  sections,
  selectedSectionId,
  onSelectSection
}) {
  const [geojson, setGeojson] = useState(null);

  useEffect(() => {
    fetch("/seatmap/stadium.geojson")
      .then(r => r.json())
      .then(setGeojson);
  }, []);

  if (!geojson) return null;

  const priceBySection = Object.fromEntries(
    sections.map(s => [
      s.sectionId,
      Math.min(...s.priceOptions.map(p => p.price))
    ])
  );

  const fieldFeature = geojson.features.find(
    f => f.properties.role === "FIELD"
  );

  return (
    <div className="absolute inset-0 flex items-center justify-center">
      <svg
        viewBox="0 0 1188 1062"
        className="w-full h-full"
        preserveAspectRatio="xMidYMid meet"
      >
        {/* ===================== */}
        {/* GLOBAL PADDING LAYER */}
        {/* ===================== */}
        <g
          transform={`
            translate(${PADDING}, ${PADDING})
            scale(${SCALE})
          `}
        >
          {/* ===================== */}
          {/* 0️⃣ OUTLINE / BACKGROUND */}
          {/* ===================== */}
          {geojson.features
            .filter(
              f =>
                f.geometry.type === "Polygon" &&
                f.properties.group === "OUTLINE"
            )
            .map((f, idx) => {
              const style = f.properties.style || {};
              const d = polygonToPath(f.geometry.coordinates);

              return (
                <g key={`outline-${idx}`}>
                  <path
                    d={d}
                    fill={style.fill ?? "#ffffff"}
                    stroke="none"
                    pointerEvents="none"
                  />
                  <path
                    d={d}
                    fill="none"
                    stroke={style.stroke ?? "#111827"}
                    strokeWidth={style.strokeWidth ?? 1}
                    strokeLinejoin="round"
                    pointerEvents="none"
                  />
                </g>
              );
            })}

          {/* ===================== */}
          {/* FIELD */}
          {/* ===================== */}
          <FieldBackground fieldFeature={fieldFeature} />

          {/* ===================== */}
          {/* 1️⃣ SECTIONS */}
          {/* ===================== */}
          {geojson.features
            .filter(
              f =>
                f.geometry.type === "Polygon" &&
                f.properties.group !== "OUTLINE"
            )
            .map((f, idx) => {
              const id = f.properties.sectionId;
              const order = f.properties.order;
              const labelPos = f.properties.labelPos;
              const path = polygonToPath(f.geometry.coordinates);

              return (
                <g key={id ?? `polygon-${idx}`}>
                  <path
                    d={path}
                    fill={
                      selectedSectionId === id
                        ? "#94c89b"
                        : "#F5F5F5"
                    }
                    stroke="none"
                    onClick={() => onSelectSection(id)}
                    className="cursor-pointer"
                  />

                  <path
                    d={path}
                    fill="none"
                    stroke="#333"
                    strokeWidth={1.5}
                    pointerEvents="none"
                  />

                  {order != null && labelPos && (
                    <text
                      x={labelPos[0]}
                      y={labelPos[1]}
                      textAnchor="middle"
                      dominantBaseline="middle"
                      fontSize={9}
                      fontWeight={500}
                      fill="#9ca3af"
                      pointerEvents="none"
                    >
                      {order}
                    </text>
                  )}
                </g>
              );
            })}

          {/* ===================== */}
          {/* 2️⃣ LINE OUTLINE */}
          {/* ===================== */}
          {geojson.features
            .filter(f => f.geometry.type === "LineString")
            .map((f, idx) => (
              <path
                key={`line-${idx}`}
                d={lineToPath(f.geometry.coordinates)}
                fill="none"
                stroke="#111827"
                strokeWidth={1}
                pointerEvents="none"
              />
            ))}
        </g>
      </svg>
    </div>
  );
}
