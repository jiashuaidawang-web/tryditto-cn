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
    void enabledWithoutANamedEnvironmentStaysClosed() throws Exception {
        MatchLabProperties properties = new MatchLabProperties();
        properties.setEnabled(true);
        MatchLabFilter filter = new MatchLabFilter(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/match-lab/overview"), response, (ignored, downstream) -> {
            throw new AssertionError("unnamed environment reached the chain");
        });
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void productionStaysClosedWhenTheSwitchIsOn() throws Exception {
        MatchLabProperties properties = new MatchLabProperties();
        properties.setEnabled(true);
        properties.setEnvironment("ci");
        properties.setProduction(true);
        MatchLabFilter filter = new MatchLabFilter(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/match-lab/score"), response, (ignored, downstream) -> {
            throw new AssertionError("production reached the chain");
        });
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void aNamedIsolatedEnvironmentReachesTheChain() throws Exception {
        MatchLabProperties properties = new MatchLabProperties();
        properties.setEnabled(true);
        properties.setEnvironment("ci");
        MatchLabFilter filter = new MatchLabFilter(properties);
        boolean[] called = {false};
        filter.doFilter(new MockHttpServletRequest("GET", "/api/match-lab/overview"), new MockHttpServletResponse(),
                (ignored, downstream) -> called[0] = true);
        assertThat(called[0]).isTrue();
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
