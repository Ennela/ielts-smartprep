import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } from 'recharts';

const SKILL_COLORS = {
  READING: '#005faf',   // Reading (Emerald/Secondary)
  WRITING: '#853100',   // Writing (Burnt Orange/Tertiary)
  LISTENING: '#003178'  // Listening (Marine/Primary)
};

export default function ScoreTrendChart({ dataPoints = [], targetScore, skill = 'READING' }) {
  const color = SKILL_COLORS[skill] || '#003fb1';

  const chartData = dataPoints.map(dp => ({
    period: dp.period,
    score: parseFloat(dp.avgScore) || 0
  }));

  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload?.length) {
      return (
        <div className="chart-tooltip">
          <p className="chart-tooltip-label">{label}</p>
          <p className="chart-tooltip-value" style={{ color }}>
            Band {payload[0].value?.toFixed(1)}
          </p>
        </div>
      );
    }
    return null;
  };

  if (!chartData.length) {
    return (
      <div className="trend-empty">
        <p>No trend data available yet.</p>
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={320}>
      <LineChart data={chartData} margin={{ top: 20, right: 30, left: 10, bottom: 10 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border-subtle)" />
        <XAxis
          dataKey="period"
          stroke="var(--color-text-muted)"
          fontSize={12}
          tickLine={false}
        />
        <YAxis
          domain={[0, 9]}
          ticks={[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]}
          stroke="var(--color-text-muted)"
          fontSize={12}
          tickLine={false}
        />
        <Tooltip content={<CustomTooltip />} />

        {/* Target score reference line */}
        {targetScore && (
          <ReferenceLine
            y={parseFloat(targetScore)}
            stroke="#ba1a1a"
            strokeDasharray="8 4"
            strokeWidth={2}
            label={{
              value: `Target ${parseFloat(targetScore).toFixed(1)}`,
              position: 'right',
              fill: '#ba1a1a',
              fontSize: 12,
              fontWeight: 600
            }}
          />
        )}

        <Line
          type="monotone"
          dataKey="score"
          stroke={color}
          strokeWidth={3}
          dot={{ r: 5, fill: color, stroke: 'var(--clr-surface)', strokeWidth: 2 }}
          activeDot={{ r: 7, fill: color, stroke: '#fff', strokeWidth: 2 }}
          animationDuration={800}
          animationEasing="ease-out"
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
