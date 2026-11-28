package cn.aiditto.matchlab;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MatchLabFilterTest {
    @Test
    void aClosedSwitchRejectsTheApiBeforeTheRestOfTheChain() throws Exception {
        MatchLabProperties properties = new MatchLabProperties();
        MatchLabFilter filter = new MatchLabFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/match-lab/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignored, downstream) -> {
            throw new AssertionError("closed switch reached the chain");
        });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
    }

    @Test
    void anOpenSwitchLeavesOtherPathsAlone() throws Exception {
        MatchLabProperties properties = new MatchLabProperties();
        properties.setEnabled(true);
        MatchLabFilter filter = new MatchLabFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = {false};
        filter.doFilter(request, response, (ignored, downstream) -> called[0] = true);
        assertThat(called[0]).isTrue();
    }
}
