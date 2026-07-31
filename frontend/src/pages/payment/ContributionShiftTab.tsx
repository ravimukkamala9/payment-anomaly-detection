import React, { useEffect, useState } from 'react';
import Plot from 'react-plotly.js';
import { RefreshCw } from 'lucide-react';

const API = '/api';

interface Cell {
  network: string; geography: string; decline_code: string; cell_short: string;
  decline_count: number; contribution_pct: number;
  last_week_contribution_pct: number | null; delta_pct: number | null;
  is_new_vs_last_week: boolean;
}

interface Response {
  n_cells: number; total_declines_current: number; total_declines_last_week: number;
  execution_ms: number; cells: Cell[];
}

export default function ContributionShiftTab() {
  const [data, setData] = useState<Response | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API}/payment/contribution-vs-last-week`);
      if (!res.ok) return;
      setData(await res.json());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  if (!data && !loading) {
    return <EmptyState onLoad={load} />;
  }
  if (loading && !data) {
    return <div style={{ color: 'var(--text-muted)', padding: 24 }}>Loading…</div>;
  }
  if (!data) return null;

  // Plotly draws horizontal bars bottom-up, so reverse the descending sort
  // to put the largest contributor at the top of the chart.
  const sorted = [...data.cells].sort((a, b) => a.contribution_pct - b.contribution_pct);
  const tableSorted = [...sorted].reverse();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          All {data.n_cells} active cells — {data.total_declines_current} declines this window vs {data.total_declines_last_week} last week ({data.execution_ms}ms)
        </div>
        <button onClick={load} disabled={loading} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 10px', borderRadius: 6, fontSize: 11, fontWeight: 600, background: 'var(--surface2)', color: 'var(--text-muted)', border: '1px solid var(--border)' }}>
          <RefreshCw size={12} style={{ animation: loading ? 'spin 1s linear infinite' : 'none' }} /> Refresh
        </button>
      </div>

      <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
        <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>Contribution % — Current vs Last Week (all {sorted.length} cells)</div>
        <Plot
          data={[
            { type: 'bar', orientation: 'h', name: 'Last week (W-1)', y: sorted.map(c => c.cell_short), x: sorted.map(c => c.last_week_contribution_pct ?? 0), marker: { color: '#3b82f655' } },
            { type: 'bar', orientation: 'h', name: 'Current', y: sorted.map(c => c.cell_short), x: sorted.map(c => c.contribution_pct), marker: { color: sorted.map(c => (c.delta_pct ?? 0) > 1 ? '#ef4444' : '#6366f1') } },
          ]}
          layout={{
            barmode: 'group',
            paper_bgcolor: 'transparent', plot_bgcolor: 'transparent',
            font: { color: '#94a3b8', size: 9 },
            xaxis: { color: '#94a3b8', gridcolor: '#2e3347', title: 'Contribution %', ticksuffix: '%' },
            yaxis: { color: '#94a3b8', gridcolor: '#2e3347', automargin: true },
            legend: { font: { color: '#94a3b8', size: 10 }, bgcolor: 'transparent', orientation: 'h', y: -0.02 },
            margin: { t: 10, r: 20, b: 40, l: 160 },
            height: Math.max(500, sorted.length * 16),
          }}
          config={{ displayModeBar: false, responsive: true }}
          style={{ width: '100%' }}
        />
      </div>

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
          <thead>
            <tr>
              {['Cell', 'Declines', 'Current %', 'Last Week %', 'Δ pp'].map(h => (
                <th key={h} style={{ padding: '7px 10px', textAlign: 'left', borderBottom: '2px solid var(--primary)44', color: 'var(--primary-light)', fontWeight: 700, whiteSpace: 'nowrap', textTransform: 'uppercase', fontSize: 10 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {tableSorted.map((c, i) => {
              const delta = c.delta_pct ?? 0;
              const strong = Math.abs(delta) > 3;
              return (
                <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
                  <td style={{ padding: '7px 10px', borderBottom: '1px solid var(--border)', whiteSpace: 'nowrap' }}>{c.cell_short}</td>
                  <td style={{ padding: '7px 10px', borderBottom: '1px solid var(--border)' }}>{c.decline_count}</td>
                  <td style={{ padding: '7px 10px', borderBottom: '1px solid var(--border)' }}>{c.contribution_pct.toFixed(2)}%</td>
                  <td style={{ padding: '7px 10px', borderBottom: '1px solid var(--border)' }}>{c.is_new_vs_last_week ? '— new —' : `${c.last_week_contribution_pct?.toFixed(2)}%`}</td>
                  <td style={{ padding: '7px 10px', borderBottom: '1px solid var(--border)', fontFamily: 'monospace', fontWeight: strong ? 700 : 400, color: strong ? (delta > 0 ? '#ef4444' : '#3b82f6') : 'var(--text)' }}>
                    {c.is_new_vs_last_week ? '—' : `${delta > 0 ? '+' : ''}${delta.toFixed(2)}`}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function EmptyState({ onLoad }: { onLoad: () => void }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 300, gap: 12, color: 'var(--text-muted)' }}>
      <div style={{ fontSize: 14 }}>No data yet</div>
      <button onClick={onLoad} style={{ padding: '8px 16px', borderRadius: 7, fontWeight: 600, fontSize: 12, background: 'var(--primary)', color: 'white', border: 'none' }}>
        Load Contribution Shift
      </button>
    </div>
  );
}
