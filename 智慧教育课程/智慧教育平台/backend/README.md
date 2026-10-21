# 后端架构规划

采用若依 RuoYi + Spring Boot 多模块架构，后端正式工作区统一为本目录。**尚未接入任何若依源码，本轮没有 Maven 工程、启动类、配置或业务实现。** 下表是职责和依赖规划，不是现存可构建模块清单。

## 基础模块与依赖

| 模块 | 规划职责 | 依赖与集成边界 |
|---|---|---|
| ruoyi-common | 上游公共类型、工具、通用契约 | 不依赖教学业务；禁止塞入课程/作业专属规则 |
| ruoyi-system | 上游用户、角色、菜单、部门及系统管理服务 | 复用身份与组织能力，不承载教学班与正式成绩业务 |
| ruoyi-framework | 上游安全、Web 配置、权限与基础设施整合 | 沿用选定版本依赖；不能反向依赖 ruoyi-education |
| ruoyi-admin | 上游应用入口、管理端装配和对外接入 | 装配基础框架及教育业务，不承担九领域的核心逻辑 |
| ruoyi-education | 独立的教学业务 Maven 模块（未来接入） | 计划依赖 common；仅在需要用户/组织服务时依赖 system；不依赖 admin |
| ruoyi-quartz 等 | 可能存在的调度等附加能力 | 根据真实版本和任务需要决定；现阶段不建立空模块 |
| ruoyi-generator 等 | 可能存在的开发辅助能力 | 根据实际版本和团队需求评审；不得据此批量生成无用占位代码 |

已核对 [若依官方 RuoYi-Vue 仓库](https://github.com/yangzongzhuan/RuoYi-Vue) 的基础模块目录。该来源仅供后续选型核对，不代表本项目已选定其当前分支或版本。

规划装配方向：`ruoyi-admin → ruoyi-framework / ruoyi-education`，`ruoyi-education → ruoyi-common`，必要时 `ruoyi-education → ruoyi-system`。上游基础模块之间的实际依赖以导入版本为准；不在本轮伪造其 POM。教育模块如需安全框架适配，优先在入口/适配边界完成，不创建 framework 与 education 的循环依赖。

Controller 所属位置在真实若依接入时统一：若上游约定控制器集中于 admin，则入口放 admin、业务留在 education；如采用业务模块内控制器，则先核对组件扫描与安全配置。两处不得重复暴露同一接口。

## 教育业务领域

九个领域属于 **同一个 `ruoyi-education` 模块内的业务包**，不拆成九个独立 Maven 模块。当前已经预留以下空目录，没有 Controller、Service、Mapper 或 Entity。

```text
ruoyi-education/
├── README.md
└── src/main/java/com/ruoyi/education/
    ├── course/
    ├── resource/
    ├── prepare/
    ├── classroom/
    ├── activity/
    ├── assignment/
    ├── learning/
    ├── analysis/
    └── ai/
```

包根 `com.ruoyi.education` 为目录规划，最终跟随真实工程包名与扫描配置确认。只有产生实际实现需求时才新增领域内分层和 Mapper XML、测试、配置等路径。

| 领域 | 负责对象和职责 | 主要协作与边界 |
|---|---|---|
| course | 课程空间、正式课程关联、教学班、学期、教师与学生成员、章节、知识点 | 提供内容归属和教学授权；账号和部门复用 system，不重建登录体系 |
| resource | 课件、视频、资料、题库、评分标准、资源版本与发布关系、转换状态 | 引用 course 归属；提供带版本资源与题目快照；不重复管理各阶段任务 |
| prepare | 备课方案、预习发布与完成编排、课堂活动预设 | 引用 course/resource；持有预设配置并经契约发起实例；不自行启动有效签到 |
| classroom | 课堂会话、讲授页码与资源版本、公开投影状态、学生同步与重连 | 引用 course/resource；不持有正式成绩或复制活动提交表；课堂可无备课方案 |
| activity | 签到、实时随堂题、课堂活动实例、开放截止及目标快照 | 引用 course/resource；可关联 classroom，也支持独立活动；小作业委托 assignment |
| assignment | 课堂小作业及课后作业、统一提交版本、批改、教师确认和成绩发布 | 引用 course/resource；提供统一提交与评价能力；AI 仅返回建议，不直接写正式成绩 |
| learning | 学习轨迹、错题、补学任务与学习证据 | 消费预习/活动/作业有效结果，保留来源；重做新增记录、不覆盖原分数 |
| analysis | 班级、学生、知识点聚合和可追溯统计 | 读取授权证据及只读投影；不反向写业务提交或生成无依据综合掌握分 |
| ai | AI 备课建议、课程答疑、作业初评的模型适配与任务记录 | 接收调用方已授权的上下文并复核范围；返回有来源和版本的建议，失败可回退人工 |

## 领域协作规则

允许的主要调用方向为：`resource → course`；`prepare → course/resource`；`classroom → course/resource`；`activity → course/resource/assignment`；`assignment → course/resource/ai`；`learning → 上游业务的公开结果契约`；`analysis → 授权只读证据`。这是设计方向，具体依赖在接口评审时细化。

- course 与 resource 提供归属/可见性契约，上层不得直接读取其 Mapper。
- classroom 负责会话状态；结束课堂需要关闭活动时，由入口用例编排调用课堂和活动服务，避免两领域互相注入。
- prepare 提供备课与模板快照；激活模板由入口用例编排调用 activity，activity 不反向读取 prepare 私有表。
- activity 的小作业入口调用 assignment；assignment 不依赖 activity，使用外部来源标识关联。
- assignment 调用 ai 初评，ai 不回调修改 assignment 的成绩；由 assignment 校验任务结果、保存建议并执行教师确认。
- learning 与 analysis 通过业务服务公开查询或约定的事件/投影获得数据。事件机制、消息中间件及异步方案尚未选定，不把表直接关联查询当作默认公共接口。
- analysis 产生的“下一次备课/补学”建议由入口用例创建草稿，相关领域拥有写入权；不建立分析与业务双向循环。

## 后续接入要求

接入真实若依时记录上游仓库、版本/提交、许可证和模块差异；由真实父 POM 注册教育模块并检查依赖方向、包扫描、Mapper 扫描和权限接入。启动与构建命令只能依据实际工程验证后填写。完整步骤见 [开发路线](../docs/development-plan.md)。
