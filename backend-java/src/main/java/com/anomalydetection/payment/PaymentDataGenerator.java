package com.anomalydetection.payment;

import com.anomalydetection.utils.RandomState;

import java.util.*;

/** Port of utils/payment_data_generator.py */
public class PaymentDataGenerator {

    public static final int CURRENT_DAY = 0;   // Monday
    public static final int CURRENT_HOUR = 14; // 14:00

    public static class Cell {
        String network, geography, entryMode, purchaseType, authType, channel, declineCode;
        int baseVol;
        double baseRate;

        Cell(String network, String geography, String entryMode, String purchaseType, String authType,
             String channel, String declineCode, int baseVol, double baseRate) {
            this.network = network; this.geography = geography; this.entryMode = entryMode;
            this.purchaseType = purchaseType; this.authType = authType; this.channel = channel;
            this.declineCode = declineCode; this.baseVol = baseVol; this.baseRate = baseRate;
        }

        String key() {
            return network + "|" + geography + "|" + entryMode + "|" + purchaseType + "|" + authType + "|" + channel + "|" + declineCode;
        }
    }

    public static class AnomalyDef {
        public String[] key;
        public double multiplier;
        public int stage;
        public String description;
        public String reason;
        /** Diagnostic-only: the bin/acquirer a drill-down into this anomaly's
         * cell should find concentrated -- simulates "one processor/BIN range
         * is the actual root cause" the way a real drill-down would surface. */
        public String dominantBin;
        public String dominantAcquirer;

        AnomalyDef(String[] key, double multiplier, int stage, String description, String reason,
                   String dominantBin, String dominantAcquirer) {
            this.key = key; this.multiplier = multiplier; this.stage = stage;
            this.description = description; this.reason = reason;
            this.dominantBin = dominantBin; this.dominantAcquirer = dominantAcquirer;
        }

        String keyStr() {
            return String.join("|", key);
        }
    }

    /** Diagnostic-only dimensions -- never part of CellKey, only ever grouped
     * on by the drill-down endpoint once a core-key cell has already flagged. */
    public static final String[] BINS = {"411111", "400000", "520000", "550000", "370000", "601100", "353011", "622200"};
    public static final String[] ACQUIRERS = {"Acquirer-A", "Acquirer-B", "Acquirer-C", "Acquirer-D", "Acquirer-E"};
    private static final int SUB_SPLITS = 3;

    public static final List<Cell> CELLS = new ArrayList<>();
    public static final List<AnomalyDef> ANOMALY_DEFS = new ArrayList<>();
    public static final Map<Integer, Double> HOUR_MULT = new HashMap<>();
    public static final Map<Integer, Double> DAY_MULT = new HashMap<>();

