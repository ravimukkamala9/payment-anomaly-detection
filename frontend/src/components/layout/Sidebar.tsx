import React from 'react';
import { Activity, BarChart2, BookOpen, Home, Upload, CreditCard } from 'lucide-react';

interface Props {
  active: string;
  onChange: (page: string) => void;
}

const navItems = [
  { id: 'dashboard', label: 'Dashboard', icon: Home },
  { id: 'analyze', label: 'Analyze', icon: Activity },
  { id: 'compare', label: 'Compare', icon: BarChart2 },
  { id: 'upload', label: 'Upload Data', icon: Upload },
  { id: 'payment', label: 'Payment Pipeline', icon: CreditCard },
  { id: 'tutorial', label: 'Learning Mode', icon: BookOpen },
];

export default function Sidebar({ active, onChange }: Props) {
  return (
    <aside style={{
      width: 220,
      background: 'var(--surface)',
      borderRight: '1px solid var(--border)',
      display: 'flex',
      flexDirection: 'column',
      padding: '0 12px',
      flexShrink: 0,
    }}>
      <div style={{ padding: '20px 8px 24px', borderBottom: '1px solid var(--border)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{
            width: 36, height: 36, borderRadius: 8,
            background: 'linear-gradient(135deg, var(--primary), var(--amber))',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Activity size={18} color="white" />
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--text)' }}>AnomalyDetect</div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>ML Framework</div>
          </div>
        </div>
      </div>

      <nav style={{ padding: '16px 0', flex: 1 }}>
        {navItems.map(({ id, label, icon: Icon }) => {
          const isActive = active === id;
          return (
            <button key={id} onClick={() => onChange(id)} style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '9px 12px',
              borderRadius: 7,
              background: isActive ? 'rgba(99,102,241,0.15)' : 'transparent',
              color: isActive ? 'var(--primary-light)' : 'var(--text-muted)',
              fontWeight: isActive ? 600 : 400,
              marginBottom: 2,
              fontSize: 13,
            }}>
              <Icon size={16} />
              {label}
            </button>
          );
        })}
      </nav>

      <div style={{ padding: '16px 8px', borderTop: '1px solid var(--border)', fontSize: 11, color: 'var(--text-muted)' }}>
        v1.0.0 · Z-Score · IForest · LOF
      </div>
    </aside>
  );
}
