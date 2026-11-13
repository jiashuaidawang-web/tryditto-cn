package cn.aiditto.profile;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProfileMapper extends BaseMapper<Profile> {
    @Update("UPDATE profile SET photo_count = #{count} WHERE user_id = #{userId}")
    int setPhotoCount(long userId, long count);
}
