import React from 'react';
import Plot from 'react-plotly.js';
import { DetectionResult } from '../../types';

interface Props {
  result: DetectionResult;
  color: string;
  height?: number;
}

export default function ScoreDistribution({ result, color, height = 280 }: Props) {
  const normal = result.scores.filter((_, i) => result.predictions[i] === 0);
  const anomaly = result.scores.filter((_, i) => result.predictions[i] === 1);

  return (
    <Plot
      data={[
        {
          x: normal,
          type: 'histogram',
          name: 'Normal',
          marker: { color: '#3b82f6', opacity: 0.7 },
          nbinsx: 30,
        },
        {
          x: anomaly,
          type: 'histogram',
          name: 'Anomaly',
          marker: { color: '#ef4444', opacity: 0.8 },
          nbinsx: 20,
        },
      ]}
      layout={{
        barmode: 'overlay',
        title: { text: 'Score Distribution', font: { color: '#e2e8f0', size: 12 } },
        paper_bgcolor: 'transparent',
        plot_bgcolor: 'transparent',
        font: { color: '#94a3b8', family: 'Inter, system-ui, sans-serif', size: 11 },
        xaxis: { title: 'Anomaly Score', gridcolor: '#2e3347', zerolinecolor: '#2e3347', color: '#94a3b8' },
        yaxis: { title: 'Count', gridcolor: '#2e3347', zerolinecolor: '#2e3347', color: '#94a3b8' },
        legend: { font: { color: '#94a3b8', size: 11 }, bgcolor: 'transparent' },
        margin: { t: 36, r: 16, b: 45, l: 50 },
        height,
      }}
      config={{ displayModeBar: false, responsive: true }}
      style={{ width: '100%' }}
    />
  );
}
