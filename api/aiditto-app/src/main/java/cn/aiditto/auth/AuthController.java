package cn.aiditto.auth;

import cn.aiditto.infra.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final EmailCodeService emails;

    public AuthController(AuthService auth, EmailCodeService emails) {
        this.auth = auth;
        this.emails = emails;
    }

    record SendRequest(String phone) {}

    record LoginRequest(String phone, String code, String ref) {}

    record EmailRequest(String email, String code) {}

    @PostMapping("/sms/send")
    Map<String, Long> send(@RequestBody SendRequest body, HttpServletRequest req) {
        return Map.of("cooldown", auth.sendSms(body.phone(), ClientIp.of(req)));
    }

    @PostMapping("/login")
    AuthService.LoginResult login(@RequestBody LoginRequest body, HttpServletRequest req) {
        return auth.login(body.phone(), body.code(), body.ref(), req.getHeader(HttpHeaders.USER_AGENT), ClientIp.of(req));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal AuthUser me) {
        auth.logout(me);
    }

    @PostMapping("/email/code")
    Map<String, String> emailCode(@AuthenticationPrincipal AuthUser me, @RequestBody EmailRequest body, HttpServletRequest req) {
        return Map.of("sentTo", emails.send(body.email(), ClientIp.of(req)));
    }

    @PostMapping("/email/verify")
    Map<String, Boolean> emailVerify(@AuthenticationPrincipal AuthUser me, @RequestBody EmailRequest body, HttpServletRequest req) {
        return Map.of("verified", emails.verify(me.userId(), body.email(), body.code(), ClientIp.of(req)));
    }
}
