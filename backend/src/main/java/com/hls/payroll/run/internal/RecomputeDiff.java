package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.Kind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a recompute of a draft changed (spec 013b FR-008): people added, people removed, lines whose net changed, and
 * people who moved between payable and cannot-be-paid, each with the reason. A line whose own inputs did not change
 * (same fingerprint) is not in it.
 */
final class RecomputeDiff {

    /** A line as it was before the recompute. */
    record Before(UUID id, String fingerprint, PersonResult.State state, BigDecimal net, String name) {}

    record Key(Kind kind, UUID id) {}

    private final List<Map<String, Object>> added = new ArrayList<>();
    private final List<Map<String, Object>> removed = new ArrayList<>();
    private final List<Map<String, Object>> changed = new ArrayList<>();
    private final List<Map<String, Object>> moved = new ArrayList<>();

    private RecomputeDiff() {}

    /** Compares the lines the run had with the freshly computed results. */
    static RecomputeDiff between(Map<Key, Before> before, Map<Key, PersonResult> fresh) {
        RecomputeDiff diff = new RecomputeDiff();
        for (Map.Entry<Key, PersonResult> entry : fresh.entrySet()) {
            Before old = before.get(entry.getKey());
            PersonResult now = entry.getValue();
            if (old == null) {
                diff.added.add(entry(entry.getKey(), now.name(), "state", now.state().name(), "net", money(now.net()),
                        "reason", now.reason() == null ? "New in this run" : now.reason()));
            } else if (!old.fingerprint().equals(now.fingerprint())) {
                if (old.state() != now.state()) {
                    diff.moved.add(entry(entry.getKey(), now.name(), "from", old.state().name(), "to", now.state().name(),
                            "reason", now.state() == PersonResult.State.CANNOT_PAY ? now.reason() : "Can now be paid"));
                } else {
                    diff.changed.add(entry(entry.getKey(), now.name(), "oldNet", money(old.net()), "newNet", money(now.net()),
                            "reason", "The figures this line is built from changed"));
                }
            }
        }
        for (Map.Entry<Key, Before> entry : before.entrySet()) {
            if (!fresh.containsKey(entry.getKey())) {
                diff.removed.add(entry(entry.getKey(), entry.getValue().name(), "reason", "No longer payable in this month"));
            }
        }
        return diff;
    }

    boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && changed.isEmpty() && moved.isEmpty();
    }

    int added() {
        return added.size();
    }

    int removed() {
        return removed.size();
    }

    int changed() {
        return changed.size();
    }

    int moved() {
        return moved.size();
    }

    /** Ids of the people whose line must go (they are no longer in the run). */
    static Set<Key> removedKeys(Map<Key, Before> before, Map<Key, PersonResult> fresh) {
        Set<Key> keys = new java.util.LinkedHashSet<>(before.keySet());
        keys.removeAll(fresh.keySet());
        return keys;
    }

    /** The JSON stored as the run's last recompute summary and in the event. */
    String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("added", added);
        map.put("removed", removed);
        map.put("changed", changed);
        map.put("moved", moved);
        return Json.write(map);
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static Map<String, Object> entry(Key key, String name, String... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", key.kind().name());
        map.put("personId", key.id().toString());
        map.put("name", name);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
