package com.school.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("check_in_record")
public class CheckInRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    private Long studentId;
    private LocalDate checkinDate;
    private String timeSlot;
    private LocalDateTime checkinTime;
    private LocalDateTime createdAt;
}
