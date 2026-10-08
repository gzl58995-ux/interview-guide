-- 为语音面试会话表新增求职方向字段（用于按方向路由提问策略）。
DO $$
BEGIN
  IF to_regclass('public.voice_interview_sessions') IS NOT NULL THEN
    IF NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'voice_interview_sessions'
        AND column_name = 'job_direction'
    ) THEN
      ALTER TABLE voice_interview_sessions
        ADD COLUMN job_direction VARCHAR(64) NOT NULL DEFAULT 'TECH';
    END IF;

    COMMENT ON COLUMN voice_interview_sessions.job_direction IS '求职方向（提示词路由键，默认 TECH）';
  END IF;
END
$$;
