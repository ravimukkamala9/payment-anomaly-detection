import React from 'react';

interface Props {
  label: string;
  value: string | number | null;
  unit?: string;
  color?: string;
  subtext?: string;
}

export default function MetricCard({ label, value, unit, color, subtext }: Props) {
  const display = value === null || value === undefined ? '—' : value;
  return (
    <div style={{
      background: 'var(--surface2)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--radius)',
      padding: '14px 18px',
    }}>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6 }}>
        {label}
      </div>
      <div style={{ fontSize: 24, fontWeight: 700, color: color || 'var(--text)' }}>
        {display}{unit && <span style={{ fontSize: 13, fontWeight: 400, marginLeft: 3 }}>{unit}</span>}
      </div>
      {subtext && <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>{subtext}</div>}
    </div>
  );
}
