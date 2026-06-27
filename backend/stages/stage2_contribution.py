"""
Stage 2 — Contribution Shift Monitor
One SQL-like computation per window. For every cell that has data RIGHT NOW,
compute its share of total declines. Compare that share to same window in weeks −1..−4.
Catches masking: a small cell on fire stays invisible at roll-up, but its SHARE of total declines shifts.
Also self-discovers NEW cells (no history → immediate flag).
"""
import time
import pandas as pd
import numpy as np

CELL_COLS = ['network', 'geography', 'entry_mode', 'purchase_type', 'auth_type', 'channel', 'decline_code']


def run(df: pd.DataFrame, current_day: int = 0, current_hour: int = 14, threshold: float = 3.0) -> dict:
    start = time.perf_counter()

    curr = df[(df['week'] == 0) & (df['day_of_week'] == current_day) & (df['hour'] == current_hour)].copy()
    hist = df[(df['week'] > 0) & (df['day_of_week'] == current_day) & (df['hour'] == current_hour)].copy()

    # Current window: contribution % per cell
    total_curr_declines = int(curr['decline_count'].sum())
    curr_cell = curr.groupby(CELL_COLS).agg(
        decline_count=('decline_count', 'sum'),
        total_count=('total_count', 'sum'),
    ).reset_index()
    curr_cell['contribution_pct'] = curr_cell['decline_count'] / max(total_curr_declines, 1)

    # Historical: contribution % per cell per week
    hist_week_totals = hist.groupby('week')['decline_count'].sum().rename('week_total')
    hist = hist.join(hist_week_totals, on='week')
    hist['contribution_pct'] = hist['decline_count'] / hist['week_total'].clip(lower=1)
    hist_cell = hist.groupby(CELL_COLS).agg(
        hist_mean=('contribution_pct', 'mean'),
        hist_std=('contribution_pct', 'std'),
        n_weeks=('week', 'count'),
    ).reset_index()
    hist_cell['hist_std'] = hist_cell['hist_std'].fillna(0)

    merged = curr_cell.merge(hist_cell, on=CELL_COLS, how='left')
    merged['is_new_cell'] = merged['hist_mean'].isna()
    merged['hist_mean'] = merged['hist_mean'].fillna(0)
    merged['z_contribution'] = (
        (merged['contribution_pct'] - merged['hist_mean'])
        / merged['hist_std'].clip(lower=1e-6)
    )
    merged.loc[merged['is_new_cell'], 'z_contribution'] = 99.0
    merged['alerted'] = (merged['z_contribution'].abs() >= threshold) | merged['is_new_cell']

    alerts = merged[merged['alerted']].sort_values('z_contribution', ascending=False)

    # Bar chart data: current contribution vs historical baseline for top cells
    top = merged.sort_values('contribution_pct', ascending=False).head(20)
    chart_data = [
        {
            'label': f"{r['decline_code']}",
            'cell_short': f"{r['network']}×{r['geography']}×{r['decline_code']}",
            'contribution_pct': round(float(r['contribution_pct']) * 100, 3),
            'hist_mean_pct': round(float(r['hist_mean']) * 100, 3),
            'z_contribution': round(float(r['z_contribution']), 2) if not pd.isna(r['z_contribution']) else 0,
            'alerted': bool(r['alerted']),
        }
        for _, r in top.iterrows()
    ]

    exec_ms = (time.perf_counter() - start) * 1000
    return {
        'stage': 2,
        'name': 'Contribution Shift Monitor',
        'description': (
            'Single computation — no pre-defined monitors. '
            'Each active cell\'s share of total declines vs same window weeks −1..−4. '
            'Self-discovers new cell combinations.'
        ),
        'n_active_cells': int(len(merged)),
        'n_alerts': int(len(alerts)),
        'n_new_cells': int(merged['is_new_cell'].sum()),
        'total_declines_current': total_curr_declines,
        'threshold': threshold,
        'execution_ms': round(exec_ms, 2),
        'chart_data': chart_data,
        'alerts': _to_records(alerts[
            CELL_COLS + ['decline_count', 'contribution_pct', 'hist_mean', 'hist_std', 'z_contribution', 'is_new_cell']
        ].head(20)),
    }


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
