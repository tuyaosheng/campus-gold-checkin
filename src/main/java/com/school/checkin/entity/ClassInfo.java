package com.school.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("class_info")
public class ClassInfo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String gradeName;
    private String className;
    private String teacherName;
    private String uniqueCode;
    private Integer studentCount;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
