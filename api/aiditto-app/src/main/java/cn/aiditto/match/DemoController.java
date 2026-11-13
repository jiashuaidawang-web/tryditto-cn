package cn.aiditto.match;

import cn.aiditto.auth.AuthUser;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.infra.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/** Demo panel. Missing entirely (404) when demo mode is off. */
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    private final DemoService demo;
    private final AppProperties props;
    private final Clock clock;

    public DemoController(DemoService demo, AppProperties props, Clock clock) {
        this.demo = demo;
        this.props = props;
        this.clock = clock;
    }

    record StageBody(String stage, String partnerId) {}

    @PostMapping("/match/stage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void stage(@AuthenticationPrincipal AuthUser me, @RequestBody StageBody body) {
        demoMode();
        demo.setStage(me.userId(), body.stage(), body.partnerId(), clock.instant());
    }

    record PartnerBody(String partnerId) {}

    @PostMapping("/match/partner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void partner(@AuthenticationPrincipal AuthUser me, @RequestBody PartnerBody body) {
        demoMode();
        demo.setPartner(me.userId(), body.partnerId(), clock.instant());
    }

    record WantsBody(boolean wantsAgain) {}

    @PostMapping("/match/partner-feedback-intent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void wants(@AuthenticationPrincipal AuthUser me, @RequestBody WantsBody body) {
        demoMode();
        demo.setPartnerWantsAgain(me.userId(), body.wantsAgain(), clock.instant());
    }

    record ActionBody(String action) {}

    @PostMapping("/match/partner-action")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void action(@AuthenticationPrincipal AuthUser me, @RequestBody ActionBody body) {
        demoMode();
        demo.partnerAction(me.userId(), body.action(), clock.instant());
    }

    record TimeBody(String to) {}

    @PostMapping("/match/time")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void time(@AuthenticationPrincipal AuthUser me, @RequestBody TimeBody body) {
        demoMode();
        demo.time(me.userId(), body.to(), clock.instant());
    }

    @PostMapping("/seed-profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void seed(@AuthenticationPrincipal AuthUser me) {
        demoMode();
    }

    @PostMapping("/reset-me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetMe(@AuthenticationPrincipal AuthUser me) {
        demoMode();
        demo.resetMe(me.userId());
    }

    private void demoMode() {
        if (!props.demoMode()) throw new DomainException(ErrorCode.NOT_FOUND);
    }
}
