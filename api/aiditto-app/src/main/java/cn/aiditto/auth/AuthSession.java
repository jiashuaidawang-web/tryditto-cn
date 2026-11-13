package cn.aiditto.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/** Opaque bearer sessions; only SHA-256(token) is stored (技术方案 §6.2). */
@TableName("auth_session")
public class AuthSession {
    @TableId(type = IdType.ASSIGN_ID)
    public Long id;
    public byte[] tokenHash;
    public Long userId;
    public Instant createdAt;
    public Instant lastSeenAt;
    public Instant expiresAt;
    public Instant revokedAt;
    public String userAgent;
    public String ip;
}
