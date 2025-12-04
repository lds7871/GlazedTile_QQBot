#用于创建数据单表
##########################

CREATE TABLE vip_id (
     id INT AUTO_INCREMENT PRIMARY KEY,       -- 自增主键
     qq_id VARCHAR(20) NOT NULL,              -- 保存 QQID，长度可根据实际需求调整
     create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 默认当前时间
)comment '白名单用户表';


##########################
create table task_open
(
    param_name varchar(100) not null comment '任务名称',
    state      tinyint(1)   not null comment '状态'
)comment '任务状态表';

INSERT INTO task_open (param_name, state) VALUES
('isMsgLisATTask', 1),
('isMsgLisKeyWordTask', 1),
('isMsgLisUserCmdTask', 1),
('isMsgLisVipCmdTask', 1),
('isMsgSchTask', 1),
('isDailyGreetingCreateTask', 1),
('isDailyGreetingMorningTask', 1),
('isDailyGreetingEveningTask', 1);


##########################

CREATE TABLE old_game (
    id INT AUTO_INCREMENT PRIMARY KEY,         -- 自增主键
    content VARCHAR(2555) comment '内容',                     -- 长文本字段
    image_url VARCHAR(255) comment '图片URL',                    -- 保存图片URL的文本字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 默认当前时间
)comment '老古董游戏表';

##########################

CREATE TABLE group_task
(
    id       int auto_increment
        primary key,
    group_id varchar(50)          not null comment '群号',
    greeting tinyint(1) default 0 not null comment '问候',
    temp     tinyint(1) default 0 not null comment '保留栏位'
)
    comment '群组定时任务表';

##########################

CREATE TABLE daliy_greeting(
    id INT AUTO_INCREMENT PRIMARY KEY,
    today VARCHAR(50) NOT NULL COMMENT '日期',
    morning_text VARCHAR(2555) COMMENT '早安问候',
    evening_text VARCHAR(2555) COMMENT '晚安问候'
)comment '每日问候表';


