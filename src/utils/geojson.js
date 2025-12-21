// Convert polygon coordinates to SVG path
export function polygonToPath(coords) {
  var points = coords[0];
  var d = "";

  for (var i = 0; i < points.length; i++) {
    var x = points[i][0];
    var y = points[i][1];

    if (i === 0) {
      d += "M " + x + " " + y + " ";
    } else {
      d += "L " + x + " " + y + " ";
    }
  }

  d += "Z";
  return d;
}

// Calculate center of polygon (for price bubble)
export function getPolygonCenter(coords) {
  var points = coords[0];
  var x = 0;
  var y = 0;

  for (var i = 0; i < points.length; i++) {
    x += points[i][0];
    y += points[i][1];
  }

  return {
    x: x / points.length,
    y: y / points.length
  };
}

export function lineToPath(coords) {
  let d = "";
  coords.forEach(([x, y], i) => {
    d += (i === 0 ? "M " : "L ") + x + " " + y + " ";
  });
  return d.trim();
}