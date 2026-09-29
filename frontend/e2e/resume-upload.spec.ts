import { expect, test, type Page, type Route } from '@playwright/test';

const file = (name: string) => ({ name, mimeType: 'text/plain', buffer: Buffer.from(`简历测试 ${name}`) });
const row = (page: Page, name: string) => page.locator('section .divide-y > div')
  .filter({ has: page.getByText(name, { exact: true }) });
const success = (route: Route, data: unknown) => route.fulfill({ json: { code: 200, message: 'success', data } });
const result = (id: number, analyzeStatus: string, extra = {}) => ({
  resume: { id, filename: '简历.txt', analyzeStatus },
  storage: { resumeId: id }, enqueueAccepted: true, duplicate: false, message: '', ...extra,
});
const selectDirections = async (page: Page, names: string[], value = 'TECH') => {
  for (const name of names) {
    await page.getByLabel(`${name}的求职方向`).selectOption(value);
  }
};

test('批量简历保留各自分析状态，兼容历史重复响应，并按分析阶段重试', async ({ page }) => {
  let uploads = 0;
  let retries = 0;
  await page.route('**/api/resumes/upload', route => {
    uploads++;
    if (uploads === 1) return success(route, result(1, 'PENDING'));
    if (uploads === 2) return success(route, { storage: { resumeId: 2 }, analysis: { overallScore: 85 }, duplicate: true });
    return success(route, result(3, 'FAILED', { enqueueAccepted: false, message: '简历已保存，但分析任务投递失败，请稍后重试' }));
  });
  await page.route('**/api/resumes/1/detail', route => success(route, { id: 1, analyzeStatus: 'COMPLETED' }));
  await page.route('**/api/resumes/3/detail', route => success(route, { id: 3, analyzeStatus: 'COMPLETED' }));
  await page.route('**/api/resumes/3/reanalyze', route => { retries++; return success(route, null); });
  await page.goto('/upload');
  await page.locator('input[type=file]').setInputFiles([file('new.txt'), file('duplicate.txt'), file('failed.txt')]);
  await expect(page.getByRole('textbox')).toHaveCount(0);
  await selectDirections(page, ['new.txt', 'duplicate.txt', 'failed.txt']);
  await page.getByRole('button', { name: '加入上传队列 3 个文件' }).click();
  await expect(row(page, 'new.txt')).toContainText('已完成');
  await expect(row(page, 'duplicate.txt')).toContainText('已存在');
  await expect(row(page, 'duplicate.txt')).toContainText('已完成');
  await expect(row(page, 'failed.txt')).toContainText('分析任务投递失败');
  await row(page, 'failed.txt').getByTitle('重新分析').click();
  await expect(page.getByText('已完成 3 个', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/upload$/);
  expect(uploads).toBe(3);
  expect(retries).toBe(1);
});

test('简历未选择求职方向时不能加入上传队列', async ({ page }) => {
  const bodies: string[] = [];
  await page.route('**/api/resumes/upload', route => {
    bodies.push(route.request().postDataBuffer()!.toString());
    return success(route, result(1, 'COMPLETED'));
  });
  await page.goto('/upload');
  await page.locator('input[type=file]').setInputFiles(file('direction.txt'));
  const enqueue = page.getByRole('button', { name: '加入上传队列 1 个文件' });
  const direction = page.getByLabel('direction.txt的求职方向');
  await expect(direction).toHaveValue('');
  await expect(enqueue).toBeDisabled();
  await expect(page.getByText('还有 1 个文件未选择求职方向')).toBeVisible();
  await direction.selectOption('SALES_BD');
  await expect(enqueue).toBeEnabled();
  await expect(page.getByText('还有 1 个文件未选择求职方向')).toHaveCount(0);
  await enqueue.click();
  await expect(row(page, 'direction.txt')).toContainText('已完成');
  expect(bodies.join('\n')).toContain('name="jobDirection"');
  expect(bodies.join('\n')).toContain('SALES_BD');
});

test('上传期间追加共用双并发队列，上传失败可单独重试', async ({ page }) => {
  let active = 0;
  let maxActive = 0;
  let failedOnce = false;
  const starts: number[] = [];
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/resumes/upload', async route => {
    const name = route.request().postDataBuffer()!.toString().match(/filename="([^"]+)"/)![1];
    starts.push(Date.now());
    active++;
    maxActive = Math.max(active, maxActive);
    await gate;
    active--;
    if (name === 'retry.txt' && !failedOnce) {
      failedOnce = true;
      return route.fulfill({ json: { code: 8001, message: '请求过于频繁，请稍后再试', data: null } });
    }
    return success(route, result(starts.length, 'COMPLETED'));
  });
  try {
    await page.goto('/upload');
    await page.locator('input[type=file]').setInputFiles([file('first.txt'), file('retry.txt'), file('third.txt')]);
    await selectDirections(page, ['first.txt', 'retry.txt', 'third.txt']);
    await page.getByRole('button', { name: '加入上传队列 3 个文件' }).click();
    await expect.poll(() => starts.length).toBe(2);
    await expect(row(page, 'third.txt')).toContainText('等待上传');
    await page.locator('input[type=file]').setInputFiles(file('append.txt'));
    await selectDirections(page, ['append.txt']);
    await page.getByRole('button', { name: '加入上传队列 1 个文件' }).click();
    await expect(row(page, 'append.txt')).toContainText('等待上传');
    expect(starts.length).toBe(2);
    release();
    await expect(row(page, 'retry.txt')).toContainText('上传失败');
    await expect(row(page, 'retry.txt').getByTitle('2 秒后可重试')).toBeDisabled();
    await expect(row(page, 'append.txt')).toContainText('已完成');
    await row(page, 'retry.txt').getByTitle('重新上传', { exact: true }).click();
    await expect(page.getByText('已完成 4 个', { exact: true })).toBeVisible();
    expect(starts.length).toBe(5);
    expect(maxActive).toBe(2);
    for (let i = 1; i < starts.length; i++) expect(starts[i] - starts[i - 1]).toBeGreaterThanOrEqual(400);
  } finally {
    release();
  }
});

