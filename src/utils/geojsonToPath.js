export function polygonToPath(coords) {
  return coords.map(
    ring => "M " + ring.map(p => `${p[0]} ${p[1]}`).join(" L ") + " Z"
  ).join(" ");
}
