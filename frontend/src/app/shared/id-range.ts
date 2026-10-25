/**
 * A range of student IDs typed in a search box: "pvt001-pvt120", "PVT1 - PVT120", "m001 to m050" or "pvt001 to 120".
 * Matches the IDs with that prefix and a number in the range, whatever follows the number ("PVT007-2025").
 */
export interface IdRange { prefix: string; from: number; to: number; }

/** The range in the text, or null when it isn't one (a plain ID like "PVT001-2025" is not a range). */
export function parseIdRange(text: string): IdRange | null {
  const t = (text ?? '').trim();
  // "pvt001-pvt120": the second ID repeats the prefix; with "to" it may be left out ("pvt001 to 120")
  const m = /^([a-z]+)\s*0*(\d+)\s*(?:-\s*([a-z]+)\s*0*(\d+)|to\s*(?:([a-z]+)\s*)?0*(\d+))$/i.exec(t);
  if (!m) return null;
  const prefix = m[1].toUpperCase();
  const other = (m[3] ?? m[5] ?? prefix).toUpperCase();
  if (other !== prefix) return null;
  const a = Number(m[2]), b = Number(m[4] ?? m[6]);
  return { prefix, from: Math.min(a, b), to: Math.max(a, b) };
}

/** Whether the student ID falls in the range ("PVT007-2025" is in PVT001-PVT120). */
export function inIdRange(userId: string | null | undefined, r: IdRange): boolean {
  const m = /^([a-z]+)\s*0*(\d+)(?:\D.*)?$/i.exec((userId ?? '').trim());
  if (!m || m[1].toUpperCase() !== r.prefix) return false;
  const n = Number(m[2]);
  return n >= r.from && n <= r.to;
}
