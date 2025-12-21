export function polygonToPath(coords) {
  return coords[0]
    .map(
      ([x, y], i) =>
        `${i === 0 ? "M" : "L"} ${x} ${y}`
    )
    .join(" ") + " Z";
}