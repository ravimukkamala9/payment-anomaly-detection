import React from 'react';
import Plot from 'react-plotly.js';
import { AnalysisResponse } from '../../types';
import { METHOD_COLORS, METHOD_LABELS } from '../../utils/colors';

interface Props {
  response: AnalysisResponse;
  height?: number;
}

export default function MetricsBarChart({ response, height = 300 }: Props) {
  const keys = Object.keys(response.results);
  const metrics = ['precision', 'recall', 'f1_score', 'accuracy'] as const;
  const labels = ['Precision', 'Recall', 'F1 Score', 'Accuracy'];

  const traces: Plotly.Data[] = keys.map(key => {
    const r = response.results[key as keyof typeof response.results];
    return {
      type: 'bar',
      name: METHOD_LABELS[key] || key,
      x: labels,
      y: metrics.map(m => r?.metrics[m] ?? 0),
      marker: { color: METHOD_COLORS[key] || '#888' },
    };
  });

  return (
    <Plot
      data={traces}
      layout={{
        barmode: 'group',
        paper_bgcolor: 'transparent',
        plot_bgcolor: 'transparent',
        font: { color: '#94a3b8', family: 'Inter, system-ui, sans-serif', size: 11 },
        xaxis: { gridcolor: '#2e3347', color: '#94a3b8' },
        yaxis: { gridcolor: '#2e3347', zerolinecolor: '#2e3347', color: '#94a3b8', range: [0, 1.05] },
        legend: { font: { color: '#94a3b8', size: 11 }, bgcolor: 'transparent', orientation: 'h', y: -0.2 },
        margin: { t: 10, r: 20, b: 80, l: 50 },
        height,
      }}
      config={{ displayModeBar: false, responsive: true }}
      style={{ width: '100%' }}
    />
  );
}
