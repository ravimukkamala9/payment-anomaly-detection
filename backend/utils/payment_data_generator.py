import numpy as np
import pandas as pd

CELL_COLS = ['network', 'geography', 'entry_mode', 'purchase_type', 'auth_type', 'channel', 'decline_code']

DECLINE_CODES_40 = [
    'NSF', 'WRONG_PIN', 'CARD_LOCKED', 'EXPIRED_CARD', 'VELOCITY_EXCEEDED',
    'INTL_BLOCK', 'CVV_MISMATCH', 'INVALID_ACCOUNT', 'DO_NOT_HONOR', 'LOST_CARD',
    'STOLEN_CARD', 'RESTRICTED_CARD', 'INVALID_TXN', 'AMOUNT_LIMIT', 'DAILY_LIMIT',
    'MERCHANT_BLOCKED', 'FX_BLOCK', 'CASHBACK_NOT_AVAIL', 'PREAUTH_EXCEEDED',
    'NOT_ACTIVATED', 'ACCT_CLOSED', 'INSUFF_CREDIT', 'AUTH_TIMEOUT',
    'ISSUER_UNAVAIL', 'BAD_FORMAT', 'ACCT_FROZEN', 'DUPLICATE_TXN',
    'SUSPICION_FRAUD', 'ENROLLMENT_REQ', 'CHIP_ERROR', 'OFFLINE_DECLINE',
    'CONTACTLESS_LIMIT', 'CONTACTLESS_FALL', 'TOKEN_INVALID', 'BIOMETRIC_FAIL',
    'RISK_SCORE', 'GEO_RESTRICT', 'MERCHANT_CAT_BLOCK', 'INSTALLMENT_ERR',
    'MANUAL_REVIEW',
]