    static {
        // VISA DOMESTIC
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","NSF",500,0.022));
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","DO_NOT_HONOR",500,0.018));
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","CARD_LOCKED",500,0.008));
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","EXPIRED_CARD",500,0.005));
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","VELOCITY_EXCEEDED",500,0.003));
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","INVALID_ACCOUNT",500,0.006));
        CELLS.add(new Cell("VISA","DOMESTIC","TAP","NORMAL","AUTH","CARD_PRESENT","NSF",300,0.019));
        CELLS.add(new Cell("VISA","DOMESTIC","TAP","NORMAL","AUTH","CARD_PRESENT","DO_NOT_HONOR",300,0.015));
        CELLS.add(new Cell("VISA","DOMESTIC","TAP","NORMAL","AUTH","CARD_PRESENT","CONTACTLESS_LIMIT",300,0.012));
        CELLS.add(new Cell("VISA","DOMESTIC","TAP","NORMAL","AUTH","CARD_PRESENT","VELOCITY_EXCEEDED",300,0.004));
        CELLS.add(new Cell("VISA","DOMESTIC","PIN","NORMAL","AUTH","CARD_PRESENT","WRONG_PIN",400,0.035));
        CELLS.add(new Cell("VISA","DOMESTIC","PIN","NORMAL","AUTH","CARD_PRESENT","NSF",400,0.025));
        CELLS.add(new Cell("VISA","DOMESTIC","PIN","CASHBACK","AUTH","CARD_PRESENT","NSF",150,0.028));
        CELLS.add(new Cell("VISA","DOMESTIC","PIN","CASHBACK","AUTH","CARD_PRESENT","WRONG_PIN",150,0.038));
        CELLS.add(new Cell("VISA","DOMESTIC","CHIP","NORMAL","AUTH","CARD_PRESENT","NSF",250,0.020));
        CELLS.add(new Cell("VISA","DOMESTIC","CHIP","NORMAL","AUTH","CARD_PRESENT","CHIP_ERROR",250,0.006));
        CELLS.add(new Cell("VISA","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","CVV_MISMATCH",200,0.008));
        CELLS.add(new Cell("VISA","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","NSF",200,0.015));
        CELLS.add(new Cell("VISA","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","DO_NOT_HONOR",200,0.012));
        CELLS.add(new Cell("VISA","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","RISK_SCORE",200,0.010));
        CELLS.add(new Cell("VISA","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","TOKEN_INVALID",200,0.007));
        CELLS.add(new Cell("VISA","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","SUSPICION_FRAUD",100,0.015));
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","PREAUTH","CARD_PRESENT","NSF",100,0.018));
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","PREAUTH_COMP","CARD_PRESENT","PREAUTH_EXCEEDED",90,0.010));
        CELLS.add(new Cell("VISA","DOMESTIC","EMV","NORMAL","PREAUTH_COMP","CARD_PRESENT","NSF",90,0.015));
        // VISA INTERNATIONAL
        CELLS.add(new Cell("VISA","INTERNATIONAL","EMV","NORMAL","AUTH","CARD_PRESENT","INTL_BLOCK",80,0.055));
        CELLS.add(new Cell("VISA","INTERNATIONAL","EMV","NORMAL","AUTH","CARD_PRESENT","NSF",80,0.035));
        CELLS.add(new Cell("VISA","INTERNATIONAL","TAP","NORMAL","AUTH","CARD_PRESENT","INTL_BLOCK",60,0.062));
        CELLS.add(new Cell("VISA","INTERNATIONAL","TAP","NORMAL","AUTH","CARD_PRESENT","NSF",60,0.030)); // ANOMALY 1
        CELLS.add(new Cell("VISA","INTERNATIONAL","WALLET","NORMAL","AUTH","ECOM","CVV_MISMATCH",40,0.045));
        CELLS.add(new Cell("VISA","INTERNATIONAL","WALLET","NORMAL","AUTH","ECOM","INTL_BLOCK",40,0.070));
        CELLS.add(new Cell("VISA","INTERNATIONAL","WALLET","NORMAL","AUTH","ECOM","RISK_SCORE",35,0.055));
        // MC DOMESTIC
        CELLS.add(new Cell("MC","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","NSF",400,0.021));
        CELLS.add(new Cell("MC","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","DO_NOT_HONOR",400,0.017));
        CELLS.add(new Cell("MC","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","INVALID_ACCOUNT",400,0.005));
        CELLS.add(new Cell("MC","DOMESTIC","TAP","NORMAL","AUTH","CARD_PRESENT","NSF",250,0.018));
        CELLS.add(new Cell("MC","DOMESTIC","TAP","NORMAL","AUTH","CARD_PRESENT","CONTACTLESS_LIMIT",250,0.010));
        CELLS.add(new Cell("MC","DOMESTIC","PIN","NORMAL","AUTH","CARD_PRESENT","WRONG_PIN",300,0.032));
        CELLS.add(new Cell("MC","DOMESTIC","PIN","NORMAL","AUTH","CARD_PRESENT","NSF",300,0.022));
        CELLS.add(new Cell("MC","DOMESTIC","PIN","CASHBACK","AUTH","CARD_PRESENT","WRONG_PIN",120,0.035)); // ANOMALY 2
        CELLS.add(new Cell("MC","DOMESTIC","PIN","CASHBACK","AUTH","CARD_PRESENT","NSF",120,0.025));
        CELLS.add(new Cell("MC","DOMESTIC","CHIP","NORMAL","AUTH","CARD_PRESENT","NSF",200,0.019));
        CELLS.add(new Cell("MC","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","NSF",180,0.014));
        CELLS.add(new Cell("MC","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","CVV_MISMATCH",180,0.009));
        CELLS.add(new Cell("MC","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","RISK_SCORE",150,0.011));
        CELLS.add(new Cell("MC","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","TOKEN_INVALID",150,0.006));
        CELLS.add(new Cell("MC","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","SUSPICION_FRAUD",80,0.013));
        CELLS.add(new Cell("MC","DOMESTIC","EMV","NORMAL","PREAUTH","CARD_PRESENT","NSF",80,0.016));
        CELLS.add(new Cell("MC","DOMESTIC","EMV","NORMAL","PREAUTH_COMP","CARD_PRESENT","PREAUTH_EXCEEDED",70,0.012));
        // MC INTERNATIONAL
        CELLS.add(new Cell("MC","INTERNATIONAL","EMV","NORMAL","AUTH","CARD_PRESENT","INTL_BLOCK",60,0.058));
        CELLS.add(new Cell("MC","INTERNATIONAL","EMV","NORMAL","AUTH","CARD_PRESENT","NSF",60,0.038));
        CELLS.add(new Cell("MC","INTERNATIONAL","TAP","NORMAL","AUTH","CARD_PRESENT","INTL_BLOCK",45,0.065));
        CELLS.add(new Cell("MC","INTERNATIONAL","WALLET","NORMAL","AUTH","ECOM","CVV_MISMATCH",35,0.050));
        CELLS.add(new Cell("MC","INTERNATIONAL","WALLET","NORMAL","AUTH","ECOM","RISK_SCORE",28,0.050));
        // AMEX DOMESTIC
        CELLS.add(new Cell("AMEX","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","NSF",150,0.025));
        CELLS.add(new Cell("AMEX","DOMESTIC","EMV","NORMAL","AUTH","CARD_PRESENT","DO_NOT_HONOR",150,0.020));
        CELLS.add(new Cell("AMEX","DOMESTIC","TAP","NORMAL","AUTH","CARD_PRESENT","NSF",100,0.022));
        CELLS.add(new Cell("AMEX","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","NSF",120,0.018));
        CELLS.add(new Cell("AMEX","DOMESTIC","WALLET","NORMAL","AUTH","ECOM","CVV_MISMATCH",120,0.010));
        // AMEX INTERNATIONAL
        CELLS.add(new Cell("AMEX","INTERNATIONAL","EMV","NORMAL","AUTH","CARD_PRESENT","INTL_BLOCK",30,0.075));
        CELLS.add(new Cell("AMEX","INTERNATIONAL","TAP","NORMAL","AUTH","CARD_PRESENT","INTL_BLOCK",20,0.080));
        CELLS.add(new Cell("AMEX","INTERNATIONAL","WALLET","NORMAL","AUTH","ECOM","NSF",25,0.040));
        CELLS.add(new Cell("AMEX","INTERNATIONAL","WALLET","NORMAL","AUTH","ECOM","CVV_MISMATCH",25,0.060)); // ANOMALY 3

        ANOMALY_DEFS.add(new AnomalyDef(
                new String[]{"VISA","INTERNATIONAL","TAP","NORMAL","AUTH","CARD_PRESENT","NSF"},
                14.0, 1, "VISA Intl TAP NSF — rate spike ×14",
                "Network routing misconfiguration sending international TAP auth to wrong endpoint, " +
                "generating NSF false declines at 14× normal rate.",
                "601100", "Acquirer-D"));
        ANOMALY_DEFS.add(new AnomalyDef(
                new String[]{"MC","DOMESTIC","PIN","CASHBACK","AUTH","CARD_PRESENT","WRONG_PIN"},
                18.0, 2, "MC DOM PIN Cashback WRONG_PIN — contribution shift ×18",
                "ATM firmware update introduced PIN verification bug specific to cashback transactions. " +
                "Now 9% of all declines vs normal 0.4% — invisible at roll-up level.",
                "520000", "Acquirer-B"));
        ANOMALY_DEFS.add(new AnomalyDef(
                new String[]{"AMEX","INTERNATIONAL","WALLET","NORMAL","AUTH","ECOM","CVV_MISMATCH"},
                22.0, 3, "AMEX Intl Wallet CVV_MISMATCH — WoW break ×22",
                "Token provisioning defect for AMEX international ecom wallet. Low-volume cell " +
                "invisible to roll-up. Same Monday 14:00 slot last 4 weeks: 1-2 declines. Today: 33.",
                "370000", "Acquirer-E"));

        double[] hourMultVals = {0.15,0.10,0.08,0.07,0.10,0.20,0.40,0.70,0.90,1.10,1.30,1.40,1.20,1.30,1.40,1.35,1.25,1.10,0.90,0.80,0.70,0.60,0.45,0.30};
        for (int h = 0; h < 24; h++) HOUR_MULT.put(h, hourMultVals[h]);
        double[] dayMultVals = {1.0,1.05,1.0,0.95,1.10,0.65,0.50};
        for (int d = 0; d < 7; d++) DAY_MULT.put(d, dayMultVals[d]);
    }

