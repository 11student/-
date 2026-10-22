# 数据库工作区规范

本目录统一管理数据库设计与脚本。[V3.0 M0 数据库设计](../docs/database-design.md)对应 44 张教育业务表；已在独立 MySQL 8.0.46 实例验证建表和关键约束。共享环境部署状态、若依版本、身份映射和正式迁移工具仍待确认。

## 空数据库执行顺序

先由部署负责人按项目规范创建并备份目标数据库，确认 MySQL 8.0.16+、InnoDB、`utf8mb4_0900_ai_ci`、UTC 会话时区及执行账号权限。然后在**空数据库**依次执行：

1. [mysql-v3-m0-core-schema.sql](mysql-v3-m0-core-schema.sql)：学期、课程、教学班、任课/学生关系、资源与按班发布。
2. [mysql-v3-m0-teaching-schema.sql](mysql-v3-m0-teaching-schema.sql)：课次、题库、备课、课堂、活动、统一作业、成绩、学习证据、统计和 AI 基础问答。

例如在 `智慧教育平台/` 目录使用 Git Bash 等支持 `<` 输入重定向的终端、已选定数据库 `education_db` 时：

```bash
mysql --default-character-set=utf8mb4 --database=education_db < sql/mysql-v3-m0-core-schema.sql
mysql --default-character-set=utf8mb4 --database=education_db < sql/mysql-v3-m0-teaching-schema.sql
```

脚本不创建数据库、不包含 `USE` 或 `DROP`，也不创建若依系统表。`user_id`、`organization_id` 与若依账号/部门的对应关系必须在项目基础工程选定后确认。第二段脚本依赖第一段，当前没有自动回滚脚本；部署失败时应依据部署前备份恢复空库，再修复脚本重跑。

[verify-v3-m0-invariants.sql](verify-v3-m0-invariants.sql)只允许在**独立、空白、可丢弃的测试库**中运行。它创建验证用存储过程，写入合成数据、断言关键非法写入被拒绝、回滚数据并删除过程；不在共享或生产库执行。

## 给 `zhijiao_m0` 加载演示数据

[demo-v3-m0-seed.sql](demo-v3-m0-seed.sql)面向已完成上述两段建表脚本的 `zhijiao_m0`。它一次写入 44 张表，共 87 条虚构记录，形成课程空间、A/B 两个教学班、课件与视频发布、课堂同步、签到与随堂题、课前预习/课堂小作业/课后作业、提交与成绩、学习统计及 AI 引用的示例关系。学生 ID `930000000101` 至 `930000000104`，教师 ID `930000000001`，组织 ID `920000000001`，均为逻辑占位值。

在 MySQL Workbench 中用 **File → Open SQL Script** 打开此文件，确认连接到预期的 MySQL 实例，清除编辑器选区，然后执行整个脚本。首行附近已有 `USE zhijiao_m0;`；无需重跑建表脚本。结尾结果应显示 `terms=1`、`classes=2`、`students=4`、`assignments=3`、`submissions=2`、`grades=2`、`ai_tasks=1`（前提是导入前所有表为空）。脚本使用临时存储过程与事务，执行账号需有 `CREATE ROUTINE`、`ALTER ROUTINE` 和数据写入权限。若该库已有其他数据，先备份并核对 ID 冲突，再执行。

示例查询：

```sql
USE zhijiao_m0;
SELECT tc.name, COUNT(cs.id) AS student_count
FROM edu_teaching_class tc
LEFT JOIN edu_class_student cs ON cs.teaching_class_id = tc.id
GROUP BY tc.id, tc.name;

SELECT a.title, COUNT(at.id) AS target_count,
       COUNT(s.id) AS submission_count, COUNT(gp.id) AS published_grade_count
FROM edu_assignment a
LEFT JOIN edu_assignment_target at ON at.assignment_id = a.id
LEFT JOIN edu_assignment_submission s ON s.target_id = at.id
LEFT JOIN edu_grade_publication gp ON gp.submission_id = s.id
GROUP BY a.id, a.title;
```

脚本遇到已存在的演示学期会拒绝重复执行。演示账号 ID **不会创建若依登录账号**；`demo-only/` 文件键和 AI 结果均为占位记录，不对应真实 PPT、视频、预览图片或模型调用。需做端到端页面演示时，先对接真实若依身份与文件处理流程，不要把这些占位值当成已接通的能力。此数据只用于本机演示和开发联调，不用于真实学生、共享正式环境或生产库。

## 建表后仍须落实

以 [架构与数据约束](../docs/architecture.md) 为设计输入，业务方还需评审状态转换、发布快照、有效提交与成绩版本、索引负载及数据保留期限；通过后将这两段脚本登记为正式迁移基线。

- 若依系统表随真实版本接入，保持上游来源与适用版本记录，不伪造账号权限表或演示数据。
- 教学业务表按九领域标明拥有者，课程内容与教学班发布、模板与实例、AI 建议与正式成绩分别建模。
- 活动并发约束、有效提交唯一性、幂等记录、成绩发布版本需共同设计，不仅依赖前端按钮防重。
- 原题/答案/资源及成员快照要能恢复历史统计，成员退出与撤权不应抹除必要的合法历史记录。
- 不使用真实学生资料作测试数据；敏感字段、附件、日志分别评审访问和保留规则。

## 后续脚本组织

确定迁移工具后，将这两段已验证脚本纳入其正式基线版本，再创建 `migrations/` 及必要的 `test-data/`。基线与增量分开，最终编号规则随工具确定；不得对已部署库重复执行这两段 `CREATE TABLE`。

每次提交记录适用版本、前置条件、影响对象、数据转换、验证方法、回退或前向修复办法、执行责任人与审核人。迁移须在独立测试库验证后才申请共享环境执行；不可逆数据修改必须有恢复方案。
