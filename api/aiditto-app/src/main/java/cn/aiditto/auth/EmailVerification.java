package cn.aiditto.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("email_verification")
public class EmailVerification {
    @TableId(type = IdType.ASSIGN_ID)
    public Long id;
    public Long userId;
    public String kind;
    public byte[] emailEnc;
    public byte[] emailHash;
    public String domain;
    public Instant verifiedAt;
}
