package cn.aiditto.match;

import cn.aiditto.auth.AuthUser;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.match.FeedbackAnswer;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches")
public class MatchController {
    private final MatchCard cards;
    private final MatchFlow flow;
    private final Clock clock;

    public MatchController(MatchCard cards, MatchFlow flow, Clock clock) {
        this.cards = cards;
        this.flow = flow;
        this.clock = clock;
    }

    @GetMapping("/current")
    Map<String, Object> current(@AuthenticationPrincipal AuthUser me) {
        return cards.current(me.userId(), clock.instant());
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        flow.accept(me.userId(), id, clock.instant());
    }

    record SlotsBody(List<String> slotIds) {}

    @PostMapping("/{id}/slots/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void slots(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @RequestBody SlotsBody body) {
        flow.confirmSlots(me.userId(), id, body.slotIds(), clock.instant());
    }

    record DeclineBody(List<String> reasons, String note) {}

    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @RequestBody DeclineBody body) {
        flow.decline(me.userId(), id, body.reasons(), body.note(), clock.instant());
    }

    @PostMapping("/{id}/reasons/{index}/flag")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void flag(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @PathVariable int index) {
        flow.flag(me.userId(), id, index);
    }

    @PostMapping("/{id}/checkin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void checkIn(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        flow.checkIn(me.userId(), id, clock.instant());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        flow.cancel(me.userId(), id, clock.instant());
    }

    record FeedbackBody(String met, Integer rating, String again, String comment, Boolean feltSafe) {}

    @PostMapping("/{id}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void feedback(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @RequestBody FeedbackBody body) {
        FeedbackAnswer answer;
        try {
            answer = new FeedbackAnswer(
                    FeedbackAnswer.Met.fromId(body.met()),
                    body.rating(),
                    body.again() == null || body.again().isBlank() ? null : FeedbackAnswer.Again.fromId(body.again()),
                    body.feltSafe());
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("feedback", "反馈内容不对");
        }
        flow.feedback(me.userId(), id, answer, body.comment(), clock.instant());
    }

    record ReportBody(String category, String note) {}

    @PostMapping("/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(@AuthenticationPrincipal AuthUser me, @PathVariable long id, @RequestBody ReportBody body) {
        flow.report(me.userId(), id, body.category(), body.note(), clock.instant());
    }

    @GetMapping("/{id}/contact")
    Map<String, Object> contact(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return cards.contact(me.userId(), id, clock.instant());
    }
}
