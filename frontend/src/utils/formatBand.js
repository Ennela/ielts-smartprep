/**
 * A band score as the app shows it: one decimal place ("7.0", "6.5"), a dash when there
 * is none. Bands arrive as JSON numbers, so a BigDecimal 7.0 is the number 7 by the time
 * it reaches a component; rendering it raw printed "Band 7" next to "Band 6.5".
 */
export function formatBand(value) {
  return value === null || value === undefined ? '—' : Number(value).toFixed(1);
}
