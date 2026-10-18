package dev.lastpagans.recruitsperformance.api.formation;

import java.util.*;

public final class FormationAssignments {
    private FormationAssignments() {}

    /** Preserves every valid unique slot, then fills vacancies in deterministic UUID order. */
    public static Map<UUID, Integer> stable(Collection<UUID> members, Map<UUID, Integer> previous, int slotCount) {
        if (slotCount < members.size()) throw new IllegalArgumentException("not enough slots for members");
        List<UUID> ordered = members.stream().distinct().sorted().toList();
        LinkedHashMap<UUID, Integer> result = new LinkedHashMap<>();
        BitSet used = new BitSet(slotCount);
        for (UUID member : ordered) {
            Integer slot = previous.get(member);
            if (slot != null && slot >= 0 && slot < slotCount && !used.get(slot)) {
                result.put(member, slot);
                used.set(slot);
            }
        }
        int next = 0;
        for (UUID member : ordered) {
            if (result.containsKey(member)) continue;
            next = used.nextClearBit(next);
            result.put(member, next);
            used.set(next);
        }
        return Collections.unmodifiableMap(result);
    }
}
