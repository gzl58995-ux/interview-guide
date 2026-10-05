export const ROUTES = {
  login: '/login',
  register: '/register',
  interview: '/interview',
  interviewCreate: (requestId: string) => `/interview/create/${requestId}`,
  interviewSession: (sessionId: string) => `/interview/session/${sessionId}`,
  resumeUpload: '/upload',
  resumeHistory: '/history',
  knowledgebaseUpload: '/knowledgebase/upload',
  admin: '/admin',
  adminResumes: '/admin/resumes',
  adminResumeDetail: (resumeId: number) => `/admin/resumes/${resumeId}`,
  adminFeedback: '/admin/feedback',
} as const;

export const ROUTE_PATTERNS = {
  interviewCreate: 'interview/create/:requestId',
  interviewSession: 'interview/session/:activeSessionId',
} as const;
