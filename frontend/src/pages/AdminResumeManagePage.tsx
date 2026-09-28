import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertCircle, CheckCircle, Clock, Download, FileStack, RefreshCw, RotateCw, Search, Users } from 'lucide-react';
import {
  adminApi,
  type AdminResumeItem,
  type AdminResumeOwner,
  type AdminResumeSort,
  type AdminResumeStats,
} from '../api/admin';
import type { AnalyzeStatus } from '../api/history';
import { ROUTES } from '../constants/routes';
import { formatDateOnly } from '../utils/date';
import { getScoreColor } from '../utils/score';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

type StatusFilter = AnalyzeStatus | 'ALL';
type OwnerFilter = number | 'ALL';

const STATUS_FILTER_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: '全部状态' },
  { value: 'COMPLETED', label: '分析完成' },
  { value: 'PROCESSING', label: '分析中' },
  { value: 'PENDING', label: '等待分析' },
  { value: 'FAILED', label: '分析失败' },
];

const SORT_OPTIONS: { value: AdminResumeSort; label: string }[] = [
  { value: 'UPLOADED_AT_DESC', label: '上传时间新→旧' },
  { value: 'UPLOADED_AT_ASC', label: '上传时间旧→新' },
  { value: 'ACCESS_COUNT_DESC', label: '访问次数多→少' },
  { value: 'FILE_SIZE_DESC', label: '文件大小大→小' },
];

function isAnalyzing(status?: string): boolean {
  return status === 'PENDING' || status === 'PROCESSING';
}

function getAnalyzeStatusText(status?: string): string {
  if (status === 'FAILED') return '分析失败';
  if (status === 'PROCESSING') return '分析中';
  if (status === 'PENDING') return '等待分析';
  if (status === 'COMPLETED') return '分析完成';
  return '待分析';
}

function AnalyzeStatusIcon({ status }: { status?: string }) {
  if (status === 'FAILED') return <AlertCircle className="w-4 h-4 text-red-500 dark:text-red-400"/>;
  if (isAnalyzing(status)) return <RefreshCw className="w-4 h-4 text-blue-500 dark:text-blue-400 animate-spin"/>;
  if (status === 'COMPLETED') return <CheckCircle className="w-4 h-4 text-green-500 dark:text-green-400"/>;
  return <Clock className="w-4 h-4 text-yellow-500 dark:text-yellow-400"/>;
}

