package com.maxqiu.test.compatibility;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** Six numbered markers form a dataset; this fixture has no business meaning. */
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@TableName("native_probe_row")
public class ProbeRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runKey;
    private Integer ordinalIndex;
    private Phase phase;

    @Getter
    public enum Phase {
        ALPHA(10), BETA(20), GAMMA(30);

        @EnumValue
        private final int code;

        Phase(int code) {
            this.code = code;
        }
    }
}
