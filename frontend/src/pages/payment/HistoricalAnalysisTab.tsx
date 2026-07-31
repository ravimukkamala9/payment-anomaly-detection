import React, { useEffect, useState } from 'react';
import Plot from 'react-plotly.js';
import { RefreshCw } from 'lucide-react';

const API = '/api';
const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

interface Point { [k: string]: number }
interface Response {
  execution_ms: number;
  weekly_trend: Point[]; hour_pattern: Point[]; day_pattern: Point[]; day_hour_heatmap: Point[];
}

export default function HistoricalAnalysisTab() {
  const [data, setData] = useState<Response | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API}/payment/historical-analysis`);
      if (!res.ok) return;
      setData(await res.json());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  if (!data && !loading) return <EmptyState onLoad={load} />;
  if (loading && !data) return <div style={{ color: 'var(--text-muted)', padding: 24 }}>Loading…</div>;
  if (!data) return null;

  const heatmapZ: number[][] = Array.from({ length: 7 }, () => Array(24).fill(0));
  for (const p of data.day_hour_heatmap) heatmapZ[p.day_of_week][p.hour] = p.decline_rate * 100;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Aggregated across all 5 weeks ({data.execution_ms}ms)</div>
        <button onClick={load} disabled={loading} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 10px', borderRadius: 6, fontSize: 11, fontWeight: 600, background: 'var(--surface2)', color: 'var(--text-muted)', border: '1px solid var(--border)' }}>
          <RefreshCw size={12} style={{ animation: loading ? 'spin 1s linear infinite' : 'none' }} /> Refresh
        </button>
      </div>

      <ChartCard title="Weekly Trend — Decline Rate">
        <Plot
          data={[{
            type: 'scatter', mode: 'lines+markers',
            x: data.weekly_trend.map(p => p.week === 0 ? 'Current' : `W-${p.week}`),
            y: data.weekly_trend.map(p => p.decline_rate * 100),
            line: { color: '#6366f1', width: 2 }, marker: { size: 8, color: '#6366f1' },
          }]}
          layout={baseLayout({ yTitle: 'Decline Rate %', height: 220 })}
          config={{ displayModeBar: false, responsive: true }}
          style={{ width: '100%' }}
        />
      </ChartCard>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <ChartCard title="Hour-of-Day Pattern">
          <Plot
            data={[{
              type: 'scatter', mode: 'lines', fill: 'tozeroy',
              x: data.hour_pattern.map(p => p.hour), y: data.hour_pattern.map(p => p.decline_rate * 100),
              line: { color: '#f59e0b', width: 2 }, fillcolor: 'rgba(245,158,11,0.1)',
            }]}
            layout={baseLayout({ xTitle: 'Hour', yTitle: 'Decline Rate %', height: 220 })}
            config={{ displayModeBar: false, responsive: true }}
            style={{ width: '100%' }}
          />
        </ChartCard>

        <ChartCard title="Day-of-Week Pattern">
          <Plot
            data={[{
              type: 'bar',
              x: data.day_pattern.map(p => DAY_NAMES[p.day_of_week]), y: data.day_pattern.map(p => p.decline_rate * 100),
              marker: { color: '#10b981' },
            }]}
            layout={baseLayout({ yTitle: 'Decline Rate %', height: 220 })}
            config={{ displayModeBar: false, responsive: true }}
            style={{ width: '100%' }}
          />
        </ChartCard>
      </div>

      <ChartCard title="Day × Hour Heatmap — Decline Rate %">
        <Plot
          data={[{
            type: 'heatmap',
            z: heatmapZ, x: Array.from({ length: 24 }, (_, i) => i), y: DAY_NAMES,
            colorscale: 'YlOrRd',
            colorbar: { tickfont: { color: '#94a3b8', size: 9 }, ticksuffix: '%' },
          }]}
          layout={baseLayout({ xTitle: 'Hour', height: 280 })}
          config={{ displayModeBar: false, responsive: true }}
          style={{ width: '100%' }}
        />
      </ChartCard>
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
      <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>{title}</div>
      {children}
    </div>
  );
}

function baseLayout({ xTitle, yTitle, height }: { xTitle?: string; yTitle?: string; height: number }) {
  return {
    paper_bgcolor: 'transparent', plot_bgcolor: 'transparent',
    font: { color: '#94a3b8', size: 10 },
    xaxis: { color: '#94a3b8', gridcolor: '#2e3347', title: xTitle },
    yaxis: { color: '#94a3b8', gridcolor: '#2e3347', title: yTitle },
    margin: { t: 10, r: 10, b: 40, l: 50 },
    height,
  };
}

function EmptyState({ onLoad }: { onLoad: () => void }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 300, gap: 12, color: 'var(--text-muted)' }}>
      <div style={{ fontSize: 14 }}>No data yet</div>
      <button onClick={onLoad} style={{ padding: '8px 16px', borderRadius: 7, fontWeight: 600, fontSize: 12, background: 'var(--primary)', color: 'white', border: 'none' }}>
        Load Historical Analysis
      </button>
    </div>
  );
}
