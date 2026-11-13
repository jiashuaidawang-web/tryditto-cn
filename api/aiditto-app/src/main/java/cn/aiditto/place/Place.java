package cn.aiditto.place;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/** Dating areas ({@code kind=area}) and venues ({@code kind=venue}), 技术方案 §4.5. Seeded by R__seed_places.sql. */
@TableName("place")
public class Place {
    @TableId(type = IdType.INPUT)
    public String id;
    public String kind;
    public String city;
    public String nameZh;
    public String nameEn;
    public String subZh;
    public String subEn;
    public String kw;
    public String nearby;
    public BigDecimal density;
    public Boolean enOnly;
    public BigDecimal lat;
    public BigDecimal lng;
    public String addressZh;
    public String addressEn;
    public String activityTags;
    public String openHours;
    public String defaultActivityZh;
    public String defaultActivityEn;
    public Boolean active;
    public Integer sortOrder;
}
