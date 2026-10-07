package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.TacticDef;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A team's combat memory: for every (unit type, enemy class) pair, how well each fighting style has traded so far.
 * Starts from the design sheet's priors (tactics.json), then learns from every engagement: damage dealt relative to
 * the enemy's health, minus damage taken relative to our own, plus kills, minus deaths. Units pick the best-scoring
 * style, still trying the others now and then (less as evidence builds up). Can be saved and loaded so an army
 * remembers across matches.
 */
public final class Tactics {
    static final int N = Combat.Style.values().length;
    static final float PRIOR = .25f;
    final Map<String, float[]> value = new HashMap<>();
    final Map<String, int[]> count = new HashMap<>();
    final Random rng;
    public int engagements, explored;

    Tactics(long seed) { rng = new Random(seed); }

    static Combat.Style prior(String myCls, String enemyCls) {
        for (TacticDef t : TacticDef.ALL) if (t.myClass().equals(myCls) && t.enemyClass().equals(enemyCls)) return Combat.Style.valueOf(t.style().toUpperCase());
        for (TacticDef t : TacticDef.ALL) if (t.myClass().equals(myCls) && t.enemyClass().equals("any")) return Combat.Style.valueOf(t.style().toUpperCase());
        return Combat.stylesFor(myCls)[0];
    }

    float[] values(String key, String myCls, String enemyCls) {
        return value.computeIfAbsent(key, k -> {
            float[] v = new float[N];
            v[prior(myCls, enemyCls).ordinal()] = PRIOR;
            return v;
        });
    }

    /** The style a unit should use against an enemy of this class. */
    Combat.Style choose(Unit u, String enemyCls) {
        String key = u.def.id() + "|" + enemyCls;
        float[] v = values(key, u.cls, enemyCls);
        int[] c = count.computeIfAbsent(key, k -> new int[N]);
        Combat.Style[] ok = Combat.stylesFor(u.cls);
        int total = 0; for (Combat.Style s : ok) total += c[s.ordinal()];
        float explore = .25f / (1 + total * .08f);   // explore early, settle later
        if (rng.nextFloat() < explore) { explored++; return ok[rng.nextInt(ok.length)]; }
        Combat.Style best = ok[0]; float bv = -1e9f;
        for (Combat.Style s : ok) if (v[s.ordinal()] > bv) { bv = v[s.ordinal()]; best = s; }
        return best;
    }

    /** Scores a finished engagement. */
    void learn(Unit u, Combat.Engagement e, boolean died) {
        if (e.dealt + e.taken < 1 && !died) return;   // nothing happened
        String key = u.def.id() + "|" + e.enemyCls;
        float[] v = values(key, u.cls, e.enemyCls);
        int[] c = count.computeIfAbsent(key, k -> new int[N]);
        float r = e.dealt / Math.max(50, e.enemyHp) - e.taken / Math.max(50, u.maxHp) + .5f * e.kills - (died ? .5f : 0);
        r = Math.max(-2, Math.min(2, r));
        int i = e.style.ordinal();
        c[i]++;
        float rate = 1f / Math.min(c[i] + 1, 25);
        v[i] += (r - v[i]) * rate;
        engagements++;
    }

    /** Best style per matchup, for UIs and benches. */
    public Map<String, String> summary() {
        Map<String, String> m = new java.util.TreeMap<>();
        for (var e : value.entrySet()) {
            float[] v = e.getValue();
            dev.beyondtabs.engine.gen.UnitDef d = dev.beyondtabs.engine.gen.UnitDef.byId(e.getKey().substring(0, e.getKey().indexOf('|')));
            Combat.Style[] ok = Combat.stylesFor(Combat.classOf(d, dev.beyondtabs.engine.gen.WeaponDef.byId(d.weaponClass()), dev.beyondtabs.engine.gen.AbilityDef.byId(d.ability())));
            int best = ok[0].ordinal();
            for (Combat.Style st : ok) if (v[st.ordinal()] > v[best]) best = st.ordinal();
            int[] c = count.getOrDefault(e.getKey(), new int[N]); int n = 0; for (int x : c) n += x;
            m.put(e.getKey(), Combat.Style.values()[best] + String.format(" (%.2f, %d fights)", v[best], n));
        }
        return m;
    }

    /** Plain-text save: one line per matchup, "key v0 .. v5 c0 .. c5". */
    public String save() {
        StringBuilder sb = new StringBuilder();
        for (var e : value.entrySet()) {
            sb.append(e.getKey());
            for (float f : e.getValue()) sb.append(' ').append(f);
            for (int x : count.getOrDefault(e.getKey(), new int[N])) sb.append(' ').append(x);
            sb.append('\n');
        }
        return sb.toString();
    }

    public void load(String text) {
        for (String line : text.split("\n")) {
            String[] p = line.trim().split(" ");
            if (p.length != 1 + 2 * N) continue;
            try {
                float[] v = new float[N]; int[] c = new int[N];
                for (int i = 0; i < N; i++) { v[i] = Float.parseFloat(p[1 + i]); c[i] = Math.min(15, Integer.parseInt(p[1 + N + i])); }   // cap old evidence so it can still adapt
                value.put(p[0], v); count.put(p[0], c);
            } catch (NumberFormatException ignored) { }
        }
    }
}
