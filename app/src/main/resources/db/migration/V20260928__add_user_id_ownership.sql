-- 业务数据按 user_id 隔离
-- 历史数据尚未上线，直接清空全部业务表（保留 users），并重启自增序列
TRUNCATE TABLE
  resume_analyses,
  interview_answers,
  rag_chat_messages,
  rag_session_knowledge_bases,
  knowledge_base_questions,
  voice_interview_messages,
  voice_interview_evaluations,
  interview_sessions,
  resumes,
  knowledge_bases,
  rag_chat_sessions,
  interview_schedule,
  voice_interview_sessions,
  vector_store
RESTART IDENTITY CASCADE;

-- 聚合根表补充归属列（非空，写入当时必须由 Service 注入当前登录用户）
ALTER TABLE resumes
  ADD COLUMN user_id BIGINT NOT NULL;
ALTER TABLE interview_sessions
  ADD COLUMN user_id BIGINT NOT NULL;
ALTER TABLE knowledge_bases
  ADD COLUMN user_id BIGINT NOT NULL;
ALTER TABLE rag_chat_sessions
  ADD COLUMN user_id BIGINT NOT NULL;
ALTER TABLE interview_schedule
  ADD COLUMN user_id BIGINT NOT NULL;

-- 语音面试原 user_id 为 VARCHAR(255)，统一改为 BIGINT 对齐 users.id
ALTER TABLE voice_interview_sessions DROP COLUMN user_id;
ALTER TABLE voice_interview_sessions
  ADD COLUMN user_id BIGINT NOT NULL;

-- file_hash 去重从全局唯一改为按用户唯一，避免跨用户复用解析/分析结果
ALTER TABLE resumes DROP CONSTRAINT idx_resume_hash;
ALTER TABLE resumes
  ADD CONSTRAINT uk_resumes_user_file_hash UNIQUE (user_id, file_hash);

ALTER TABLE knowledge_bases DROP CONSTRAINT idx_kb_hash;
ALTER TABLE knowledge_bases
  ADD CONSTRAINT uk_kb_user_file_hash UNIQUE (user_id, file_hash);

-- 面试创建幂等键从全局唯一改为按用户唯一
DROP INDEX uk_interview_sessions_request_id;
CREATE UNIQUE INDEX uk_interview_sessions_user_request_id
  ON interview_sessions (user_id, request_id);

-- 归属列表查询索引
CREATE INDEX idx_resume_user_uploaded
  ON resumes (user_id, uploaded_at);
CREATE INDEX idx_interview_session_user_created
  ON interview_sessions (user_id, created_at);
CREATE INDEX idx_kb_user_uploaded
  ON knowledge_bases (user_id, uploaded_at);
CREATE INDEX idx_rag_session_user_updated
  ON rag_chat_sessions (user_id, updated_at);
CREATE INDEX idx_interview_schedule_user_time
  ON interview_schedule (user_id, interview_time);
CREATE INDEX idx_voice_session_user_updated
  ON voice_interview_sessions (user_id, updated_at);

COMMENT ON COLUMN resumes.user_id IS '归属用户 ID';
COMMENT ON COLUMN interview_sessions.user_id IS '归属用户 ID';
COMMENT ON COLUMN knowledge_bases.user_id IS '归属用户 ID';
COMMENT ON COLUMN rag_chat_sessions.user_id IS '归属用户 ID';
COMMENT ON COLUMN interview_schedule.user_id IS '归属用户 ID';
COMMENT ON COLUMN voice_interview_sessions.user_id IS '归属用户 ID';
