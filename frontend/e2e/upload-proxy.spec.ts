import { createServer as createHttpServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { expect, test, type Page } from '@playwright/test';
import { createServer, type ViteDevServer } from 'vite';

let upstream: Server;
let devServer: ViteDevServer;
let baseURL: string;
let forwardedHeaders: { host?: string; origin?: string };
let rejectUploads = false;

const selectResumeDirection = async (page: Page, path: string) => {
  if (path === '/upload') {
    await page.getByLabel(/的求职方向/).selectOption('TECH');
  }
};

// 使用真实 Vite 代理，避免 page.route() 绕过 Host / Origin 转发和连接错误。
test.beforeAll(async () => {
  upstream = createHttpServer((req, res) => {
    forwardedHeaders = { host: req.headers.host, origin: req.headers.origin };
    req.resume();
    if (rejectUploads && req.method === 'POST') {
      res.writeHead(403, { 'Content-Type': 'text/plain' });
      res.end('Invalid CORS request');
      return;
    }
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ code: 200, message: 'success', data: {
      resume: { id: 1, analyzeStatus: 'COMPLETED' },
      knowledgeBase: { id: 1, vectorStatus: 'COMPLETED' },
    } }));
  });
  await new Promise<void>(resolve => upstream.listen(0, '127.0.0.1', resolve));
  const port = (upstream.address() as AddressInfo).port;
  devServer = await createServer({
    configFile: './vite.config.ts',
    logLevel: 'silent',
    server: { host: '127.0.0.1', port: 0, open: false,
      proxy: { '/api': { target: `http://127.0.0.1:${port}` } },
    },
  });
  await devServer.listen();
  baseURL = `http://127.0.0.1:${(devServer.httpServer!.address() as AddressInfo).port}`;
});

test.afterAll(async () => {
  await devServer?.close();
  if (upstream?.listening) await new Promise<void>(resolve => upstream.close(() => resolve()));
});

test('开发代理保留同源上传的 Host 和 Origin，避免后端误判跨域', async ({ page, request }) => {
  for (const path of ['/upload', '/knowledgebase/upload']) {
    await page.goto(baseURL + path);
    await page.locator('input[type=file]').setInputFiles({
      name: 'proxy-test.txt', mimeType: 'text/plain', buffer: Buffer.from('代理转发测试'),
    });
    await selectResumeDirection(page, path);
    await page.getByRole('button', { name: '加入上传队列 1 个文件' }).click();
    await expect(page.getByText('已完成 1 个', { exact: true })).toBeVisible();
    expect(forwardedHeaders.origin).toBe(baseURL);
    expect(forwardedHeaders.host).toBe(new URL(baseURL).host);
  }
  // 真正的跨域来源也保持原样，仍由后端现有 CORS 策略决定是否允许。
  await request.post(baseURL + '/api/resumes/upload', { headers: { Origin: 'https://foreign.example' } });
  expect(forwardedHeaders.origin).toBe('https://foreign.example');
  expect(forwardedHeaders.host).toBe(new URL(baseURL).host);
});

test('后端返回非 Result 的 403 时，两种上传页面显示拒绝原因', async ({ page }) => {
  rejectUploads = true;
  try {
    for (const path of ['/upload', '/knowledgebase/upload']) {
      await page.goto(baseURL + path);
      await page.locator('input[type=file]').setInputFiles({
        name: 'cors-test.txt', mimeType: 'text/plain', buffer: Buffer.from('跨域拒绝测试'),
      });
      await selectResumeDirection(page, path);
      await page.getByRole('button', { name: '加入上传队列 1 个文件' }).click();
      await expect(page.getByText('请求被服务端拒绝（403），请检查访问权限或跨域配置', { exact: true })).toBeVisible();
    }
  } finally {
    rejectUploads = false;
  }
});

test('后端未启动时，两种上传页面显示服务连接失败的明确原因', async ({ page }) => {
  await new Promise<void>(resolve => upstream.close(() => resolve()));
  for (const path of ['/upload', '/knowledgebase/upload']) {
    await page.goto(baseURL + path);
    await page.locator('input[type=file]').setInputFiles({
      name: 'offline-test.txt', mimeType: 'text/plain', buffer: Buffer.from('服务离线测试'),
    });
    await selectResumeDirection(page, path);
    await page.getByRole('button', { name: '加入上传队列 1 个文件' }).click();
    await expect(page.getByText('无法连接后端服务，请确认后端已启动后重试', { exact: true })).toBeVisible();
  }
});
