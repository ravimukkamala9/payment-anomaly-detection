import React, { useEffect, useState } from 'react';
import { RefreshCw, AlertTriangle } from 'lucide-react';
import WoWLineChart from '../../components/charts/WoWLineChart';

const API = '/api';

interface HeadAlert {
  [dim: string]: unknown;
  decline_count: number;
  contribution_pct: number;
  z_contribution: number;
  is_new_cell: boolean;
}

interface WoWEntry {
  cell: string; short: string;
  week_rates: Record<string, number | null>;
  wow_mean: number; current_rate: number; z_wow: number; is_new_cell: boolean;
}

interface WoWAlert {
  [dim: string]: unknown;
  decline_rate: number;
  wow_mean: number;
  z_wow: number;
  n_weeks: number;
}

interface WoWResult {
  n_active_cells: number; n_alerts: number; execution_ms: number;
  wow_chart_data: WoWEntry[]; alerts: WoWAlert[];
}

interface Head {
  name: string; owner: string; dims: string[];
  n_active_cells: number; n_alerts: number; execution_ms: number;
  alerts: HeadAlert[];
  wow: WoWResult;
}

interface Response { heads: Record<string, Head>; }

const HEAD_COLORS: Record<string, string> = {
  network_channel: '#6366f1',
  payment_method: '#f59e0b',
  acquiring_risk: '#ec4899',
};

