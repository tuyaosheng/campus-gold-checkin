CREATE DATABASE IF NOT EXISTS `checkin_system` DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;
USE `checkin_system`;

DROP TABLE IF EXISTS `check_in_record`;
DROP TABLE IF EXISTS `student_info`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `class_info`;
DROP TABLE IF EXISTS `sys_setting`;

CREATE TABLE `class_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `grade_name` VARCHAR(50) NOT NULL COMMENT '年级名称',
  `class_name` VARCHAR(100) NOT NULL COMMENT '班级名称',
  `teacher_name` VARCHAR(50) DEFAULT NULL COMMENT '班主任姓名',
  `unique_code` VARCHAR(32) NOT NULL COMMENT '班级打卡特征码',
  `student_count` INT NOT NULL DEFAULT 0 COMMENT '学生人数',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_unique_code` (`unique_code`),
  UNIQUE KEY `uk_grade_class_name` (`grade_name`, `class_name`),
  KEY `idx_teacher_name` (`teacher_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='班级信息表';

CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
  `password` VARCHAR(128) NOT NULL COMMENT '密码摘要',
  `real_name` VARCHAR(50) NOT NULL COMMENT '姓名',
  `role_code` VARCHAR(20) NOT NULL COMMENT '角色编码：ADMIN/STAFF/GRADE_LEADER/TEACHER',
  `grade_name` VARCHAR(50) DEFAULT NULL COMMENT '绑定年级，级长账号使用',
  `class_id` BIGINT DEFAULT NULL COMMENT '教师绑定班级ID',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0停用',
  `must_change_password` TINYINT NOT NULL DEFAULT 1 COMMENT '是否首次登录后必须改密：1是 0否',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_role_grade_class` (`role_code`, `grade_name`, `class_id`),
  CONSTRAINT `fk_sys_user_class_id` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统用户表';

CREATE TABLE `student_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `class_id` BIGINT NOT NULL COMMENT '班级ID',
  `student_no` VARCHAR(50) DEFAULT NULL COMMENT '学号',
  `student_name` VARCHAR(50) NOT NULL COMMENT '学生姓名',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_student_no` (`class_id`, `student_no`),
  KEY `idx_student_class_name` (`class_id`, `student_name`),
  CONSTRAINT `fk_student_class_id` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='学生信息表';

CREATE TABLE `check_in_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `class_id` BIGINT NOT NULL COMMENT '班级ID',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `checkin_date` DATE NOT NULL COMMENT '打卡日期',
  `time_slot` VARCHAR(20) NOT NULL COMMENT '时段：MORNING/EVENING',
  `checkin_time` DATETIME NOT NULL COMMENT '打卡时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_date_slot` (`student_id`, `checkin_date`, `time_slot`),
  KEY `idx_record_class_date_slot` (`class_id`, `checkin_date`, `time_slot`),
  KEY `idx_record_time` (`checkin_time`),
  CONSTRAINT `fk_record_class_id` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`),
  CONSTRAINT `fk_record_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_info` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='打卡记录表';

CREATE TABLE `sys_setting` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `setting_key` VARCHAR(50) NOT NULL COMMENT '配置键',
  `setting_value` TEXT NOT NULL COMMENT '配置值',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统配置表';

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('semester_start_date', '2026-02-23', '学期开始日期');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes', '我相信——我的坚持，可以成就更好的自己！\n今天的专注，会变成明天的底气。\n每一次按时到位，都是对目标的靠近。', '每日金句，多行存储');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes_start_index', '1', '每日金句顺序轮换起始序号，从 1 开始');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes_start_date', '2026-03-10', '每日金句顺序轮换起始日期');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes_morning', '我相信——我的坚持，可以成就更好的自己！\n清晨的自律，是一天最好的开场。', '晨读金句，多行存储');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes_morning_start_index', '1', '晨读金句顺序轮换起始序号，从 1 开始');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes_morning_start_date', '2026-03-10', '晨读金句顺序轮换起始日期');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes_evening', '今天的专注，会变成明天的底气。\n把今天的坚持，写进明天的成绩里。', '晚读金句，多行存储');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes_evening_start_index', '1', '晚读金句顺序轮换起始序号，从 1 开始');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('daily_quotes_evening_start_date', '2026-03-10', '晚读金句顺序轮换起始日期');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('rank_rule_settings', 'mode=COUNT\nbronzeCount=0\nbronze2Count=5\nbronze1Count=10\nsilverCount=15\nsilver2Count=20\nsilver1Count=25\ngoldCount=32\ngold3Count=39\ngold2Count=46\ngold1Count=53\nplatinumCount=61\nplatinum3Count=69\nplatinum2Count=77\nplatinum1Count=85\ndiamondCount=94\ndiamond4Count=103\ndiamond3Count=112\ndiamond2Count=121\ndiamond1Count=130\nstarryCount=136\nstarry4Count=141\nstarry3Count=146\nstarry2Count=150\nstarry1Count=152\nkingCount=153\nlegendCount=159\ngloryCount=165\ngloryPercent=5\nlegendPercent=5\nkingPercent=10\nstarryPercent=10\ndiamondPercent=15\nplatinumPercent=15\ngoldPercent=15\nsilverPercent=15\nbronzePercent=10', '战力段位规则设置');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('evening_checkin_settings', 'defaultReturnWeekday=SUNDAY\noverrideReturnDate=\nrule.1=七年级|06:00|07:10|21:30|21:50|19:00|19:20\nrule.2=八年级|06:00|07:10|21:30|21:50|19:20|19:40\nrule.3=九年级|06:00|07:10|21:30|21:50|19:40|20:00', '分年级打卡规则设置');

INSERT INTO `sys_setting` (`setting_key`, `setting_value`, `remark`)
VALUES ('checkin_debug_enabled', '0', '打卡调试开关：1开启 0关闭');

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role_code`, `grade_name`, `class_id`, `enabled`, `must_change_password`)
VALUES ('admin', 'a3538747fa421fc41ed154f6d8c0ac83', '系统管理员', 'ADMIN', NULL, NULL, 1, 1);

