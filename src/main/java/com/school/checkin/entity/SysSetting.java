package com.school.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_setting")
public class SysSetting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String settingKey;
    private String settingValue;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
