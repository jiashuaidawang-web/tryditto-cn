package cn.aiditto.matchlab;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Off unless an operator turns the research console on. A hidden menu is not this switch. */
@ConfigurationProperties("aiditto.match-lab")
public class MatchLabProperties {
    private boolean enabled = false;
    private String commitSha = "UNSPECIFIED";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
}
