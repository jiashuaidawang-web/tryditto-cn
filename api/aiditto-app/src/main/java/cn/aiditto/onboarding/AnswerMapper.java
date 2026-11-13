package cn.aiditto.onboarding;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface AnswerMapper extends BaseMapper<Answer> {

    @Insert("""
            INSERT INTO onboarding_answer (user_id, question_id, value_json, value_enc, updated_at)
            VALUES (#{userId}, #{questionId}, CAST(#{valueJson} AS JSON), #{valueEnc}, #{updatedAt})
            AS new ON DUPLICATE KEY UPDATE value_json = new.value_json, value_enc = new.value_enc, updated_at = new.updated_at
            """)
    void upsert(@Param("userId") long userId, @Param("questionId") String questionId, @Param("valueJson") String valueJson,
                @Param("valueEnc") byte[] valueEnc, @Param("updatedAt") Instant updatedAt);
}
