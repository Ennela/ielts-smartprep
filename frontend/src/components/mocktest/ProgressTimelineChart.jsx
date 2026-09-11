import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import styles from '../../styles/MockTestAnalytics.module.css';

// Colour is defined once, on the .chart wrapper in the CSS module, so light and dark mode
// swap in one place and the SVG strokes below just reference the role.
const SERIES = [
  { key: 'listening', label: 'Listening', color: 'var(--series-listening)' },
  { key: 'reading', label: 'Reading', color: 'var(--series-reading)' },
  { key: 'writing', label: 'Writing', color: 'var(--series-writing)' },
];

const fmt = (v) => (v === null || v === undefined ? '—' : Number(v).toFixed(1));

/**
 * One question: "Is each skill going up, down, or sideways across my sittings?"
 * Three lines, one per graded skill, one point per completed attempt. The overall band is
 * deliberately not a fourth line — it is the average of the other three and would only
 * add ink; it lives in the trend cards above the chart instead.
 */
export default function ProgressTimelineChart({ timeline = [] }) {
  const data = timeline.map((p) => ({
    attempt: `Attempt ${p.attemptNumber}`,
    listening: p.listeningBand === null || p.listeningBand === undefined ? null : Number(p.listeningBand),
    reading: p.readingBand === null || p.readingBand === undefined ? null : Number(p.readingBand),
    writing: p.writingBand === null || p.writingBand === undefined ? null : Number(p.writingBand),
    current: p.current,
  }));

  if (data.length < 2) {
    return (
      <p className={styles.empty}>
        {data.length === 0
          ? 'No completed sittings to plot yet.'
          : 'One completed sitting so far. The trend line appears from your second full test.'}
      </p>
    );
  }

  const lastIndex = data.length - 1;

  // Direct labels sit at the end of each line. Two skills that finish on the same band
  // would print on top of each other, so series whose final values are within half a
  // band are fanned out vertically, highest value on top.
  const endOffsets = {};
  const finals = SERIES
    .map((s) => ({ key: s.key, value: data[lastIndex][s.key] }))
    .filter((f) => f.value !== null)
    .sort((a, b) => b.value - a.value);
  let cluster = [];
  const flush = () => {
    cluster.forEach((f, i) => { endOffsets[f.key] = (i - (cluster.length - 1) / 2) * 13; });
    cluster = [];
  };
  finals.forEach((f) => {
    if (cluster.length && cluster[cluster.length - 1].value - f.value >= 0.5) flush();
    cluster.push(f);
  });
  flush();

  const endLabel = (key, label) => (props) => {
    if (props.index !== lastIndex || props.value === null || props.value === undefined) return null;
    return (
      <text x={props.x + 8} y={props.y + 4 + (endOffsets[key] || 0)} fontSize={11} fontWeight={600} fill="var(--on-surface-variant)">
        {label}
      </text>
    );
  };

  const CustomTooltip = ({ active, payload, label }) => {
    if (!active || !payload?.length) return null;
    return (
      <div className={styles['chart-tooltip']}>
        <p style={{ fontWeight: 700 }}>{label}</p>
        {payload.map((entry) => (
          <p key={entry.dataKey}>
            {SERIES.find((s) => s.key === entry.dataKey)?.label}: Band {fmt(entry.value)}
          </p>
        ))}
      </div>
    );
  };

  return (
    <div>
      <div className={styles.chart} role="img" aria-label="Band score per skill across completed mock tests">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 12, right: 64, left: -16, bottom: 4 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border-subtle)" vertical={false} />
            <XAxis dataKey="attempt" stroke="var(--color-text-muted)" fontSize={12} tickLine={false} axisLine={false} />
            <YAxis
              domain={[0, 9]}
              ticks={[0, 3, 6, 9]}
              stroke="var(--color-text-muted)"
              fontSize={12}
              tickLine={false}
              axisLine={false}
            />
            <Tooltip content={<CustomTooltip />} cursor={{ stroke: 'var(--outline-variant)' }} />
            {SERIES.map((s) => (
              <Line
                key={s.key}
                type="monotone"
                dataKey={s.key}
                stroke={s.color}
                strokeWidth={2}
                dot={{ r: 4, fill: s.color, stroke: 'var(--surface-container-lowest)', strokeWidth: 2 }}
                activeDot={{ r: 6, fill: s.color, stroke: 'var(--surface-container-lowest)', strokeWidth: 2 }}
                connectNulls
                isAnimationActive={false}
                label={endLabel(s.key, s.label)}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
      <ul className={styles.legend} aria-hidden="true">
        {SERIES.map((s) => (
          <li key={s.key}>
            <span className={styles['legend-swatch']} style={{ background: s.color }} />
            {s.label}
          </li>
        ))}
      </ul>
    </div>
  );
}
