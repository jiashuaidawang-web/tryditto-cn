package cn.aiditto.matchlab;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Off unless an operator turns the research console on inside a named non-production environment. */
@ConfigurationProperties("aiditto.match-lab")
public class MatchLabProperties {
    private boolean enabled = false;
    private boolean production = false;
    private String environment = "";
    private String commitSha = "UNSPECIFIED";

    public boolean admits() {
        if (!enabled || production) return false;
        return "local".equals(environment) || "ci".equals(environment) || "isolated".equals(environment);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isProduction() { return production; }
    public void setProduction(boolean production) { this.production = production; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
}
