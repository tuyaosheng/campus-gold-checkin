package com.school.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_info")
public class StudentInfo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    private String studentNo;
    private String studentName;
    private Integer displayOrderNo;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


