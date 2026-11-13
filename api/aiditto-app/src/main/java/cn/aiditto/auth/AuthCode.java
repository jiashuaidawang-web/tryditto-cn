package cn.aiditto.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/** SMS and email codes (技术方案 §4.3). In demo mode {@code codeHash} is the hash of DEMO_LOGIN_CODE. */
@TableName("auth_code")
public class AuthCode {
    public static final String SMS = "sms";
    public static final String EMAIL = "email";

    @TableId(type = IdType.ASSIGN_ID)
    public Long id;
    public String channel;
    public byte[] targetHash;
    public byte[] codeHash;
    public Instant sentAt;
    public Instant expiresAt;
    public Integer attempts;
    public Instant lastAttemptAt;
    public String ip;
}