# (network, geography, entry_mode, purchase_type, auth_type, channel, decline_code, base_hourly_total, base_decline_rate)
CELLS = [
    # VISA DOMESTIC — High volume
    ('VISA','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','NSF',             500, 0.022),
    ('VISA','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','DO_NOT_HONOR',    500, 0.018),
    ('VISA','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','CARD_LOCKED',     500, 0.008),
    ('VISA','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','EXPIRED_CARD',    500, 0.005),
    ('VISA','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','VELOCITY_EXCEEDED',500,0.003),
    ('VISA','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','INVALID_ACCOUNT', 500, 0.006),
    ('VISA','DOMESTIC','TAP','NORMAL','AUTH','CARD_PRESENT','NSF',             300, 0.019),
    ('VISA','DOMESTIC','TAP','NORMAL','AUTH','CARD_PRESENT','DO_NOT_HONOR',    300, 0.015),
    ('VISA','DOMESTIC','TAP','NORMAL','AUTH','CARD_PRESENT','CONTACTLESS_LIMIT',300,0.012),
    ('VISA','DOMESTIC','TAP','NORMAL','AUTH','CARD_PRESENT','VELOCITY_EXCEEDED',300,0.004),
    ('VISA','DOMESTIC','PIN','NORMAL','AUTH','CARD_PRESENT','WRONG_PIN',       400, 0.035),
    ('VISA','DOMESTIC','PIN','NORMAL','AUTH','CARD_PRESENT','NSF',             400, 0.025),
    ('VISA','DOMESTIC','PIN','CASHBACK','AUTH','CARD_PRESENT','NSF',           150, 0.028),
    ('VISA','DOMESTIC','PIN','CASHBACK','AUTH','CARD_PRESENT','WRONG_PIN',     150, 0.038),
    ('VISA','DOMESTIC','CHIP','NORMAL','AUTH','CARD_PRESENT','NSF',            250, 0.020),
    ('VISA','DOMESTIC','CHIP','NORMAL','AUTH','CARD_PRESENT','CHIP_ERROR',     250, 0.006),
    ('VISA','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','CVV_MISMATCH',         200, 0.008),
    ('VISA','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','NSF',                  200, 0.015),
    ('VISA','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','DO_NOT_HONOR',         200, 0.012),
    ('VISA','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','RISK_SCORE',           200, 0.010),
    ('VISA','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','TOKEN_INVALID',        200, 0.007),
    ('VISA','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','SUSPICION_FRAUD',      100, 0.015),
    ('VISA','DOMESTIC','EMV','NORMAL','PREAUTH','CARD_PRESENT','NSF',          100, 0.018),
    ('VISA','DOMESTIC','EMV','NORMAL','PREAUTH_COMP','CARD_PRESENT','PREAUTH_EXCEEDED',90,0.010),
    ('VISA','DOMESTIC','EMV','NORMAL','PREAUTH_COMP','CARD_PRESENT','NSF',      90, 0.015),
    # VISA INTERNATIONAL
    ('VISA','INTERNATIONAL','EMV','NORMAL','AUTH','CARD_PRESENT','INTL_BLOCK',  80, 0.055),
    ('VISA','INTERNATIONAL','EMV','NORMAL','AUTH','CARD_PRESENT','NSF',         80, 0.035),
    ('VISA','INTERNATIONAL','TAP','NORMAL','AUTH','CARD_PRESENT','INTL_BLOCK',  60, 0.062),
    ('VISA','INTERNATIONAL','TAP','NORMAL','AUTH','CARD_PRESENT','NSF',         60, 0.030),  # ← ANOMALY 1
    ('VISA','INTERNATIONAL','WALLET','NORMAL','AUTH','ECOM','CVV_MISMATCH',     40, 0.045),
    ('VISA','INTERNATIONAL','WALLET','NORMAL','AUTH','ECOM','INTL_BLOCK',       40, 0.070),
    ('VISA','INTERNATIONAL','WALLET','NORMAL','AUTH','ECOM','RISK_SCORE',       35, 0.055),
    # MC DOMESTIC
    ('MC','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','NSF',               400, 0.021),
    ('MC','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','DO_NOT_HONOR',      400, 0.017),
    ('MC','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','INVALID_ACCOUNT',   400, 0.005),
    ('MC','DOMESTIC','TAP','NORMAL','AUTH','CARD_PRESENT','NSF',               250, 0.018),
    ('MC','DOMESTIC','TAP','NORMAL','AUTH','CARD_PRESENT','CONTACTLESS_LIMIT', 250, 0.010),
    ('MC','DOMESTIC','PIN','NORMAL','AUTH','CARD_PRESENT','WRONG_PIN',         300, 0.032),
    ('MC','DOMESTIC','PIN','NORMAL','AUTH','CARD_PRESENT','NSF',               300, 0.022),
    ('MC','DOMESTIC','PIN','CASHBACK','AUTH','CARD_PRESENT','WRONG_PIN',       120, 0.035),  # ← ANOMALY 2
    ('MC','DOMESTIC','PIN','CASHBACK','AUTH','CARD_PRESENT','NSF',             120, 0.025),
    ('MC','DOMESTIC','CHIP','NORMAL','AUTH','CARD_PRESENT','NSF',              200, 0.019),
    ('MC','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','NSF',                    180, 0.014),
    ('MC','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','CVV_MISMATCH',           180, 0.009),
    ('MC','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','RISK_SCORE',             150, 0.011),
    ('MC','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','TOKEN_INVALID',          150, 0.006),
    ('MC','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','SUSPICION_FRAUD',         80, 0.013),
    ('MC','DOMESTIC','EMV','NORMAL','PREAUTH','CARD_PRESENT','NSF',             80, 0.016),
    ('MC','DOMESTIC','EMV','NORMAL','PREAUTH_COMP','CARD_PRESENT','PREAUTH_EXCEEDED',70,0.012),
    # MC INTERNATIONAL
    ('MC','INTERNATIONAL','EMV','NORMAL','AUTH','CARD_PRESENT','INTL_BLOCK',    60, 0.058),
    ('MC','INTERNATIONAL','EMV','NORMAL','AUTH','CARD_PRESENT','NSF',           60, 0.038),
    ('MC','INTERNATIONAL','TAP','NORMAL','AUTH','CARD_PRESENT','INTL_BLOCK',    45, 0.065),
    ('MC','INTERNATIONAL','WALLET','NORMAL','AUTH','ECOM','CVV_MISMATCH',       35, 0.050),
    ('MC','INTERNATIONAL','WALLET','NORMAL','AUTH','ECOM','RISK_SCORE',         28, 0.050),
    # AMEX DOMESTIC
    ('AMEX','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','NSF',             150, 0.025),
    ('AMEX','DOMESTIC','EMV','NORMAL','AUTH','CARD_PRESENT','DO_NOT_HONOR',    150, 0.020),
    ('AMEX','DOMESTIC','TAP','NORMAL','AUTH','CARD_PRESENT','NSF',             100, 0.022),
    ('AMEX','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','NSF',                  120, 0.018),
    ('AMEX','DOMESTIC','WALLET','NORMAL','AUTH','ECOM','CVV_MISMATCH',         120, 0.010),
    # AMEX INTERNATIONAL — Low volume
    ('AMEX','INTERNATIONAL','EMV','NORMAL','AUTH','CARD_PRESENT','INTL_BLOCK',  30, 0.075),
    ('AMEX','INTERNATIONAL','TAP','NORMAL','AUTH','CARD_PRESENT','INTL_BLOCK',  20, 0.080),
    ('AMEX','INTERNATIONAL','WALLET','NORMAL','AUTH','ECOM','NSF',              25, 0.040),
    ('AMEX','INTERNATIONAL','WALLET','NORMAL','AUTH','ECOM','CVV_MISMATCH',     25, 0.060),  # ← ANOMALY 3
]

