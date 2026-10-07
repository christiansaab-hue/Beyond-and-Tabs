package dev.beyondtabs.engine;
/** Runs selected benches by name: battle60 battle300 abilities learning siege. */
public final class BenchOne {
    public static void main(String[] a) {
        for (String n : a) switch (n) {
            case "battle60" -> Bench.battle(60, true);
            case "battle300" -> Bench.battle(300, true);
            case "abilities" -> Bench.abilities();
            case "learning" -> Bench.learning();
            case "siege" -> Bench.siege();
            case "stands" -> { Bench.ragdollStands(); Bench.ragdollKnockAndRecover(); }
            default -> System.out.println("unknown " + n);
        }
        System.out.println(Bench.failures == 0 ? "OK" : Bench.failures + " FAILURES");
    }
}
