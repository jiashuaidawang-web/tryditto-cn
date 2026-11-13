package cn.aiditto.profile;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Structured projection of the answers that matching reads (技术方案 §4.4). Answers stay the source of truth.
 * Every column is rewritten on update so a cleared answer clears its column.
 */
@TableName("profile")
public class Profile {
    @TableId(type = IdType.INPUT)
    public Long userId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String gender;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public byte[] expectedGendersEnc;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public LocalDate birthday;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer ageMin;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer ageMax;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer heightCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean heightAny;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer heightMin;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer heightMax;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Boolean isStudent;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String schoolYear;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String schoolDomain;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String occupation;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String educationLevel;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String educationImportance;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String budget;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String matchingStyle;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String relationshipLooking;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String hobbies;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String hobbyDetails;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String placeId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String travelMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer travelMinutes;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public String firstMessage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Integer photoCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    public Instant updatedAt;
}
