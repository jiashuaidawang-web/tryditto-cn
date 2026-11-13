package cn.aiditto.block;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlockMapper extends BaseMapper<Block> {
    @Insert("INSERT IGNORE INTO block_contact (block_id, user_id, kind, value_hash) VALUES (#{blockId}, #{userId}, #{kind}, #{hash})")
    int insertContact(long blockId, long userId, String kind, byte[] hash);

    @Delete("DELETE FROM block_contact WHERE block_id = #{blockId}")
    int deleteContacts(long blockId);
}