    /** Grouping key: everything but decline_code — different decline codes on the
     * same transaction context are different ways the SAME pool of transactions
     * can fail, so they must share one total_count per (week, day, hour). */
    private static String groupKey(Cell c) {
        return c.network + "|" + c.geography + "|" + c.entryMode + "|" + c.purchaseType + "|" + c.authType + "|" + c.channel;
    }

    public static List<PaymentRow> generate(long seed) {
        RandomState rng = new RandomState(seed);
        // Separate stream so the bin/acquirer split never perturbs the RNG
        // sequence driving total_count/decline_count -- Stage 1/2/3 outputs
        // must stay byte-for-byte identical to before this split existed.
        RandomState splitRng = new RandomState(seed + 1_000_000);
        Map<String, AnomalyDef> anomalyMap = new HashMap<>();
        for (AnomalyDef a : ANOMALY_DEFS) anomalyMap.put(a.keyStr(), a);

        LinkedHashMap<String, List<Cell>> groups = new LinkedHashMap<>();
        Map<String, Integer> groupBaseVol = new HashMap<>();
        for (Cell cell : CELLS) {
            String gkey = groupKey(cell);
            groups.computeIfAbsent(gkey, k -> new ArrayList<>()).add(cell);
            groupBaseVol.merge(gkey, cell.baseVol, Math::max);
        }

        List<PaymentRow> rows = new ArrayList<>();
        for (int week = 0; week < 5; week++) {
            for (int day = 0; day < 7; day++) {
                for (int hour = 0; hour < 24; hour++) {
                    double h = HOUR_MULT.get(hour);
                    double d = DAY_MULT.get(day);
                    double w = 1.0 + rng.normal(0, 0.03);

                    for (Map.Entry<String, List<Cell>> entry : groups.entrySet()) {
                        int baseVol = groupBaseVol.get(entry.getKey());
                        int total = Math.max(0, (int) (baseVol * h * d * w * (1 + rng.normal(0, 0.08))));

                        for (Cell cell : entry.getValue()) {
                            double rate = clip(cell.baseRate * (1 + rng.normal(0, 0.12)), 0.001, 0.95);

                            boolean isAnomalyWindow = (week == 0 && day == CURRENT_DAY && hour == CURRENT_HOUR);
                            AnomalyDef anomaly = isAnomalyWindow ? anomalyMap.get(cell.key()) : null;
                            if (anomaly != null) {
                                rate = clip(rate * anomaly.multiplier, 0, 0.98);
                            }

                            int declineCount = (int) clip(total * rate, 0, total);
                            double declineRate = total > 0 ? Math.round(((double) declineCount / total) * 1e6) / 1e6 : 0.0;

                            splitIntoBinAcquirerRows(rows, splitRng, week, day, hour, cell, total, declineCount, anomaly);
                        }
                    }
                }
            }
        }
        return rows;
    }

