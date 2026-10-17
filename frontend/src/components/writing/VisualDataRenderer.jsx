import {
    LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
    XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';

const COLORS = ['#003fb1', '#006c4a', '#842c00', '#ba1a1a', '#6750a4', '#4a6572'];

/**
 * A map feature or process step as text. Imported prompts are checked to hold strings, but a
 * prompt Gemini writes is not, and an object here made React throw and the chart vanish.
 */
const asText = (item) => (item !== null && typeof item === 'object' ? Object.values(item).join(' – ') : String(item));

/** A slice's percentage, drawn inside the slice; slivers under 4% are left to the legend. */
function renderPieLabel({ cx, cy, midAngle, outerRadius, percent }) {
    if (percent < 0.04) return null;
    const radius = outerRadius * 0.62;
    const angle = (-midAngle * Math.PI) / 180;
    return (
        <text x={cx + radius * Math.cos(angle)} y={cy + radius * Math.sin(angle)} fill="#fff"
              textAnchor="middle" dominantBaseline="central" fontSize={12} fontWeight={600}>
            {`${Math.round(percent * 100)}%`}
        </text>
    );
}

export default function VisualDataRenderer({ visualDataJson, essayType }) {
    if (!visualDataJson) return null;

    let data = null;
    try {
        data = typeof visualDataJson === 'string' ? JSON.parse(visualDataJson) : visualDataJson;
    } catch (e) {
        console.error("Failed to parse visualData JSON", e);
        return <div style={{ color: 'var(--error)' }}>Invalid visual chart data.</div>;
    }

    if (!data) return null;

    // The prompt column is narrow. Recharts drops x-axis labels that would overlap, a rotated
    // y-axis title is clipped at the edge, and pie labels drawn outside the pie are cut off,
    // so every label a candidate needs is kept inside the drawing area instead.
    const xAxisKey = data.xAxisKey || 'name';
    const rows = Array.isArray(data.data) ? data.data : [];
    const crowdedX = rows.length > 5 || rows.some(r => String(r?.[xAxisKey] ?? '').length > 8);
    const xAxisProps = {
        dataKey: xAxisKey,
        stroke: 'var(--outline)',
        interval: 0,
        tick: { fontSize: 12 },
        ...(crowdedX ? { angle: -30, textAnchor: 'end', height: 64 } : {}),
    };
    const yAxisProps = {
        stroke: 'var(--outline)',
        width: 56,
        tick: { fontSize: 12 },
        tickFormatter: (v) => (typeof v === 'number' ? v.toLocaleString('en-US') : v),
    };
    const axisCaption = (data.yAxisLabel || data.xAxisLabel) && (
        <p style={{ margin: '0 0 8px 0', fontSize: '0.8rem', color: 'var(--on-surface-variant)' }}>
            {data.yAxisLabel && <span>Vertical axis: {data.yAxisLabel}</span>}
            {data.yAxisLabel && data.xAxisLabel && <span> · </span>}
            {data.xAxisLabel && <span>Horizontal axis: {data.xAxisLabel}</span>}
        </p>
    );

    const renderChart = () => {
        const keys = data.keys || [];

        switch (essayType) {
            case 'LINE_GRAPH':
                return (
                    <ResponsiveContainer width="100%" height={320}>
                        <LineChart data={data.data} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="var(--outline-variant)" />
                            <XAxis {...xAxisProps} />
                            <YAxis {...yAxisProps} />
                            <Tooltip contentStyle={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }} />
                            <Legend />
                            {keys.map((key, index) => (
                                <Line
                                    key={key}
                                    type="monotone"
                                    dataKey={key}
                                    stroke={COLORS[index % COLORS.length]}
                                    strokeWidth={3}
                                    dot={{ r: 4 }}
                                />
                            ))}
                        </LineChart>
                    </ResponsiveContainer>
                );

            case 'BAR_CHART':
                return (
                    <ResponsiveContainer width="100%" height={320}>
                        <BarChart data={data.data} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="var(--outline-variant)" />
                            <XAxis {...xAxisProps} />
                            <YAxis {...yAxisProps} />
                            <Tooltip contentStyle={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }} />
                            <Legend />
                            {keys.map((key, index) => (
                                <Bar key={key} dataKey={key} fill={COLORS[index % COLORS.length]} radius={[4, 4, 0, 0]} />
                            ))}
                        </BarChart>
                    </ResponsiveContainer>
                );

            case 'PIE_CHART':
                return (
                    <ResponsiveContainer width="100%" height={340}>
                        <PieChart>
                            <Pie
                                data={data.data}
                                cx="50%"
                                cy="45%"
                                labelLine={false}
                                // The share inside each slice; the names are in the legend, where
                                // they are not cut off at the edge of the column.
                                label={renderPieLabel}
                                outerRadius="70%"
                                fill="#8884d8"
                                dataKey={keys[0] || 'value'}
                                isAnimationActive={false}
                            >
                                {data.data?.map((entry, index) => (
                                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                ))}
                            </Pie>
                            <Tooltip contentStyle={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }} />
                            <Legend />
                        </PieChart>
                    </ResponsiveContainer>
                );

            case 'TABLE': {
                const tableData = data.data || [];
                if (tableData.length === 0) return null;
                const headers = Object.keys(tableData[0]);
                return (
                    <div style={{ overflowX: 'auto', marginTop: 12, borderRadius: 'var(--radius-md)', border: '1px solid var(--outline-variant)' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                            <thead>
                                <tr style={{ background: 'var(--surface-container-high)', borderBottom: '1px solid var(--outline-variant)' }}>
                                    {headers.map(h => (
                                        <th key={h} style={{ padding: '10px 14px', textAlign: 'left', fontWeight: 700 }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {tableData.map((row, idx) => (
                                    <tr key={idx} style={{ borderBottom: '1px solid var(--outline-variant)', background: idx % 2 === 0 ? 'var(--surface-container-lowest)' : 'var(--surface-container-low)' }}>
                                        {headers.map(h => (
                                            <td key={h} style={{ padding: '10px 14px' }}>{row[h]}</td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                );
            }

            case 'MAP':
                return (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 12 }}>
                        <div style={{ padding: 14, background: 'var(--surface-container-low)', borderRadius: 'var(--radius-md)', border: '1px solid var(--outline-variant)' }}>
                            <div style={{ fontWeight: 700, marginBottom: 8, color: 'var(--primary)', display: 'flex', alignItems: 'center', gap: 6 }}>
                                <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 18 }}>map</span>
                                Map 1 Details
                            </div>
                            <ul style={{ margin: 0, paddingLeft: 20, fontSize: '0.9rem', lineHeight: 1.6 }}>
                                {data.map1?.map((item, idx) => (
                                    <li key={idx}>{asText(item)}</li>
                                ))}
                            </ul>
                        </div>
                        <div style={{ padding: 14, background: 'var(--surface-container-low)', borderRadius: 'var(--radius-md)', border: '1px solid var(--outline-variant)' }}>
                            <div style={{ fontWeight: 700, marginBottom: 8, color: 'var(--secondary)', display: 'flex', alignItems: 'center', gap: 6 }}>
                                <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 18 }}>map</span>
                                Map 2 Details (Modifications)
                            </div>
                            <ul style={{ margin: 0, paddingLeft: 20, fontSize: '0.9rem', lineHeight: 1.6 }}>
                                {data.map2?.map((item, idx) => (
                                    <li key={idx}>{asText(item)}</li>
                                ))}
                            </ul>
                        </div>
                    </div>
                );

            case 'DIAGRAM':
                return (
                    <div style={{ marginTop: 12 }}>
                        <div style={{ fontWeight: 700, marginBottom: 12, color: 'var(--primary)', display: 'flex', alignItems: 'center', gap: 6 }}>
                            <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 18 }}>account_tree</span>
                            Process Steps
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                            {data.steps?.map((step, idx) => (
                                <div key={idx} style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                                    <div style={{
                                        width: 24, height: 24, borderRadius: '50%', background: 'var(--primary)',
                                        color: 'var(--on-primary)', display: 'flex', alignItems: 'center',
                                        justifyContent: 'center', fontWeight: 700, fontSize: '0.8rem', flexShrink: 0
                                    }}>
                                        {idx + 1}
                                    </div>
                                    <div style={{ padding: 12, background: 'var(--surface-container-low)', borderRadius: 'var(--radius-md)', border: '1px solid var(--outline-variant)', flex: 1, fontSize: '0.9rem' }}>
                                        {asText(step)}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                );

            default:
                return null;
        }
    };

    return (
        <div className="visual-data-renderer" style={{ marginTop: 16 }}>
            <h4 style={{ margin: '0 0 12px 0', fontSize: '1rem', fontWeight: 700 }}>{data.title}</h4>
            {(essayType === 'LINE_GRAPH' || essayType === 'BAR_CHART') && axisCaption}
            {renderChart()}
        </div>
    );
}