# Three injected anomalies — each designed to be caught by a specific stage
ANOMALY_DEFS = [
    {
        'key': ('VISA', 'INTERNATIONAL', 'TAP', 'NORMAL', 'AUTH', 'CARD_PRESENT', 'NSF'),
        'multiplier': 14.0,
        'stage': 1,
        'description': 'VISA Intl TAP NSF — rate spike ×14',
        'reason': 'Network routing misconfiguration sending international TAP auth to wrong endpoint, '
                  'generating NSF false declines at 14× normal rate.',
    },
    {
        'key': ('MC', 'DOMESTIC', 'PIN', 'CASHBACK', 'AUTH', 'CARD_PRESENT', 'WRONG_PIN'),
        'multiplier': 18.0,
        'stage': 2,
        'description': 'MC DOM PIN Cashback WRONG_PIN — contribution shift ×18',
        'reason': 'ATM firmware update introduced PIN verification bug specific to cashback transactions. '
                  'Now 9% of all declines vs normal 0.4% — invisible at roll-up level.',
    },
    {
        'key': ('AMEX', 'INTERNATIONAL', 'WALLET', 'NORMAL', 'AUTH', 'ECOM', 'CVV_MISMATCH'),
        'multiplier': 22.0,
        'stage': 3,
        'description': 'AMEX Intl Wallet CVV_MISMATCH — WoW break ×22',
        'reason': 'Token provisioning defect for AMEX international ecom wallet. Low-volume cell '
                  'invisible to roll-up. Same Monday 14:00 slot last 4 weeks: 1-2 declines. Today: 33.',
    },
]

HOUR_MULT = {
    0: 0.15, 1: 0.10, 2: 0.08, 3: 0.07, 4: 0.10, 5: 0.20,
    6: 0.40, 7: 0.70, 8: 0.90, 9: 1.10, 10: 1.30, 11: 1.40,
    12: 1.20, 13: 1.30, 14: 1.40, 15: 1.35, 16: 1.25, 17: 1.10,
    18: 0.90, 19: 0.80, 20: 0.70, 21: 0.60, 22: 0.45, 23: 0.30,
}
DAY_MULT = {0: 1.0, 1: 1.05, 2: 1.0, 3: 0.95, 4: 1.10, 5: 0.65, 6: 0.50}

CURRENT_DAY = 0   # Monday
CURRENT_HOUR = 14  # 14:00


def generate_payment_data(seed: int = 42) -> pd.DataFrame:
    rng = np.random.RandomState(seed)
    anomaly_map = {a['key']: a for a in ANOMALY_DEFS}

    # Group decline codes by their shared transaction context (everything but
    # decline_code). All decline codes in the same context are different ways
    # the SAME pool of transactions can fail, so they must share one
    # total_count per (week, day, hour) rather than each drawing its own.
    groups: dict = {}
    group_base_vol: dict = {}
    group_order = []
    for net, geo, entry, ptype, auth, ch, code, base_vol, base_rate in CELLS:
        gkey = (net, geo, entry, ptype, auth, ch)
        if gkey not in groups:
            groups[gkey] = []
            group_order.append(gkey)
            group_base_vol[gkey] = base_vol
        else:
            group_base_vol[gkey] = max(group_base_vol[gkey], base_vol)
        groups[gkey].append((code, base_rate))

    rows = []
    for week in range(5):           # 0=current, 1-4=historical
        for day in range(7):
            for hour in range(24):
                h = HOUR_MULT[hour]
                d = DAY_MULT[day]
                w = 1.0 + rng.normal(0, 0.03)  # slight weekly variation

                for gkey in group_order:
                    net, geo, entry, ptype, auth, ch = gkey
                    base_vol = group_base_vol[gkey]
                    total = max(0, int(base_vol * h * d * w * (1 + rng.normal(0, 0.08))))

                    for code, base_rate in groups[gkey]:
                        cell_key = (net, geo, entry, ptype, auth, ch, code)
                        rate = float(np.clip(base_rate * (1 + rng.normal(0, 0.12)), 0.001, 0.95))

                        is_anomaly_window = (week == 0 and day == CURRENT_DAY and hour == CURRENT_HOUR)
                        if is_anomaly_window and cell_key in anomaly_map:
                            rate = float(np.clip(rate * anomaly_map[cell_key]['multiplier'], 0, 0.98))

                        decline_count = int(np.clip(total * rate, 0, total))
                        rows.append({
                            'week': week,
                            'day_of_week': day,
                            'hour': hour,
                            'network': net,
                            'geography': geo,
                            'entry_mode': entry,
                            'purchase_type': ptype,
                            'auth_type': auth,
                            'channel': ch,
                            'decline_code': code,
                            'total_count': total,
                            'decline_count': decline_count,
                            'decline_rate': round(decline_count / total, 6) if total > 0 else 0.0,
                        })

    return pd.DataFrame(rows)


def get_anomaly_info() -> list:
    return [
        {
            'stage': a['stage'],
            'cell': ' × '.join(a['key']),
            'description': a['description'],
            'reason': a['reason'],
            'multiplier': a['multiplier'],
        }
        for a in ANOMALY_DEFS
    ]
