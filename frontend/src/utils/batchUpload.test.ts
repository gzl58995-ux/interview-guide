import assert from 'node:assert/strict';
import test from 'node:test';

import type { BatchUploadItem } from '../types/batchUpload.ts';
import {
  canRetryUpload,
  countReadyItemsMissingCategory,
  KNOWLEDGE_BASE_FILE_POLICY,
  RESUME_FILE_POLICY,
  getFileIdentity,
  MAX_BATCH_FILES,
  MAX_CONCURRENT_UPLOADS,
  RateLimitedUploadQueue,
  selectUploadFiles,
  toBatchUploadStatus,
  UPLOAD_RETRY_COOLDOWN_MS,
  UPLOAD_START_INTERVAL_MS,
  validateUploadFile,
} from './batchUpload.ts';

const delay = (milliseconds: number) => new Promise(resolve => setTimeout(resolve, milliseconds));
const file = (name: string, size = 1024, lastModified = 1) => (
  { name, size, lastModified } as File
);

test('文件校验限制格式、空文件和 50MB 大小', () => {
  assert.equal(validateUploadFile(file('guide.pdf'), KNOWLEDGE_BASE_FILE_POLICY), null);
  assert.equal(validateUploadFile(file('guide.MD'), KNOWLEDGE_BASE_FILE_POLICY), null);
  assert.equal(validateUploadFile(file('empty.txt', 0), KNOWLEDGE_BASE_FILE_POLICY), '文件为空');
  assert.equal(validateUploadFile(file('large.docx', 50 * 1024 * 1024 + 1), KNOWLEDGE_BASE_FILE_POLICY), '文件超过 50MB');
  assert.equal(validateUploadFile(file('archive.zip'), KNOWLEDGE_BASE_FILE_POLICY), '仅支持 PDF、DOCX、DOC、TXT、MD');
});

test('连续选择共用列表去重和 10 个文件上限', () => {
  const firstSelection = selectUploadFiles([], [file('guide.pdf')], KNOWLEDGE_BASE_FILE_POLICY, 1);
  const secondSelection = selectUploadFiles(firstSelection.accepted, [
    file('guide.pdf'),
    ...Array.from({ length: 10 }, (_, index) => file(`${index}.txt`, 1024, index + 2)),
  ], KNOWLEDGE_BASE_FILE_POLICY, 2);

  assert.equal(firstSelection.accepted[0]?.status, 'READY');
  assert.equal(secondSelection.accepted.length, MAX_BATCH_FILES - 1);
  assert.match(secondSelection.rejected[0]!, /已在列表中/);
  assert.match(secondSelection.rejected[secondSelection.rejected.length - 1]!, /单批最多 10 个文件/);
  assert.equal(getFileIdentity(file('guide.pdf')), getFileIdentity(file('guide.pdf')));
});

test('共享队列限制并发、发送节奏并接收运行中追加任务', async () => {
  const queue = new RateLimitedUploadQueue(MAX_CONCURRENT_UPLOADS, 25);
  const starts: number[] = [];
  const order: string[] = [];
  let active = 0;
  let maximumActive = 0;
  const task = (clientId: string) => async () => {
    starts.push(Date.now());
    order.push(clientId);
    active += 1;
    maximumActive = Math.max(maximumActive, active);
    await delay(70);
    active -= 1;
  };

  queue.enqueue('1', task('1'));
  queue.enqueue('2', task('2'));
  queue.enqueue('3', task('3'));
  await delay(10);
  assert.equal(queue.enqueue('4', task('4')), true);
  assert.equal(queue.enqueue('4', task('duplicate')), false);
  await queue.whenIdle();

  assert.equal(maximumActive, MAX_CONCURRENT_UPLOADS);
  assert.deepEqual(order, ['1', '2', '3', '4']);
  starts.slice(1).forEach((startedAt, index) => {
    assert.ok(startedAt - starts[index]! >= 20);
  });
  queue.dispose();
});

test('单项失败不会阻断队列中的后续任务', async () => {
  const queue = new RateLimitedUploadQueue(1, 0);
  const completed: string[] = [];

  queue.enqueue('failed', async () => {
    throw new Error('上传失败');
  });
  queue.enqueue('completed', async () => {
    completed.push('completed');
  });
  await queue.whenIdle();

  assert.deepEqual(completed, ['completed']);
  queue.dispose();
});

test('上传和向量化状态、生产限制保持独立', () => {
  assert.equal(toBatchUploadStatus('PENDING'), 'PENDING');
  assert.equal(toBatchUploadStatus('PROCESSING'), 'PROCESSING');
  assert.equal(toBatchUploadStatus('COMPLETED'), 'COMPLETED');
  assert.equal(toBatchUploadStatus('FAILED'), 'PROCESS_FAILED');
  assert.equal(UPLOAD_START_INTERVAL_MS, 500);
  assert.equal(UPLOAD_RETRY_COOLDOWN_MS, 2000);
  assert.equal(canRetryUpload({ status: 'UPLOAD_FAILED', retryAvailableAt: 3000 }, 2999), false);
  assert.equal(canRetryUpload({ status: 'UPLOAD_FAILED', retryAvailableAt: 3000 }, 3000), true);
});


test('新加入的文件默认未选择分类，等待用户选择', () => {
  const selection = selectUploadFiles([], [file('resume.pdf')], RESUME_FILE_POLICY, 1);
  assert.equal(selection.accepted[0]?.category, '');
});

test('只统计待提交且未选择分类的文件', () => {
  const item = (overrides: Partial<BatchUploadItem>) => ({
    clientId: 'client',
    file: file('resume.pdf'),
    customName: '',
    category: '',
    status: 'READY',
    ...overrides,
  } as BatchUploadItem);

  const items = [
    item({ clientId: 'missing' }),
    item({ clientId: 'chosen', category: 'TECH' }),
    item({ clientId: 'queued', status: 'QUEUED' }),
    item({ clientId: 'uploading', status: 'UPLOADING' }),
  ];

  assert.equal(countReadyItemsMissingCategory(items), 1);
  assert.equal(countReadyItemsMissingCategory([]), 0);
});

test('简历复用同一校验器，但保留 10MB 和自身格式限制', () => {
  assert.equal(validateUploadFile(file('resume.pdf', 10 * 1024 * 1024), RESUME_FILE_POLICY), null);
  assert.equal(validateUploadFile(file('resume.pdf', 10 * 1024 * 1024 + 1), RESUME_FILE_POLICY), '文件超过 10MB');
  assert.equal(validateUploadFile(file('resume.md'), RESUME_FILE_POLICY), '仅支持 PDF、DOCX、DOC、TXT');
  assert.equal(validateUploadFile(file('resume.doc'), RESUME_FILE_POLICY), null);
});
