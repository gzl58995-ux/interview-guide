-- 为简历表新增求职方向字段（用于按方向路由解析提示词）。
-- V1 已包含该字段；本迁移用于未执行 V1 的既有库基线后补列。
DO $$
BEGIN
  IF to_regclass('public.resumes') IS NOT NULL THEN
    IF NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'resumes'
        AND column_name = 'job_direction'
    ) THEN
      ALTER TABLE resumes
        ADD COLUMN job_direction VARCHAR(64) NOT NULL DEFAULT 'TECH';
    END IF;

    COMMENT ON COLUMN resumes.job_direction IS '求职方向（提示词路由键，默认 TECH）';
  END IF;
END
$$;
