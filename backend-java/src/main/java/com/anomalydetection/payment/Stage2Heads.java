package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static com.anomalydetection.payment.Dim.*;

/** Monitoring split into independently-owned heads, each watching a few
 * dimensions instead of all of them.
 *
 * The reason is statistical as much as organizational: one monitor over
 * every dimension has a cell count equal to their cross-product, so at
 * 12-15 real features most cells see almost no traffic and their
 * contribution share is mostly noise. Each head here keeps few enough
 * dimensions that its cells stay dense enough to trust -- and small enough
 * that one team can reason about them.
 *
 * decline_code is in every head: contribution share is a share *of
 * declines*, so dropping the reason makes it meaningless.
 *
 * Each head runs both jobs a full monitor needs, at its own dimension
 * subset: ContributionHead (share vs. the rest of the window -- catches
 * masking) and RateHead (a cell's own rate vs. its own history -- catches
 * genuine drift regardless of share). Same pairing Stage 2 and Stage 3
 * provide today at the full 7-dim key.
 *
 * The cost: an anomaly that only shows up as an interaction between two
 * heads' dimensions is invisible to both. DrillDown is the mitigation --
 * it runs on demand against an already-flagged cell and can cross head
 * boundaries freely, precisely because it isn't a standing monitor. */
public class Stage2Heads {

    private record Head(String key, String name, String owner, List<Dim> dims) {}

    private static final List<Head> HEADS = List.of(
            new Head("network_channel", "Network & Channel", "Network Ops",
                    List.of(NETWORK, CHANNEL, GEOGRAPHY, DECLINE_CODE)),
            new Head("payment_method", "Payment Method", "Payment Product",
                    List.of(ENTRY_MODE, PURCHASE_TYPE, AUTH_TYPE, DECLINE_CODE)),
            new Head("acquiring_risk", "Acquiring & Risk", "Risk / Fraud",
                    List.of(BIN, ACQUIRER, DECLINE_CODE)));

    public static Map<String, Object> run(JdbcTemplate jdbc, int currentDay, int currentHour, double threshold) {
        Map<String, Object> heads = new LinkedHashMap<>();
        for (Head h : HEADS) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", h.name());
            result.put("owner", h.owner());
            result.put("dims", h.dims().stream().map(Dim::label).toList());
            result.putAll(ContributionHead.run(jdbc, currentDay, currentHour, threshold, h.dims()));
            result.put("wow", RateHead.run(jdbc, currentDay, currentHour, threshold, h.dims()));
            heads.put(h.key(), result);
        }
        return Map.of("heads", heads);
    }
}
