import assert from 'node:assert/strict';
import test from 'node:test';

import {
  FEEDBACK_CATEGORIES,
  FEEDBACK_STATUS_LABELS,
  categoryLabel,
  normalizePagePath,
  statusLabel,
  validateFeedbackContent,
} from './feedbackForm.ts';

test('四个分类与两个状态都有中文标签', () => {
  assert.deepEqual(
    FEEDBACK_CATEGORIES.map(item => item.value),
    ['BUG', 'SUGGESTION', 'EXPERIENCE', 'OTHER'],
  );
  for (const item of FEEDBACK_CATEGORIES) {
    assert.ok(item.label.length > 0);
    assert.ok(item.className.length > 0);
  }
  assert.deepEqual(FEEDBACK_STATUS_LABELS, { PENDING: '待处理', PROCESSED: '已处理' });
});

test('未知枚举值兜底显示原始值', () => {
  assert.equal(categoryLabel('FUTURE'), 'FUTURE');
  assert.equal(statusLabel('ARCHIVED'), 'ARCHIVED');
  assert.equal(categoryLabel('BUG'), '功能异常');
  assert.equal(statusLabel('PENDING'), '待处理');
});

test('正文校验覆盖空、空白与 5-1000 字边界', () => {
  assert.notEqual(validateFeedbackContent(''), null);
  assert.notEqual(validateFeedbackContent('   '), null);
  assert.notEqual(validateFeedbackContent('abcd'), null);
  assert.equal(validateFeedbackContent('abcde'), null);
  assert.equal(validateFeedbackContent('a'.repeat(1000)), null);
  assert.notEqual(validateFeedbackContent('a'.repeat(1001)), null);
  assert.equal(validateFeedbackContent('  abcde  '), null);
});

test('页面路径为空返回 undefined，超长截断到 512', () => {
  assert.equal(normalizePagePath(''), undefined);
  assert.equal(normalizePagePath('/history'), '/history');
  assert.equal(normalizePagePath('/' + 'a'.repeat(600))?.length, 512);
});
