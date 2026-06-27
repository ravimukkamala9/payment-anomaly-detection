"""
Stage 3 — WoW Z-Score (Full Granularity)
No roll-up. For every cell active in the current window, compare its decline RATE
to the same cell at the same (day-of-week, hour) slot in the prior 4 weeks.
Catches what Stage 1 misses (low-volume cells drowned out at roll-up level)
and what Stage 2 misses (rate change without contribution share change).
"""
import time
import pandas as pd
import numpy as np

CELL_COLS = ['network', 'geography', 'entry_mode', 'purchase_type', 'auth_type', 'channel', 'decline_code']


def run(df: pd.DataFrame, current_day: int = 0, current_hour: int = 14, threshold: float = 3.0) -> dict:
    start = time.perf_counter()

    curr = df[(df['week'] == 0) & (df['day_of_week'] == current_day) & (df['hour'] == current_hour)]
    hist = df[(df['week'] > 0) & (df['day_of_week'] == current_day) & (df['hour'] == current_hour)]

    # Full cell-level aggregation
    curr_cell = curr.groupby(CELL_COLS).agg(
        total_count=('total_count', 'sum'),
        decline_count=('decline_count', 'sum'),
    ).reset_index()
    curr_cell['decline_rate'] = curr_cell['decline_count'] / curr_cell['total_count'].clip(lower=1)

    # Historical per-week decline rates
    hist_week = hist.groupby(['week'] + CELL_COLS).agg(
        total_count=('total_count', 'sum'),
        decline_count=('decline_count', 'sum'),
    ).reset_index()
    hist_week['decline_rate'] = hist_week['decline_count'] / hist_week['total_count'].clip(lower=1)

    # WoW stats — capture individual week rates for the chart
    def agg_weeks(group):
        rates = sorted(group[['week', 'decline_rate']].values.tolist(), key=lambda x: x[0])
        return pd.Series({
            'wow_mean': group['decline_rate'].mean(),
            'wow_std': group['decline_rate'].std(),
            'wow_min': group['decline_rate'].min(),
            'wow_max': group['decline_rate'].max(),
            'n_weeks': len(group),
            'w1_rate': next((r for w, r in rates if w == 1), None),
            'w2_rate': next((r for w, r in rates if w == 2), None),
            'w3_rate': next((r for w, r in rates if w == 3), None),
            'w4_rate': next((r for w, r in rates if w == 4), None),
        })

    stats = hist_week.groupby(CELL_COLS).apply(agg_weeks).reset_index()
    stats['wow_std'] = stats['wow_std'].fillna(0)

    merged = curr_cell.merge(stats, on=CELL_COLS, how='left')
    merged['is_new_cell'] = merged['wow_mean'].isna()
    merged['wow_mean'] = merged['wow_mean'].fillna(0)
    merged['z_wow'] = (
        (merged['decline_rate'] - merged['wow_mean'])
        / merged['wow_std'].clip(lower=0.0008)
    )
    merged.loc[merged['is_new_cell'], 'z_wow'] = 99.0
    merged['alerted'] = (merged['z_wow'].abs() >= threshold) | merged['is_new_cell']

    alerts = merged[merged['alerted']].sort_values('z_wow', ascending=False)

    # WoW line chart data — for each alert, show weeks −4..−1 + current
    wow_chart_data = []
    for _, row in alerts.head(10).iterrows():
        label = f"{row['network']}×{row['geography']}×{row['entry_mode']}×{row['decline_code']}"
        wow_chart_data.append({
            'cell': label,
            'short': f"{row['network']}×{row['decline_code']}",
            'week_rates': {
                'W-4': _safe(row.get('w4_rate')),
                'W-3': _safe(row.get('w3_rate')),
                'W-2': _safe(row.get('w2_rate')),
                'W-1': _safe(row.get('w1_rate')),
                'Current': round(float(row['decline_rate']), 4),
            },
            'wow_mean': round(float(row['wow_mean']), 4),
            'current_rate': round(float(row['decline_rate']), 4),
            'z_wow': round(float(row['z_wow']), 2),
            'is_new_cell': bool(row['is_new_cell']),
        })

    exec_ms = (time.perf_counter() - start) * 1000
    return {
        'stage': 3,
        'name': 'WoW Z-Score — Full Granularity',
        'description': (
            'No roll-up. Per-cell decline rate vs same Mon 14:00 in weeks −1..−4. '
            'Catches low-volume cells invisible to Stage 1, and rate shifts invisible to Stage 2.'
        ),
        'n_active_cells': int(len(merged)),
        'n_alerts': int(len(alerts)),
        'n_new_cells': int(merged['is_new_cell'].sum()),
        'threshold': threshold,
        'execution_ms': round(exec_ms, 2),
        'wow_chart_data': wow_chart_data,
        'alerts': _to_records(alerts[
            CELL_COLS + ['decline_rate', 'wow_mean', 'wow_std', 'z_wow', 'n_weeks', 'is_new_cell']
        ].head(20)),
    }


def _safe(v) -> float | None:
    if v is None or (isinstance(v, float) and np.isnan(v)): return None
    return round(float(v), 4)


def _to_records(df: pd.DataFrame) -> list:
    records = []
    for _, r in df.iterrows():
        d = {}
        for k, v in r.items():
            if isinstance(v, (np.integer,)): d[k] = int(v)
            elif isinstance(v, (np.floating,)): d[k] = None if np.isnan(v) else round(float(v), 6)
            elif isinstance(v, (np.bool_,)): d[k] = bool(v)
            else: d[k] = v
        records.append(d)
    return records
