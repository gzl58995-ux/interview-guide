/**
 * 通用分页响应结构
 * 对应后端 PageResult<T>，page 从 1 开始
 */
export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}
