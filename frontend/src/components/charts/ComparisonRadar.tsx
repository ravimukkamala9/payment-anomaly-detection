import React from 'react';
import Plot from 'react-plotly.js';
import { AnalysisResponse } from '../../types';
import { METHOD_COLORS, METHOD_LABELS } from '../../utils/colors';

interface Props {
  response: AnalysisResponse;
  height?: number;
}

const DIMS = ['Accuracy', 'Precision', 'Recall', 'F1 Score', 'Speed', 'Interpretability'];

function getRadarValues(key: string, response: AnalysisResponse): number[] {
  const r = response.results[key as keyof typeof response.results];
  if (!r) return [0, 0, 0, 0, 0, 0];
  const m = r.metrics;
  // Speed: normalize exec time (lower is better, cap at 1000ms)
  const speedScore = Math.max(0, 1 - (m.execution_time_ms / 200));
  // Interpretability scores (heuristic)
  const interp: Record<string, number> = { zscore: 0.95, isolation_forest: 0.6, lof: 0.5 };

  if (m.accuracy === null) {
    return [0.5, 0.5, 0.5, 0.5, speedScore, interp[key] ?? 0.5];
  }
  return [
    m.accuracy ?? 0,
    m.precision ?? 0,
    m.recall ?? 0,
    m.f1_score ?? 0,
    speedScore,
    interp[key] ?? 0.5,
  ];
}

export default function ComparisonRadar({ response, height = 360 }: Props) {
  const keys = Object.keys(response.results);

  const traces: Plotly.Data[] = keys.map(key => {
    const vals = getRadarValues(key, response);
    return {
      type: 'scatterpolar',
      r: [...vals, vals[0]],
      theta: [...DIMS, DIMS[0]],
      name: METHOD_LABELS[key] || key,
      fill: 'toself',
      line: { color: METHOD_COLORS[key] || '#888', width: 2 },
      marker: { color: METHOD_COLORS[key] || '#888', size: 5 },
      fillcolor: (METHOD_COLORS[key] || '#888') + '22',
    };
  });

  return (
    <Plot
      data={traces}
      layout={{
        polar: {
          radialaxis: { visible: true, range: [0, 1], gridcolor: '#2e3347', color: '#94a3b8', tickfont: { size: 10 } },
          angularaxis: { gridcolor: '#2e3347', color: '#94a3b8' },
          bgcolor: 'transparent',
        },
        paper_bgcolor: 'transparent',
        font: { color: '#94a3b8', family: 'Inter, system-ui, sans-serif', size: 11 },
        legend: { font: { color: '#94a3b8', size: 11 }, bgcolor: 'transparent' },
        margin: { t: 30, r: 60, b: 30, l: 60 },
        height,
        showlegend: true,
      }}
      config={{ displayModeBar: false, responsive: true }}
      style={{ width: '100%' }}
    />
  );
}
