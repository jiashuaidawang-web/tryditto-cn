package cn.aiditto.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Serializes per-user writes that check a count first (photos ≤ 6, blocks ≤ 20). Call inside a transaction. */
@Mapper
public interface UserLock {
    @Select("SELECT id FROM `user` WHERE id = #{userId} FOR UPDATE")
    Long lock(long userId);
}