    /** Splits one (week, day, hour, cell) aggregate into SUB_SPLITS bin×acquirer
     * sub-rows whose total_count/decline_count sum back exactly to the inputs.
     * Stage 1/2/3 group only on the 7-dim CellKey (never bin/acquirer), so this
     * split is invisible to them -- summing the sub-rows reproduces the exact
     * same monitored numbers as before. For an anomaly window, one dominant
     * bin/acquirer pair is given the large majority of the declines, so a
     * drill-down into that cell finds a concentrated root cause instead of an
     * even spread. */
    private static void splitIntoBinAcquirerRows(List<PaymentRow> rows, RandomState rng, int week, int day, int hour,
                                                   Cell cell, int total, int declineCount, AnomalyDef anomaly) {
        String[] bins = new String[SUB_SPLITS];
        String[] acquirers = new String[SUB_SPLITS];
        int[] totals = new int[SUB_SPLITS];
        int[] declines = new int[SUB_SPLITS];

        if (anomaly != null) {
            bins[0] = anomaly.dominantBin;
            acquirers[0] = anomaly.dominantAcquirer;
            totals[0] = Math.max((int) Math.round(total * 0.5), Math.min(total, 1));
            declines[0] = Math.min((int) Math.round(declineCount * 0.85), totals[0]);

            int remainingTotal = total - totals[0];
            int remainingDeclines = declineCount - declines[0];
            int allocatedTotal = 0, allocatedDeclines = 0;
            for (int i = 1; i < SUB_SPLITS; i++) {
                bins[i] = distinctChoice(rng, BINS, anomaly.dominantBin);
                acquirers[i] = distinctChoice(rng, ACQUIRERS, anomaly.dominantAcquirer);
                boolean last = (i == SUB_SPLITS - 1);
                totals[i] = last ? (remainingTotal - allocatedTotal) : (remainingTotal / (SUB_SPLITS - 1));
                declines[i] = last ? (remainingDeclines - allocatedDeclines) : Math.min(remainingDeclines / (SUB_SPLITS - 1), totals[i]);
                allocatedTotal += totals[i];
                allocatedDeclines += declines[i];
            }
        } else {
            double[] weights = new double[SUB_SPLITS];
            double sumW = 0;
            for (int i = 0; i < SUB_SPLITS; i++) { weights[i] = rng.nextDouble() + 0.1; sumW += weights[i]; }

            int allocatedTotal = 0, allocatedDeclines = 0;
            for (int i = 0; i < SUB_SPLITS; i++) {
                bins[i] = rng.choice(BINS);
                acquirers[i] = rng.choice(ACQUIRERS);
                boolean last = (i == SUB_SPLITS - 1);
                totals[i] = last ? (total - allocatedTotal) : (int) Math.round(total * weights[i] / sumW);
                declines[i] = last ? (declineCount - allocatedDeclines) : Math.min((int) Math.round(declineCount * weights[i] / sumW), totals[i]);
                allocatedTotal += totals[i];
                allocatedDeclines += declines[i];
            }
        }

        for (int i = 0; i < SUB_SPLITS; i++) {
            int t = Math.max(totals[i], 0);
            int dc = Math.max(Math.min(declines[i], t), 0);
            double rate = t > 0 ? Math.round(((double) dc / t) * 1e6) / 1e6 : 0.0;
            rows.add(new PaymentRow(week, day, hour, cell.network, cell.geography, cell.entryMode,
                    cell.purchaseType, cell.authType, cell.channel, cell.declineCode,
                    t, dc, rate, bins[i], acquirers[i]));
        }
    }

    private static String distinctChoice(RandomState rng, String[] pool, String exclude) {
        String pick;
        do { pick = rng.choice(pool); } while (pick.equals(exclude) && pool.length > 1);
        return pick;
    }

    private static double clip(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static List<Map<String, Object>> getAnomalyInfo() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AnomalyDef a : ANOMALY_DEFS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", a.stage);
            m.put("cell", String.join(" × ", a.key));
            m.put("description", a.description);
            m.put("reason", a.reason);
            m.put("multiplier", a.multiplier);
            out.add(m);
        }
        return out;
    }
}
