package cn.aiditto.block;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/** "Don't match me with" entry (技术方案 §4.7). Contents are encrypted; {@code block_contact} keeps lookup hashes. */
@TableName("\"block\"")
public class Block {
    @TableId(type = IdType.ASSIGN_ID)
    public Long id;
    public Long userId;
    public byte[] nameEnc;
    public byte[] emailsEnc;
    public byte[] phonesEnc;
    public byte[] noteEnc;
    public Instant createdAt;
}
