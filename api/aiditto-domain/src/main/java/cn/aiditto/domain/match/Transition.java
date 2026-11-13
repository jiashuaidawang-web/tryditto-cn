package cn.aiditto.domain.match;

import java.util.List;

/**
 * Result of {@link MatchMachine#apply}. {@code changed == false} means the event was an exact replay of
 * something already recorded (network retry, ticker re-run): nothing to write, no effects.
 */
public record Transition(MatchState from, MatchSnapshot after, List<Effect> effects, boolean changed, String rule) {

    public Transition {
        effects = List.copyOf(effects);
    }

    static Transition unchanged(MatchSnapshot m) {
        return new Transition(m.state(), m, List.of(), false, "replay");
    }

    public MatchState to() {
        return after.state();
    }
}
