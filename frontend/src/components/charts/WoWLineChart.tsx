import React from 'react';
import Plot from 'react-plotly.js';

interface WoWEntry {
  cell: string;
  short: string;
  week_rates: Record<string, number | null>;
  current_rate: number;
  wow_mean: number;
  z_wow: number;
  is_new_cell: boolean;
}

interface Props {
  data: WoWEntry[];
  height?: number;
}

const PALETTE = ['#6366f1', '#f59e0b', '#10b981', '#ec4899', '#3b82f6', '#8b5cf6', '#ef4444'];
const WEEKS = ['W-4', 'W-3', 'W-2', 'W-1', 'Current'];

export default function WoWLineChart({ data, height = 340 }: Props) {
  const traces: Plotly.Data[] = data.slice(0, 6).map((entry, i) => ({
    x: WEEKS,
    y: WEEKS.map(w => entry.week_rates[w] ?? null),
    type: 'scatter',
    mode: 'lines+markers',
    name: entry.short,
    line: {
      color: PALETTE[i % PALETTE.length],
      width: entry.short.includes('Current') ? 3 : 2,
    },
    marker: {
      color: WEEKS.map(w => w === 'Current' ? '#ef4444' : PALETTE[i % PALETTE.length]),
      size: WEEKS.map(w => w === 'Current' ? 10 : 6),
    },
    connectgaps: false,
  }));

  return (
    <Plot
      data={traces}
      layout={{
        paper_bgcolor: 'transparent',
        plot_bgcolor: 'transparent',
        font: { color: '#94a3b8', family: 'Inter, system-ui, sans-serif', size: 11 },
        xaxis: { gridcolor: '#2e3347', color: '#94a3b8', title: 'Week' },
        yaxis: { gridcolor: '#2e3347', zerolinecolor: '#2e3347', color: '#94a3b8', title: 'Decline Rate', tickformat: '.1%' },
        legend: { font: { color: '#94a3b8', size: 10 }, bgcolor: 'transparent', orientation: 'h', y: -0.25 },
        margin: { t: 10, r: 20, b: 80, l: 60 },
        height,
        shapes: [{
          type: 'line', x0: 'Current', x1: 'Current', y0: 0, y1: 1,
          yref: 'paper', line: { color: '#ef4444', width: 1, dash: 'dot' },
        }],
      }}
      config={{ displayModeBar: false, responsive: true }}
      style={{ width: '100%' }}
    />
  );
}
