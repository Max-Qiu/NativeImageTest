-- ============================================================
--  NativeImageTest 数据库初始化脚本
--  数据库: native_demo
-- ============================================================

-- 认证用户表（专用于 Spring Security 登录，不与业务表混用）
CREATE TABLE IF NOT EXISTS t_sys_user
(
    id       BIGINT      AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录用户名',
    password VARCHAR(100) NOT NULL        COMMENT 'BCrypt 编码密码',
    role     VARCHAR(50)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 业务用户表（仅用于 CRUD 功能测试）
CREATE TABLE IF NOT EXISTS t_user
(
    id   BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    age  INT         NOT NULL COMMENT '年龄'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ============================================================
--  初始数据
--  密码均为 123456（BCrypt 编码）
--  也可通过 POST /auth/register 动态注册
-- ============================================================
INSERT IGNORE INTO t_sys_user (username, password, role)
VALUES ('admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5/n9b3M3O0jH5w0WUE3MWj5jJX1hYTW', 'ADMIN'),
       ('alice', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5/n9b3M3O0jH5w0WUE3MWj5jJX1hYTW', 'USER');

INSERT IGNORE INTO t_user (id, name, age)
VALUES (1, 'Alice', 25),
       (2, 'Bob', 30),
       (3, 'Charlie', 25),
       (4, 'Diana', 35);