test('简历校验 10MB、格式和空文件，连续选择最多 10 个', async ({ page }) => {
  await page.goto('/upload');
  await page.locator('input[type=file]').setInputFiles([
    { ...file('large.txt'), buffer: Buffer.alloc(10 * 1024 * 1024 + 1) },
    file('unsupported.md'), { ...file('empty.txt'), buffer: Buffer.alloc(0) }, file('valid.txt'),
  ]);
  await expect(page.getByText(/large.txt（文件超过 10MB）/)).toBeVisible();
  await expect(page.getByText(/unsupported.md（仅支持/)).toBeVisible();
  await expect(page.getByText(/empty.txt（文件为空）/)).toBeVisible();
  await expect(page.getByRole('heading', { name: '文件列表（1/10）' })).toBeVisible();
  await page.locator('input[type=file]').setInputFiles(Array.from({ length: 10 }, (_, i) => file(`${i}.txt`)));
  await expect(page.getByRole('heading', { name: '文件列表（10/10）' })).toBeVisible();
  await expect(page.locator('input[type=file]')).toBeDisabled();
  await expect(page.getByText('9.txt（单批最多 10 个文件）')).toBeVisible();
});

test('慢分析查询不阻塞其他简历，失败原因来自详情接口', async ({ page }) => {
  let uploads = 0;
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/resumes/upload', route => success(route, result(++uploads, 'PENDING')));
  await page.route('**/api/resumes/1/detail', async route => {
    await gate;
    await success(route, { id: 1, analyzeStatus: 'COMPLETED' });
  });
  await page.route('**/api/resumes/2/detail', route => success(route, {
    id: 2, analyzeStatus: 'FAILED', analyzeError: '分析服务暂时不可用',
  }));
  try {
    await page.goto('/upload');
    await page.locator('input[type=file]').setInputFiles([file('slow.txt'), file('failed.txt')]);
    await selectDirections(page, ['slow.txt', 'failed.txt']);
    await page.getByRole('button', { name: '加入上传队列 2 个文件' }).click();
    await expect(row(page, 'failed.txt')).toContainText('分析服务暂时不可用');
    await expect(row(page, 'slow.txt')).toContainText('等待分析');
    release();
    await expect(row(page, 'slow.txt')).toContainText('已完成');
  } finally {
    release();
  }
});

test('已有历史分析时仍尊重当前处理状态，重新分析失败可再次尝试', async ({ page }) => {
  let retries = 0;
  await page.route('**/api/resumes/upload', route => success(route, result(7, 'FAILED', {
    duplicate: true, analysis: { overallScore: 90 },
  })));
  await page.route('**/api/resumes/7/detail', route => success(route, {
    id: 7, analyzeStatus: retries > 1 ? 'COMPLETED' : 'FAILED', analyzeError: '当前分析失败',
  }));
  await page.route('**/api/resumes/7/reanalyze', route => {
    retries++;
    return retries === 1
      ? route.fulfill({ json: { code: 8001, message: '请稍后再试', data: null } })
      : success(route, null);
  });
  await page.goto('/upload');
  await page.locator('input[type=file]').setInputFiles(file('history.txt'));
  await selectDirections(page, ['history.txt']);
  await page.getByRole('button', { name: '加入上传队列 1 个文件' }).click();
  await expect(row(page, 'history.txt')).toContainText('当前分析失败');
  await row(page, 'history.txt').getByTitle('重新分析').click();
  await expect(row(page, 'history.txt')).toContainText('请稍后再试');
  await row(page, 'history.txt').getByTitle('重新分析').click();
  await expect(row(page, 'history.txt')).toContainText('已完成');
});

test('简历支持键盘选择和多文件拖拽', async ({ page }) => {
  await page.goto('/upload');
  await page.getByLabel('选择简历文件').focus();
  const chooser = page.waitForEvent('filechooser');
  await page.keyboard.press('Enter');
  await (await chooser).setFiles(file('keyboard.txt'));
  const dataTransfer = await page.evaluateHandle(() => {
    const data = new DataTransfer();
    data.items.add(new File(['简历一'], 'drag-1.txt', { type: 'text/plain' }));
    data.items.add(new File(['简历二'], 'drag-2.txt', { type: 'text/plain' }));
    return data;
  });
  await page.locator('label').filter({ has: page.locator('input[type=file]') })
    .dispatchEvent('drop', { dataTransfer });
  await expect(page.getByRole('heading', { name: '文件列表（3/10）' })).toBeVisible();
});
