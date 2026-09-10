// Leave room for DD-Mon-YYYY labels, including the inward-aligned endpoints.
export function spacedDateTicks(points, minimumSpacing = 220) {
  if (points.length <= 1) return points

  const first = points[0]
  const last = points[points.length - 1]
  const ticks = [first]

  for (const point of points.slice(1, -1)) {
    if (point.x - ticks[ticks.length - 1].x >= minimumSpacing &&
        last.x - point.x >= minimumSpacing) {
      ticks.push(point)
    }
  }

  ticks.push(last)
  return ticks
}
