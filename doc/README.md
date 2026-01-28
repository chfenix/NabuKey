# NabuKey 开发文档

本目录包含 NabuKey 项目的所有开发文档。

## 📚 文档列表

### 项目规划

- **[开发路线图](roadmap.md)** - 项目功能规划总览和优先级排序
- **[功能详细设计](features/)** - 各个功能模块的详细技术文档
  - [本地 STT](features/local_stt.md)
  - [本地 LLM](features/local_llm.md)
  - [屏幕管理](features/screen_management.md)
  - ...更多请见 features 目录

### 核心系统文档

- **[表情系统开发文档](expression_system.md)** - 表情动画系统的完整架构说明和开发指南
- **[表情系统快速参考](expression_quick_reference.md)** - 表情系统的快速参考指南

### 项目规则

项目的编译、部署和 Git 工作流规则请参考：
- **[项目开发规则](../.agent/project_rules.md)** - 位于 `.agent/project_rules.md`

## 📖 文档使用指南

### 新手入门
1. 先阅读 [项目开发规则](../.agent/project_rules.md) 了解项目结构和编译流程
2. 阅读 [表情系统开发文档](expression_system.md) 了解表情系统架构

### 日常开发
- 添加新表情时，参考 [表情系统快速参考](expression_quick_reference.md)
- 遇到编译问题时，查看 [项目开发规则](../.agent/project_rules.md)

### 维护更新
- 添加新功能时，请在对应文档中更新说明
- 修改架构时，请更新相关文档的架构图和示例代码

## 🗂️ 文档组织原则

- **`.agent/project_rules.md`**: 项目级别的规则和配置（编译、部署、Git 等）
- **`doc/`**: 功能模块的开发文档（表情系统、语音识别等）

## 📝 文档编写规范

1. **使用 Markdown 格式**
2. **包含代码示例** - 所有示例代码应该可以直接使用
3. **保持更新** - 代码变更时同步更新文档
4. **添加日期** - 在文档底部标注最后更新日期

---

**最后更新**: 2026-01-27