export default function MonitoringHeadsTab() {
  const [data, setData] = useState<Response | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API}/payment/stage2-heads?threshold=3.0`, { method: 'POST' });
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

  const heads = Object.entries(data.heads);
  const totalCellsAcrossHeads = heads.reduce((s, [, h]) => s + h.n_active_cells, 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          {heads.length} heads, {totalCellsAcrossHeads} cells combined — vs. one 7-dim monitor at 63 cells. Each head runs both jobs: contribution shift (share vs. the rest of the window) and week-over-week (a cell's own rate vs. its own history).
        </div>
        <button onClick={load} disabled={loading} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 10px', borderRadius: 6, fontSize: 11, fontWeight: 600, background: 'var(--surface2)', color: 'var(--text-muted)', border: '1px solid var(--border)', flexShrink: 0 }}>
          <RefreshCw size={12} style={{ animation: loading ? 'spin 1s linear infinite' : 'none' }} /> Refresh
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        {heads.map(([key, head]) => (
          <HeadCard key={key} head={head} color={HEAD_COLORS[key] ?? '#6366f1'} />
        ))}
      </div>
    </div>
  );
}

function HeadCard({ head, color }: { head: Head; color: string }) {
  const extremeZ = head.alerts.some(a => Math.abs(a.z_contribution) > 1000)
    || head.wow.alerts.some(a => Math.abs(a.z_wow) > 1000);

  return (
    <div style={{ background: 'var(--surface2)', border: `1px solid ${color}44`, borderRadius: 10, overflow: 'hidden' }}>
      <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)' }}>
        <div style={{ fontSize: 14, fontWeight: 700, color }}>{head.name}</div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>Owner: {head.owner}</div>
        <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 4, fontFamily: 'monospace' }}>
          {head.dims.join(' × ')}
        </div>
        <div style={{ display: 'flex', gap: 24, marginTop: 8 }}>
          <StatGroup label="Contribution Shift" cells={head.n_active_cells} alerts={head.n_alerts} ms={head.execution_ms} />
          <StatGroup label="Week-over-Week" cells={head.wow.n_active_cells} alerts={head.wow.n_alerts} ms={head.wow.execution_ms} />
        </div>
      </div>

      {extremeZ && (
        <div style={{ padding: '8px 14px', background: 'rgba(245,158,11,0.08)', borderBottom: '1px solid var(--border)', display: 'flex', gap: 6, alignItems: 'flex-start' }}>
          <AlertTriangle size={12} color="#f59e0b" style={{ marginTop: 1, flexShrink: 0 }} />
          <span style={{ fontSize: 10.5, color: 'var(--text-muted)', lineHeight: 1.4 }}>
            Some z-scores here are extreme — sparse cells at this granularity have near-zero historical variance, so the std floor dominates. A real deployment would need shrinkage toward a parent rate before alerting on this head.
          </span>
        </div>
      )}

      <div>
        <div style={{ padding: '8px 14px 4px', fontSize: 10, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          Contribution Shift — share vs. rest of window
        </div>
        <ContributionTable head={head} />
      </div>
      <div style={{ borderTop: '1px solid var(--border)' }}>
        <div style={{ padding: '8px 14px 4px', fontSize: 10, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          Week-over-Week — own rate vs. own history
        </div>
        <WoWSection head={head} color={color} />
      </div>
    </div>
  );
}

function ContributionTable({ head }: { head: Head }) {
  return (
    <div style={{ maxHeight: 260, overflow: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 10.5 }}>
        <thead>
          <tr>
            {head.dims.map(d => (
              <th key={d} style={{ padding: '6px 8px', textAlign: 'left', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', fontSize: 9, position: 'sticky', top: 0, background: 'var(--surface2)' }}>
                {d.replace(/_/g, ' ')}
              </th>
            ))}
            <th style={{ padding: '6px 8px', textAlign: 'left', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', fontSize: 9, position: 'sticky', top: 0, background: 'var(--surface2)' }}>Share</th>
            <th style={{ padding: '6px 8px', textAlign: 'left', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', fontSize: 9, position: 'sticky', top: 0, background: 'var(--surface2)' }}>Z</th>
          </tr>
        </thead>
        <tbody>
          {head.alerts.map((a, i) => {
            const zAbs = Math.abs(a.z_contribution);
            return (
              <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
                {head.dims.map(d => (
                  <td key={d} style={{ padding: '5px 8px', borderBottom: '1px solid var(--border)', whiteSpace: 'nowrap' }}>{String(a[d])}</td>
                ))}
                <td style={{ padding: '5px 8px', borderBottom: '1px solid var(--border)' }}>{(a.contribution_pct * 100).toFixed(2)}%</td>
                <td style={{ padding: '5px 8px', borderBottom: '1px solid var(--border)', fontFamily: 'monospace', fontWeight: 700, color: zAbs > 10 ? '#ef4444' : '#f59e0b' }}>
                  {zAbs > 1000 ? a.z_contribution.toExponential(1) : a.z_contribution.toFixed(1)}
                </td>
              </tr>
            );
          })}
          {head.alerts.length === 0 && (
            <tr><td colSpan={head.dims.length + 2} style={{ padding: '10px 8px', color: 'var(--text-muted)' }}>No alerts</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function WoWSection({ head, color }: { head: Head; color: string }) {
  const wow = head.wow;
  return (
    <div>
      {wow.wow_chart_data.length > 0 ? (
        <div style={{ padding: '0 8px' }}>
          <WoWLineChart data={wow.wow_chart_data} height={200} />
        </div>
      ) : (
        <div style={{ padding: '10px 14px', fontSize: 11, color: 'var(--text-muted)' }}>No alerted cells to chart.</div>
      )}
      <div style={{ maxHeight: 200, overflow: 'auto', borderTop: '1px solid var(--border)' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 10.5 }}>
          <thead>
            <tr>
              {head.dims.map(d => (
                <th key={d} style={{ padding: '6px 8px', textAlign: 'left', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', fontSize: 9, position: 'sticky', top: 0, background: 'var(--surface2)' }}>
                  {d.replace(/_/g, ' ')}
                </th>
              ))}
              <th style={{ padding: '6px 8px', textAlign: 'left', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', fontSize: 9, position: 'sticky', top: 0, background: 'var(--surface2)' }}>Rate</th>
              <th style={{ padding: '6px 8px', textAlign: 'left', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', fontSize: 9, position: 'sticky', top: 0, background: 'var(--surface2)' }}>Z</th>
            </tr>
          </thead>
          <tbody>
            {wow.alerts.map((a, i) => {
              const zAbs = Math.abs(a.z_wow);
              return (
                <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
                  {head.dims.map(d => (
                    <td key={d} style={{ padding: '5px 8px', borderBottom: '1px solid var(--border)', whiteSpace: 'nowrap' }}>{String(a[d])}</td>
                  ))}
                  <td style={{ padding: '5px 8px', borderBottom: '1px solid var(--border)' }}>{(a.decline_rate * 100).toFixed(2)}%</td>
                  <td style={{ padding: '5px 8px', borderBottom: '1px solid var(--border)', fontFamily: 'monospace', fontWeight: 700, color: zAbs > 10 ? '#ef4444' : '#f59e0b' }}>
                    {zAbs > 1000 ? a.z_wow.toExponential(1) : a.z_wow.toFixed(1)}
                  </td>
                </tr>
              );
            })}
            {wow.alerts.length === 0 && (
              <tr><td colSpan={head.dims.length + 2} style={{ padding: '10px 8px', color: 'var(--text-muted)' }}>No alerts</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StatGroup({ label, cells, alerts, ms }: { label: string; cells: number; alerts: number; ms: number }) {
  return (
    <div>
      <div style={{ fontSize: 9, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 3 }}>{label}</div>
      <div style={{ display: 'flex', gap: 12 }}>
        <MiniStat label="Cells" value={cells} />
        <MiniStat label="Alerts" value={alerts} color={alerts > 0 ? '#ef4444' : undefined} />
        <MiniStat label="Time" value={`${ms}ms`} />
      </div>
    </div>
  );
}

function MiniStat({ label, value, color }: { label: string; value: string | number; color?: string }) {
  return (
    <div>
      <div style={{ fontSize: 15, fontWeight: 700, color: color || 'var(--text)' }}>{value}</div>
      <div style={{ fontSize: 9, color: 'var(--text-muted)', textTransform: 'uppercase' }}>{label}</div>
    </div>
  );
}

function EmptyState({ onLoad }: { onLoad: () => void }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 300, gap: 12, color: 'var(--text-muted)' }}>
      <div style={{ fontSize: 14 }}>No data yet</div>
      <button onClick={onLoad} style={{ padding: '8px 16px', borderRadius: 7, fontWeight: 600, fontSize: 12, background: 'var(--primary)', color: 'white', border: 'none' }}>
        Load Monitoring Heads
      </button>
    </div>
  );
}
