-- 用户增加管理员标识；注册接口一律创建普通用户，管理员由运维手工提升：
-- UPDATE users SET is_admin = TRUE WHERE username = 'xxx';
ALTER TABLE users
  ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.is_admin IS '是否管理员：仅管理员可访问系统设置 /api/llm-provider/**';
