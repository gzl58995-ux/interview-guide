# 简历解析提示词（按求职方向路由）

每个方向一个目录，固定包含 `system.st` 与 `user.st`，模板变量只允许 `{resumeText}`。

新增一个求职方向：
1. 新建目录 `prompts/resume-analysis/<direction-kebab>/`，放入 `system.st` 和 `user.st`。
2. 在 `application.yml` 的 `app.resume.analysis.prompt-routes` 注册路由：键为方向代码（与前端 `JOB_DIRECTION_OPTIONS` 一致）。
3. 若该方向要作为默认方向，修改 `app.resume.analysis.default-direction`。

路由键即上传接口 `jobDirection` 的取值；未注册方向上传时返回「不支持的求职方向」。
