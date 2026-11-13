package cn.aiditto.account;

import cn.aiditto.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AccountController {
    private final AccountService accounts;
    private final MeService me;

    public AccountController(AccountService accounts, MeService me) {
        this.accounts = accounts;
        this.me = me;
    }

    @GetMapping("/api/me")
    MeService.MeView me(@AuthenticationPrincipal AuthUser user) {
        return me.me(user.userId());
    }

    @GetMapping("/api/referrals/me")
    Map<String, String> referral(@AuthenticationPrincipal AuthUser user) {
        return Map.of("url", accounts.inviteUrl(accounts.require(user.userId())));
    }

    @PostMapping("/api/account/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@AuthenticationPrincipal AuthUser user) {
        accounts.deactivate(user.userId());
    }

    @PostMapping("/api/account/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@AuthenticationPrincipal AuthUser user) {
        accounts.reactivate(user.userId());
    }

    @DeleteMapping("/api/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthUser user) {
        accounts.requestDelete(user.userId());
    }
}