function formatFileSize(bytes: number): string {
  if (bytes <= 0) return '-';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), sizes.length - 1);
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function AdminResumeManagePage() {
  const navigate = useNavigate();
  const [resumes, setResumes] = useState<AdminResumeItem[]>([]);
  const [stats, setStats] = useState<AdminResumeStats | null>(null);
  const [owners, setOwners] = useState<AdminResumeOwner[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [ownerFilter, setOwnerFilter] = useState<OwnerFilter>('ALL');
  const [sort, setSort] = useState<AdminResumeSort>('UPLOADED_AT_DESC');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [downloadingId, setDownloadingId] = useState<number | null>(null);

  const abortRef = useRef<AbortController | null>(null);

  // 关键词输入防抖，触发查询时回到第一页
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(keywordInput.trim());
      setPage(1);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [keywordInput]);

  const loadData = useCallback(async (silent = false) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    if (!silent) setLoading(true);

    try {
      const [pageData, statsData] = await Promise.all([
        adminApi.queryResumes({
          keyword: keyword || undefined,
          analyzeStatus: statusFilter === 'ALL' ? undefined : statusFilter,
          userId: ownerFilter === 'ALL' ? undefined : ownerFilter,
          sort,
          page,
          size: pageSize,
        }, controller.signal),
        adminApi.getStatistics(),
      ]);
      if (controller.signal.aborted) return;

      setLoadError('');
      setStats(statsData);
      setTotal(pageData.total);

      const serverTotalPages = Math.max(1, pageData.totalPages);
      setTotalPages(serverTotalPages);
      if (page > serverTotalPages) {
        setPage(serverTotalPages);
        return;
      }
      setResumes(pageData.items);
    } catch (err) {
      if (controller.signal.aborted) return;
      console.error('加载全部简历失败', err);
      if (!silent) setLoadError(err instanceof Error ? err.message : '加载简历失败');
    } finally {
      if (!controller.signal.aborted && !silent) setLoading(false);
    }
  }, [keyword, statusFilter, ownerFilter, sort, page, pageSize]);

  useEffect(() => {
    loadData();
    return () => abortRef.current?.abort();
  }, [loadData]);

  // 轮询：有分析中的简历时静默刷新当前页与统计
  const hasAnalyzing = (stats?.analyzingCount ?? 0) > 0
    || resumes.some(resume => isAnalyzing(resume.analyzeStatus));

  useEffect(() => {
    if (!hasAnalyzing) return;
    const timer = window.setInterval(() => loadData(true), 3000);
    return () => window.clearInterval(timer);
  }, [hasAnalyzing, loadData]);

  // 用户筛选项只在页面初始化时加载
  useEffect(() => {
    adminApi.getOwners()
      .then(setOwners)
      .catch(err => console.error('加载用户列表失败', err));
  }, []);

  const hasActiveFilters = keyword.length > 0 || statusFilter !== 'ALL' || ownerFilter !== 'ALL';

  const resetFilters = () => {
    setKeywordInput('');
    setKeyword('');
    setStatusFilter('ALL');
    setOwnerFilter('ALL');
    setSort('UPLOADED_AT_DESC');
    setPage(1);
  };

  const handleStatusChange = (value: StatusFilter) => {
    setStatusFilter(value);
    setPage(1);
  };

  const handleOwnerChange = (value: OwnerFilter) => {
    setOwnerFilter(value);
    setPage(1);
  };

  const handleSortChange = (value: AdminResumeSort) => {
    setSort(value);
    setPage(1);
  };

  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    setPage(1);
  };

  const handleDownload = async (resume: AdminResumeItem) => {
    if (downloadingId !== null) return;
    setDownloadingId(resume.id);
    try {
      const blob = await adminApi.downloadResume(resume.id);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = resume.filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('下载简历失败', error);
      alert(error instanceof Error ? error.message : '下载失败，请重试');
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <motion.div
      className="w-full"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
    >
      {/* 统计卡片 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard label="简历总数" value={stats?.totalResumes ?? 0} icon={FileStack}/>
        <StatCard label="涉及用户" value={stats?.userCount ?? 0} icon={Users}/>
        <StatCard label="分析中" value={stats?.analyzingCount ?? 0} icon={RefreshCw}/>
        <StatCard label="面试记录" value={stats?.interviewCount ?? 0} icon={CheckCircle}/>
      </div>

      {/* 查询表单 */}
      <div className="flex flex-wrap items-center gap-3 mb-6">
        <div
          className="flex items-center gap-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-600 rounded-xl px-4 py-3 flex-1 min-w-[220px] max-w-md focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-primary-100 transition-all">
          <Search className="w-5 h-5 text-slate-400"/>
          <input
            type="text"
            placeholder="搜索简历名称或所属用户..."
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            className="flex-1 outline-none text-slate-700 dark:text-slate-200 placeholder:text-slate-400 bg-transparent"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(event) => handleStatusChange(event.target.value as StatusFilter)}
          className="px-3 py-3 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
          title="按分析状态筛选"
        >
          {STATUS_FILTER_OPTIONS.map(option => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        <select
          value={ownerFilter}
          onChange={(event) => handleOwnerChange(
            event.target.value === 'ALL' ? 'ALL' : Number(event.target.value))}
          className="px-3 py-3 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-primary-500/20 max-w-[200px]"
          title="按归属用户筛选"
        >
          <option value="ALL">全部用户</option>
          {owners.map(owner => (
            <option key={owner.userId} value={owner.userId}>
              {(owner.username ?? '未知用户')}（{owner.resumeCount}）
            </option>
          ))}
        </select>
        <select
          value={sort}
          onChange={(event) => handleSortChange(event.target.value as AdminResumeSort)}
          className="px-3 py-3 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
          title="排序方式"
        >
          {SORT_OPTIONS.map(option => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        {hasActiveFilters && (
          <button
            onClick={resetFilters}
            className="px-4 py-3 text-sm text-slate-500 dark:text-slate-400 hover:text-primary-500 dark:hover:text-primary-400 transition-colors"
          >
            重置
          </button>
        )}
        <button
          onClick={() => loadData()}
          disabled={loading}
          className="flex items-center gap-2 px-4 py-3 bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-200 rounded-xl hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors disabled:opacity-50"
          title="刷新列表"
        >
          <RotateCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`}/>
          刷新
        </button>
      </div>

      {/* 加载状态 */}
      {loading && (
        <div className="text-center py-20">
          <div className="w-10 h-10 border-3 border-slate-200 dark:text-slate-200 border-t-primary-500 rounded-full mx-auto mb-4 animate-spin"/>
          <p className="text-slate-500 dark:text-slate-400">加载中...</p>
        </div>
      )}

      {/* 加载失败 */}
      {!loading && loadError && (
        <div className="text-center py-20 bg-white dark:bg-slate-800 rounded-2xl">
          <AlertCircle className="w-12 h-12 text-red-400 mx-auto mb-4"/>
          <h3 className="text-lg font-semibold text-slate-700 dark:text-slate-300 mb-2">加载失败</h3>
          <p className="text-slate-500 dark:text-slate-400 mb-6">{loadError}</p>
          <button
            onClick={() => loadData()}
            className="px-5 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
          >
            重新加载
          </button>
        </div>
      )}

      {/* 空状态 */}
      {!loading && !loadError && resumes.length === 0 && (
        <motion.div
          className="text-center py-20 bg-white dark:bg-slate-800 rounded-2xl"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
        >
          <div className="text-6xl mb-6">📄</div>
          <h3 className="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">
            {hasActiveFilters ? '没有匹配的简历' : '暂无简历数据'}
          </h3>
          <p className="text-slate-500 dark:text-slate-400">
            {hasActiveFilters ? '尝试调整搜索关键词或筛选条件' : '平台上还没有用户上传简历'}
          </p>
        </motion.div>
      )}

      {/* 表格 */}
      {!loading && !loadError && resumes.length > 0 && (
        <>
          <motion.div
            className="bg-white dark:bg-slate-800 rounded-2xl shadow-sm overflow-hidden"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
          >
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                <tr className="bg-slate-50 dark:bg-slate-700/50 border-b border-slate-100 dark:border-slate-600">
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">简历名称</th>
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">所属用户</th>
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">文件大小</th>
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">上传日期</th>
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">分析状态</th>
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">AI 评分</th>
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">面试次数</th>
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">访问次数</th>
                  <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">下载文件</th>
                </tr>
                </thead>
                <tbody>
                <AnimatePresence>
                  {resumes.map((resume, index) => (
                    <motion.tr
                      key={resume.id}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: index * 0.03 }}
                      onClick={() => navigate(ROUTES.adminResumeDetail(resume.id))}
                      title="查看简历详情"
                      className="border-b border-slate-100 dark:border-slate-700 last:border-0 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors cursor-pointer"
                    >
                      <td className="px-6 py-5">
                        <div className="flex items-center gap-3">
                          <div
                            className="w-10 h-10 bg-primary-50 dark:bg-primary-900/30 rounded-xl flex items-center justify-center text-primary-500 dark:text-primary-400 flex-shrink-0">
                            <FileStack className="w-5 h-5"/>
                          </div>
                          <span className="font-medium text-slate-800 dark:text-white">{resume.filename}</span>
                        </div>
                      </td>
                      <td className="px-6 py-5">
                        <div className="flex items-center gap-2">
                          <div
                            className="w-7 h-7 rounded-full bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center text-white text-xs font-semibold flex-shrink-0">
                            {(resume.username ?? '?').slice(0, 1).toUpperCase()}
                          </div>
                          <span className="text-slate-600 dark:text-slate-300">
                            {resume.username ?? '未知用户'}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-5 text-slate-500 dark:text-slate-400 whitespace-nowrap">{formatFileSize(resume.fileSize)}</td>
                      <td className="px-6 py-5 text-slate-500 dark:text-slate-400 whitespace-nowrap">{formatDateOnly(resume.uploadedAt)}</td>
                      <td className="px-6 py-5">
                        <div className="flex items-center gap-2">
                          <AnalyzeStatusIcon status={resume.analyzeStatus}/>
                          <span className="text-sm text-slate-600 dark:text-slate-300 whitespace-nowrap">
                            {getAnalyzeStatusText(resume.analyzeStatus)}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-5">
                        {resume.analyzeStatus === 'COMPLETED' && resume.latestScore !== undefined && resume.latestScore !== null ? (
                          <span className={`inline-flex px-3 py-1 rounded-full text-sm font-bold ${getScoreColor(resume.latestScore)}`}>
                            {resume.latestScore}
                          </span>
                        ) : isAnalyzing(resume.analyzeStatus) ? (
                          <span className="text-blue-500 dark:text-blue-400 text-sm">生成中...</span>
                        ) : resume.analyzeStatus === 'FAILED' ? (
                          <span className="text-red-500 dark:text-red-400 text-sm" title={resume.analyzeError}>失败</span>
                        ) : (
                          <span className="text-slate-400 dark:text-slate-500">-</span>
                        )}
                      </td>
                      <td className="px-6 py-5">
                        <span className={`inline-flex px-3 py-1 rounded-full text-sm font-medium ${
                          resume.interviewCount > 0
                            ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400'
                            : 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-300'
                        }`}>
                          {resume.interviewCount} 次
                        </span>
                      </td>
                      <td className="px-6 py-5 text-slate-500 dark:text-slate-400">{resume.accessCount}</td>
                      <td className="px-6 py-5">
                        <button
                          onClick={(event) => {
                            event.stopPropagation();
                            handleDownload(resume);
                          }}
                          disabled={downloadingId !== null}
                          title="下载原始简历文件"
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-primary-500 dark:text-primary-400 bg-primary-50 dark:bg-primary-900/30 hover:bg-primary-100 dark:hover:bg-primary-900/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          {downloadingId === resume.id
                            ? <RefreshCw className="w-4 h-4 animate-spin"/>
                            : <Download className="w-4 h-4"/>}
                          下载
                        </button>
                      </td>
                    </motion.tr>
                  ))}
                </AnimatePresence>
                </tbody>
              </table>
            </div>
          </motion.div>

          <Pagination
            page={page}
            pageSize={pageSize}
            total={total}
            totalPages={totalPages}
            onPageChange={setPage}
            onPageSizeChange={handlePageSizeChange}
          />
        </>
      )}
    </motion.div>
  );
}

function StatCard({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: number;
  icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl p-5 shadow-sm flex items-center gap-4">
      <div className="w-11 h-11 rounded-xl bg-primary-50 dark:bg-primary-900/30 text-primary-500 dark:text-primary-400 flex items-center justify-center flex-shrink-0">
        <Icon className="w-5 h-5"/>
      </div>
      <div className="min-w-0">
        <p className="text-sm text-slate-500 dark:text-slate-400 truncate">{label}</p>
        <p className="text-xl font-bold text-slate-800 dark:text-white">{value}</p>
      </div>
    </div>
  );
}

interface PaginationProps {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}

function Pagination({
  page,
  pageSize,
  total,
  totalPages,
  onPageChange,
  onPageSizeChange,
}: PaginationProps) {
  if (total === 0) return null;
  const from = (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, total);

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 mt-4 px-1 text-sm">
      <div className="flex items-center gap-2 text-slate-500 dark:text-slate-400">
        <span>
          第 <span className="font-semibold text-slate-700 dark:text-slate-200">{from}-{to}</span> /
          共 <span className="font-semibold text-slate-700 dark:text-slate-200">{total}</span> 份
        </span>
        <span className="text-slate-300 dark:text-slate-600">|</span>
        <span>每页</span>
        <select
          value={pageSize}
          onChange={event => onPageSizeChange(parseInt(event.target.value, 10))}
          className="px-2 py-1 rounded border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
        >
          {PAGE_SIZE_OPTIONS.map(size => (
            <option key={size} value={size}>{size}</option>
          ))}
        </select>
      </div>
      <div className="flex items-center gap-1">
        <PaginationButton disabled={page <= 1} onClick={() => onPageChange(1)} title="第一页">«</PaginationButton>
        <PaginationButton disabled={page <= 1} onClick={() => onPageChange(page - 1)} title="上一页">‹</PaginationButton>
        <span className="px-3 text-slate-700 dark:text-slate-200">{page} / {totalPages}</span>
        <PaginationButton disabled={page >= totalPages} onClick={() => onPageChange(page + 1)} title="下一页">›</PaginationButton>
        <PaginationButton disabled={page >= totalPages} onClick={() => onPageChange(totalPages)} title="最后一页">»</PaginationButton>
      </div>
    </div>
  );
}

function PaginationButton({
  children,
  disabled,
  onClick,
  title,
}: {
  children: React.ReactNode;
  disabled: boolean;
  onClick: () => void;
  title: string;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title}
      className="w-8 h-8 rounded border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
    >
      {children}
    </button>
  );
}
