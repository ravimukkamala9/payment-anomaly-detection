import React from 'react';
import Plot from 'react-plotly.js';
import { AnalysisResponse, MethodKey } from '../../types';
import { METHOD_LABELS } from '../../utils/colors';

interface Props {
  response: AnalysisResponse;
  method: MethodKey;
  height?: number;
}

export default function ScatterPlot({ response, method, height = 380 }: Props) {
  const result = response.results[method];
  if (!result) return <div style={{ color: 'var(--text-muted)', padding: 20 }}>No result for this method.</div>;

  const f1 = response.feature_names[0] || 'feature_1';
  const f2 = response.feature_names[1] || 'feature_2';
  const fi = response.raw_columns.indexOf(f1);
  const fj = response.raw_columns.indexOf(f2);
  const xs = response.data.map(r => r[fi] ?? 0);
  const ys = response.data.map(r => r[fj] ?? 0);

  const normal: number[] = [], anomaly: number[] = [];
  result.predictions.forEach((p, i) => (p === 1 ? anomaly : normal).push(i));

  const traces: Plotly.Data[] = [
    {
      x: normal.map(i => xs[i]),
      y: normal.map(i => ys[i]),
      mode: 'markers',
      type: 'scatter',
      name: 'Normal',
      marker: { color: '#3b82f6', size: 6, opacity: 0.7 },
    },
    {
      x: anomaly.map(i => xs[i]),
      y: anomaly.map(i => ys[i]),
      mode: 'markers',
      type: 'scatter',
      name: 'Anomaly',
      marker: { color: '#ef4444', size: 9, symbol: 'x', opacity: 0.9, line: { width: 1.5, color: '#ef4444' } },
    },
  ];

  return (
    <Plot
      data={traces}
      layout={{
        title: { text: METHOD_LABELS[method], font: { color: '#e2e8f0', size: 14 } },
        paper_bgcolor: 'transparent',
        plot_bgcolor: 'transparent',
        font: { color: '#94a3b8', family: 'Inter, system-ui, sans-serif', size: 11 },
        xaxis: { title: f1, gridcolor: '#2e3347', zerolinecolor: '#2e3347', color: '#94a3b8' },
        yaxis: { title: f2, gridcolor: '#2e3347', zerolinecolor: '#2e3347', color: '#94a3b8' },
        legend: { font: { color: '#94a3b8', size: 11 }, bgcolor: 'transparent' },
        margin: { t: 40, r: 20, b: 50, l: 55 },
        height,
      }}
      config={{ displayModeBar: false, responsive: true }}
      style={{ width: '100%' }}
    />
  );
}
