import { lineToPath } from "../utils/geojson";

export default function FieldBackground({ fieldFeature }) {
  if (!fieldFeature) return null;

  const coords = fieldFeature.geometry.coordinates;
  const d = lineToPath(coords);

  // =====================
  // CONFIG
  // =====================
  const outerLineWidth = 2;
  const innerLineWidth = 2;
  const innerPadding = 25; // khoảng từ line ngoài → line trong

  // =====================
  // BOUNDING BOX (OUTER FIELD)
  // =====================
  const xs = coords.map(p => p[0]);
  const ys = coords.map(p => p[1]);

  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);

  const outerW = maxX - minX;
  const outerH = maxY - minY;

  // =====================
  // INNER FIELD (CỎ SỌC)
  // =====================
  const innerX = minX + innerPadding;
  const innerY = minY + innerPadding;
  const innerW = outerW - innerPadding * 2;
  const innerH = outerH - innerPadding * 2;

  const cx = innerX + innerW / 2;
  const cy = innerY + innerH / 2;

  // =====================
  // FIFA RATIOS (RELATIVE)
  // =====================
  const centerCircleR = innerW * 0.06;
  const penaltyW = innerW * 0.18;
  const penaltyH = innerH * 0.44;
  const goalW = innerW * 0.06;
  const goalH = innerH * 0.22;
  const penaltySpotOffset = innerW * 0.12;

  return (
    <g pointerEvents="none">
      <defs>
        {/* Grass stripes */}
        <pattern
          id="grass"
          width="80"
          height={innerH}
          patternUnits="userSpaceOnUse"
        >
          <rect width="40" height={innerH} fill="#2e7d32" />
          <rect x="40" width="40" height={innerH} fill="#388e3c" />
        </pattern>

        {/* Clip inner grass */}
        <clipPath id="innerGrassClip">
          <rect
            x={innerX}
            y={innerY}
            width={innerW}
            height={innerH}
            
          />
        </clipPath>
      </defs>

      {/* ===================== */}
      {/* 1️⃣ OUTER GRASS */}
      {/* ===================== */}
      <path d={d} fill="#3fa35c" />

      {/* ===================== */}
      {/* 2️⃣ INNER GRASS (STRIPED) */}
      {/* ===================== */}
      <rect
        x={innerX}
        y={innerY}
        width={innerW}
        height={innerH}
        fill="url(#grass)"
        clipPath="url(#innerGrassClip)"
      />

      {/* ===================== */}
      {/* 3️⃣ INNER WHITE LINE (BỌC CỎ SỌC) */}
      {/* ===================== */}
      <rect
        x={innerX}
        y={innerY}
        width={innerW}
        height={innerH}
        
        fill="none"
        stroke="white"
        strokeWidth={innerLineWidth}
      />

      {/* ===================== */}
      {/* 4️⃣ OUTER TOUCHLINE (GEOJSON) */}
      {/* ===================== */}
      <path
        d={d}
        fill="none"
        stroke="white"
        strokeWidth={outerLineWidth}
      />

      {/* ===================== */}
      {/* 5️⃣ FIELD MARKINGS */}
      {/* ===================== */}

      {/* Center line */}
      <line
        x1={cx}
        y1={innerY}
        x2={cx}
        y2={innerY + innerH}
        stroke="white"
        strokeWidth={2}
      />

      {/* Center circle */}
      <circle
        cx={cx}
        cy={cy}
        r={centerCircleR}
        fill="none"
        stroke="white"
        strokeWidth={2}
      />
      <circle cx={cx} cy={cy} r={4} fill="white" />

      {/* Left penalty area */}
      <rect
        x={innerX}
        y={cy - penaltyH / 2}
        width={penaltyW}
        height={penaltyH}
        fill="none"
        stroke="white"
        strokeWidth={2}
      />

      {/* Right penalty area */}
      <rect
        x={innerX + innerW - penaltyW}
        y={cy - penaltyH / 2}
        width={penaltyW}
        height={penaltyH}
        fill="none"
        stroke="white"
        strokeWidth={2}
      />

      {/* Left goal area */}
      <rect
        x={innerX}
        y={cy - goalH / 2}
        width={goalW}
        height={goalH}
        fill="none"
        stroke="white"
        strokeWidth={2}
      />

      {/* Right goal area */}
      <rect
        x={innerX + innerW - goalW}
        y={cy - goalH / 2}
        width={goalW}
        height={goalH}
        fill="none"
        stroke="white"
        strokeWidth={2}
      />

      {/* Penalty spots */}
      <circle
        cx={innerX + penaltySpotOffset}
        cy={cy}
        r={4}
        fill="white"
      />
      <circle
        cx={innerX + innerW - penaltySpotOffset}
        cy={cy}
        r={4}
        fill="white"
      />
    </g>
  );
}
