import assert from 'node:assert/strict';
import test from 'node:test';
import { buildVoiceWebSocketUrl } from './voiceWebSocketUrl.ts';

test('开发环境沿用当前域名和端口，不固定 localhost:8080', () => {
  assert.equal(buildVoiceWebSocketUrl(42, '', 'http://127.0.0.1:4186/voice-interview'),
    'ws://127.0.0.1:4186/ws/voice-interview/42');
  assert.equal(buildVoiceWebSocketUrl(42, '', 'http://192.168.1.10:5173/'),
    'ws://192.168.1.10:5173/ws/voice-interview/42');
});

test('HTTPS 部署自动使用 WSS', () => {
  assert.equal(buildVoiceWebSocketUrl(42, '', 'https://interview.example/voice-interview'),
    'wss://interview.example/ws/voice-interview/42');
});

test('显式 API 地址和相对 API 地址与 REST 请求保持一致', () => {
  assert.equal(buildVoiceWebSocketUrl(42, 'https://api.example:8443', 'https://interview.example/'),
    'wss://api.example:8443/ws/voice-interview/42');
  assert.equal(buildVoiceWebSocketUrl(42, '/backend/', 'https://interview.example/voice-interview'),
    'wss://interview.example/backend/ws/voice-interview/42');
  assert.equal(buildVoiceWebSocketUrl(42, 'https://api.example/backend', 'https://interview.example/'),
    'wss://api.example/backend/ws/voice-interview/42');
});

test('携带登录 Token 时附加 token 查询参数', () => {
  assert.equal(buildVoiceWebSocketUrl(42, '', 'http://127.0.0.1:4186/voice-interview', 'abc.def.ghi'),
    'ws://127.0.0.1:4186/ws/voice-interview/42?token=abc.def.ghi');
  assert.equal(buildVoiceWebSocketUrl(42, '/backend', 'https://interview.example/', 'tok+en/value'),
    'wss://interview.example/backend/ws/voice-interview/42?token=tok%2Ben%2Fvalue');
});
