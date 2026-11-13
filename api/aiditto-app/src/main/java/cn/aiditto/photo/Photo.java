package cn.aiditto.photo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/** 技术方案 §4.5. A row starts {@code pending} at upload-url and becomes {@code active} at finalize. */
@TableName("photo")
public class Photo {
    public static final String PENDING = "pending";
    public static final String ACTIVE = "active";
    public static final String DELETED = "deleted";

    @TableId(type = IdType.ASSIGN_ID)
    public Long id;
    public Long userId;
    public String kind;
    public Integer position;
    public String status;
    public String storageKey;
    public String contentType;
    public Integer width;
    public Integer height;
    public Integer bytes;
    public byte[] sha256;
    public String moderation;
    public Instant createdAt;
    public Instant deletedAt;
}
