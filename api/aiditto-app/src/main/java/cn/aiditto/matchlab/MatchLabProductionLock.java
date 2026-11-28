package cn.aiditto.matchlab;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** A production profile closes Match Lab even if the switch was left on. */
@Component
public class MatchLabProductionLock {
    public MatchLabProductionLock(MatchLabProperties properties, Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile) || "production".equals(profile)) properties.setProduction(true);
        }
    }
}
