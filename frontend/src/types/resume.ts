// 求职方向（用于简历解析路由分类）
export type JobDirection = 'PROCUREMENT_SUPPLY_CHAIN' | 'SALES_BD' | 'TECH';

// 简历分析响应类型
export interface ResumeAnalysisResponse {
  overallScore: number;
  scoreDetail: ScoreDetail;
  summary: string;
  strengths: string[];
  suggestions: Suggestion[];
  originalText: string;
}

// 存储信息
export interface StorageInfo {
  fileKey: string;
  fileUrl: string;
  resumeId?: number;
}

// 上传API完整响应（异步模式：analysis 可能为空）
export interface UploadResponse {
  analysis?: ResumeAnalysisResponse;
  resume?: {
    id: number;
    filename: string;
    analyzeStatus: string;
  };
  storage: StorageInfo;
  /** 任务是否成功投递到异步队列；false 时简历已保存但状态为 FAILED，可重试 */
  enqueueAccepted: boolean;
  duplicate: boolean;
  message: string;
}

export interface ScoreDetail {
  contentScore: number;      // 内容完整性 (0-25)
  structureScore: number;    // 结构清晰度 (0-20)
  skillMatchScore: number;   // 技能匹配度 (0-25)
  expressionScore: number;   // 表达专业性 (0-15)
  projectScore: number;      // 项目经验 (0-15)
}

export interface Suggestion {
  category: string;         // 建议类别
  priority: '高' | '中' | '低';
  issue: string;            // 问题描述
  recommendation: string;   // 具体建议
}

export interface ApiError {
  error: string;
  detectedType?: string;
  allowedTypes?: string[];
}
