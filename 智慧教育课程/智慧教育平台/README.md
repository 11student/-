# 智慧教育课程平台

本项目面向教师、学生与学校管理员，规划将课程建设、课前准备、PPT 授课与同步跟课、课堂活动、课后作业和学情反馈整合为连续的教学工作台。正式开发的项目根目录为本目录。

**当前状态：已接入若依后端基础工程和原生登录接口，静态联调登录页已打包进后端并通过 Maven 构建；完整 Vue 前端、教学业务模块和数据库联调尚未完成。** 版本、接口、环境变量和验证范围见 [后端工程说明](backend/README.md)。

## 教学业务闭环

课程空间与教学班授权 → 资源及知识点建设 → 备课与预习 → 开始课堂、演示 PPT、学生同步跟页 → 签到、随堂题与课堂小作业 → 课后作业与批改 → 教师确认并发布成绩 → 错题、补学与学情反馈 → 下一次备课草稿。

以上均为规划能力。PPT 状态同步与音视频直播分别评审；AI 建议不能直接成为正式成绩。课堂小作业与课后作业规划共用提交、批改和评价能力。

## 需求与范围依据

- [原始需求分析](../智慧教育课程需求分析.docx)：需求编号、业务约束和验收依据，保留于外层工作区。
- [功能架构优化方案 V3.0](../功能架构优化方案.md)：当前三端信息架构与教学工作流设计基线，保留于外层工作区。
- [精简架构与数据约束](docs/architecture.md)：上述来源的工程映射，不替代原始成果。

原需求的 P0/P1/P2 与架构方案的 M0/M1/M2 不是同一套分级。同步课堂、自建课程及部分 AI 功能的优先级差异必须经过范围评审，不能将建议当作已批准交付承诺。

## 技术架构规划

- 后端已接入若依 RuoYi-Vue 3.9.2 的 Spring Boot 3 分支，多模块工程保留原有管理、公共、安全框架与系统能力；`ruoyi-education` 仍是规划中的教学业务模块。
- 当前 [静态登录页](frontend/login.html)已连接若依身份接口；后续采用与所选后端版本匹配的一套 Vue 前端工程，以角色和权限组织三端页面，共享登录、请求、路由权限、公共组件与业务 API。
- 数据库工作统一置于 `sql/`；教学业务演示库已由数据库分支准备，若依登录所需系统表与教学占位用户 ID 的对应仍待核对，当前后端尚未完成数据库联调。
- 课件转换、实时同步、文件存储与 AI 适配均为后续技术评审内容，暂不指定服务或协议实现。
- 后端当前为 Java 17 编译目标、Spring Boot 3.5.16 与 Maven 工程；前端版本和数据库迁移工具仍待实际工程接入与评审。

## 项目目录

```text
智慧教育平台/
├── README.md
├── AGENTS.md
├── CONTRIBUTING.md
├── backend/
│   ├── README.md
│   ├── pom.xml
│   ├── LICENSE
│   ├── ruoyi-admin/  ruoyi-common/  ruoyi-framework/
│   ├── ruoyi-system/  ruoyi-quartz/  ruoyi-generator/
│   └── ruoyi-education/
│       ├── README.md
│       └── src/main/java/com/ruoyi/education/
│           ├── course/       ├── resource/    ├── prepare/
│           ├── classroom/    ├── activity/    ├── assignment/
│           ├── learning/     ├── analysis/    └── ai/
├── frontend/
│   ├── README.md
│   ├── login.html  login.js  logo.png
│   └── src/
│       ├── views/education/{teacher,student,admin}/
│       ├── api/education/
│       └── components/education/
├── sql/
│   ├── README.md
│   └── upstream/ruoyi/  # 上游系统表与调度表脚本，尚未执行
├── docs/
│   ├── architecture.md
│   ├── api-contracts.md
│   ├── development-plan.md
│   ├── migration-report.md
│   ├── historical-inventory.csv
│   ├── backup-verification.csv
│   └── workspace-verification.json
└── prototypes/README.md
```

`ruoyi-education` 领域包与前端页面目录为明确分工而预留，目前为空；`com.ruoyi.education` 为规划包根，接入教学模块时随实际工程扫描配置校准。上游基础模块已导入并可构建，教学业务尚无实现。

## 三端职责

| 端 | 主要职责 | 权限边界 |
|---|---|---|
| 教师 | 课程资源、备课、预习、授课、活动、批改、发布评价和教学改进 | 仅操作获授权课程与教学班，正式成绩变更留痕 |
| 学生 | 待办、授权资源、预习、同步课堂、提交作业、查看反馈和补学 | 仅访问本人及本班授权内容，不提前获取答案 |
| 管理员 | 组织人员、正式课程、教学班、教学授权、资源治理和运行反馈 | 教务与超级管理员权限分开；默认不能修改教师已确认成绩 |

## 模块划分与协作入口

`ruoyi-education` 内规划九个领域：`course`、`resource`、`prepare`、`classroom`、`activity`、`assignment`、`learning`、`analysis`、`ai`。职责、依赖与调用边界见 [后端规划](backend/README.md)；三端页面与公共能力归属见 [前端规划](frontend/README.md)。

- 开发代理先读 [AGENTS.md](AGENTS.md)，团队成员先读 [CONTRIBUTING.md](CONTRIBUTING.md)。
- 前后端联调从 [接口协作约定](docs/api-contracts.md) 开始。
- 数据库变更遵守 [SQL 工作区规范](sql/README.md)，原型登记遵守 [原型工作区规范](prototypes/README.md)。
- 历史盘点、完整备份、迁移校验与待确认清理项见 [迁移报告](docs/migration-report.md)。

## 后续开发阶段

1. 为后端准备独立测试库，初始化上游系统表，与 Redis 一起完成登录、验证码、用户信息与权限路由联调。
2. 确认并接入匹配的唯一前端工程，让静态登录原型使用真实接口和后端权限。
3. 完成领域模型、权限矩阵、接口契约与教学数据库设计评审，再注册 `ruoyi-education` 模块。
4. 按教学闭环推进课程与资源、同步课堂、活动、预习、作业和学情；AI 及增强能力按已批准范围排期。
5. 验证跨班权限、断线恢复、并发提交、教师确认成绩、真实设备体验，再进入试点。

详细门槛与待决事项见 [开发路线](docs/development-plan.md)。当前仅有上游基础身份能力和构建验证，没有教学业务实现或实际登录运行验证。
