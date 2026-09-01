# UI 层落地顺序计划（T8.1–T8.5）

> 版本：v0.1（2026-09-01）
> 上游设计：[DESIGN_TOKENS.md v4.2.1](./DESIGN_TOKENS.md)（语义令牌/主题包唯一 source of truth；
> 落地映射见 §17）。本文件只决定**顺序与验收节奏**，不重复设计内容；实现细节一律以 DESIGN_TOKENS 为准。
> 落点模块：`designsystem`（`theme/`、`behavior/`、`components/`、`render/`）+ `core:uistate` + `:lint` + `:app`。

---

## 0. 目标与铁律

- 目标：把 `designsystem` 从"单主题组件库"演进为"多风格包运行时"，且 `ChatScreen`/`SettingsScreen` **零视觉回归**。
- 铁律（继承 ARCHITECTURE.md）：
  1. 组件库是唯一 UI 出口，不允许 bypass lint/组件直用 M3 原生；
  2. 纯逻辑进纯 Kotlin（`core:uistate` 可 JVM 单测），不进 Compose；
  3. 每阶段结束必须 **design-guard 四 job 全绿**，不留下"破瓶阶段"；
  4. 改设计先改 DESIGN_TOKENS，再改代码。

## 1. 总顺序与依赖

```
P0 基线冻结 → P1 T8.1 令牌层 → P2 T8.2 主题包(编译期) ┐
                        └──────────────→ P3 T8.5 行为层 ┤ (P2∥P3 可并行)
                                                   ↓
                                      P4 T8.3 运行时可插拔 → P5 T8.4 lint + 全量验收
```

依赖关系：
- P1 是**唯一前置**（motion/type/semantic 令牌、M3 bridge 前提）；
- P2（StyleController/ThemePacks）与 P3（行为层/组件）互相独立，可并行，按团队并线安排；
- P4 依赖 P2（导入/合并/校验建立在运行时框架上），依赖 P1/P3 的令牌与语义；
- P5 依赖全 UI 面（lint 规则要对已存在的组件出口生效），回填触发 P4 的 rules；
- 每阶段交付物 = **设计回读核对 + 新/改文件 + JVM/Compose 测试 + CI 门禁绿 + 回归对照**。

## 2. 阶段拆解（每阶段含：文件 / 交付 / 门禁）

### P0 基线冻结
- [ ] `design-guard` 四 job（core-test / android-build / design-guard / release-build）跑一次全绿，记为基线；
- [ ] 用 `docs/design-system-showcase.html` 对 `ChatScreen`/`SettingsScreen` 截图存基线快照（回归对照源）；
- [ ] `lint` 现有三条规则 + 白名单确认无漏；
- [ ] 拉独立分支 `feat/ui-landing-t8`，冻结 DESIGN_TOKENS v4.2.1。

门禁：四 job 绿。交付：基线快照目录 + 工人起点。

### P1 T8.1 令牌层（新/改文件见 DESIGN_TOKENS §17.3）
- [ ] 增 `theme/TypeRoles.kt`：`AppTextStyle` 六角色 + `AppTextTone`，与 `AppInputs.kt` 现有枚举对齐合并；
- [ ] 增 `theme/AppBrandTokens.kt`：brand 明暗双板（Primitive 灰阶/状态，§3.2）；
- [ ] 增 `theme/AppTokens.kt`：`AppColors` 全语义面板（品牌/表面/文本/边线/状态/业务 6 组）+ `AppTypographyTokens` + `AppMotion`；
- [ ] 改 `theme/Dimens.kt`：`TypeScale` → 六角色 `TextStyle`（§3.3 fontSans/fontMono + 字重/行高）；radius → `AppRadius`；
- [ ] 改 `theme/AppTheme.kt`：从 `AppBrandTokens` 取色，**品牌色首次进 M3**（§9.1 全槽位）；删 `dynamicColor`；
- [ ] JVM 测试：`AppTokensTest`（12.1 面板完整性）、`ContrastMatrixTest`（12.2 WCAG）。

交付：语义面板 + 六角色 + brand 明暗双板可用。门禁：design-guard 绿；§9.1 M3 槽位全映射；12.1/12.2 绿；
`design-system-showcase.html` 无回归（此时仅换色，不应有视觉变化——**有变化即映射错**）。

### P2 T8.2 主题包·编译期（可 ∥ P3）
- [ ] 增 `theme/AppThemeSpec.kt`：`AppThemeSpec`/`TokenPair`/`AppRadius`；
- [ ] 增 `theme/StyleController.kt`：接口 + 默认实现（StateFlow + TableModule 持久化）+ `LocalStyleController`；
- [ ] 增 `theme/AppThemeBridge.kt`：M3 全槽位映射 + 镜像断言辅助；
- [ ] 增 `theme/ThemePacks.kt`：编译期内置包注册表（brand 常驻 + console 演示包）；
- [ ] 改 `theme/AppTheme.kt`：`AppTheme(spec: AppThemeSpec = …)`；`:app` DI 装配 `StyleController`；
- [ ] 设置页接入"风格切换"入口（三态 darkMode + 风格包列表）；
- [ ] Compose 测试：`M3MirrorTest`（12.4 镜像断言）、`ThemeSwitchTest`（12.5 切换生效/FOLLOW_SYSTEM）。

交付：多风格包运行时基线（编译期可换肤）。门禁：12.4/12.5 绿；切换零状态丢失。

### P3 T8.5 行为层 + 组件库（可 ∥ P2）
子顺序：**行为基建 → 骨架 → 存量回归 → 浮层 → 表单 → 消息链路**；每子步都应保持可编译。

- P3a 行为基建
  - [ ] 增 `behavior/AppInteraction.kt`：`appStateLayer` + 行为常量（§4.2）；
  - [ ] 增 `behavior/AppTransitions.kt`（转场模式×档位绑定，§5.2）+ `behavior/MotionResolver.kt`（reduce-motion 直切，§10.3）；
- P3b 骨架五型
  - [ ] 增 `components/scaffold/`：`ChatScaffold`/`TabbedScaffold`/`NavScaffold`/`DetailScaffold`/`FormScaffold` +
    `AppTopTabs`/`AppNavBar`/`AppModalSheet`（§6.5.1）+ `AppScaffoldCompat`；
  - [ ] 改存量 `AppScaffold`/`AppScaffoldWithState` → 内部转发最接近骨架变体；
- P3c 存量组件接入 + 回归
  - [ ] `AppComponents.kt`/`AppInputBar.kt` 全组件接入 `appStateLayer`（§4.3 清单，弃 ripple）；
  - [ ] `ChatScreen`/`SettingsScreen` 迁移到 `ChatScaffold`/详情骨架，逐像素对比 P0 基线快照；
- P3d 浮层子组件
  - [ ] 增 `components/overlay/`：`AppDialog`(三变体)/`AppDropdownMenu`/`AppMultiSelectSheet`/`AppToast`+`ToastHost`/`AppBanner`+`BannerHost`；
  - [ ] `NavScaffoldHost` 内置 `AppToast` 位（底栏上方 inset）；
- P3e 表单子组件
  - [ ] 增 `components/form/`：`AppTextField` 补全（浮动标签/error 图标/形态分工）+ `AppSearchField` + `AppCheckbox`/`AppRadio`；
  - [ ] 增 `components/messaging/`：`AppToolCard`+`ToolCardRegistry`/`AppBlockGroup`/`AppBlockGroupReducer`(core:uistate)`/
    `AppApprovalCard`/`AppProgressSummary`/`StreamEmittedCursor` + `render/MarkdownRenderer.kt`；
  - [ ] 改 `core:uistate`：增 `RenderBlock.Group`（纯 Kotlin）+ reducer 分支；
  - [ ] 改 `render/RenderBlockView.kt`：按 §6.8 路由（AI 全宽流、思考「✦ 摘要」、思考/tool 聚组、进度移动化）；
  - [ ] 改 `AppInputBar`：发/stop 同槽位替换 + Ime 避让。

交付：五型骨架承载 chat/settings 全页面；消息链路八类消息块落地。门禁：12.7–12.10 全绿；
chat/settings 相对 P0 基线 **零视觉回归**（唯一放行：交互态与转场按设计就该变动的部分）。

### P4 T8.3 运行时可插拔
- [ ] 增 `theme/validator/`：`ThemeJsonCodec`(v1)+`ThemePackMerger`(delta)+`ThemeValidator`+`Contrast.kt`(sRGB/线性化)；
- [ ] 增 `theme/ThemePackLoader.kt`（assets/filesDir 双根，复用 `SkillLoader` 模式）；
- [ ] `:app` 装配 `ThemePackLoader` + 设置页导入入口（§7.3 合并 / §7.4 校验报告）；
- [ ] 测试：`ThemeLoaderTableTest`（12.3 表驱动：正常 delta / 未知键告警 / 类型冲突拒载 / 显式 null 拒载 / 版本过高拒载 / 越界回退 + 导入报告）。

上门禁：12.3 绿；手写 delta 包切换成功、非法包拒载出报告。

### P5 T8.4 lint 扩展 + 全量验收
- [ ] 改 `lint/.../DesignSystemDetector.kt` + `DesignSystemIssueRegistry.kt`：注册 §十一 八条新规则
  （`DirectColorLiteral`/`RawTextStyleConstruction`/`ForbiddenWindowComponent`/`ForbiddenPlatformToast`/
  `ForbiddenRawDropdown`/`ForbiddenRawTextField`/`ForbiddenRawToolCard`/`ForbiddenRawJsonRender`）；
- [ ] lint 单元用例 + 在 `feature/*` 植入违规样例断言 design-guard 红；
- [ ] 全量验收，对照 DESIGN_TOKENS §17.5：
  - four job 全绿；
  - `ChatScreen`/`SettingsScreen` 相对 P0 快照零视觉回归；
  - 手写 delta 包导入成功、非法包拒载出报告；
  - 八条新 lint 规则植入违规可命中；
  - `AppTokensTest`/`ContrastMatrixTest`/`M3MirrorTest` 绿。

交付：UI 层落地完成，提交合并请求（附 §17.5 验收清单截图）。

## 3. 里程碑与并行建议

| 里程碑 | 覆盖阶段 | 判定 |
| --- | --- | --- |
| 令牌可用 | P1 结束 | 品牌色首进 M3，showcase 无回归 |
| 可换肤 | P2 结束 | 编译期多风格包切换生效 |
| 全组件化 | P3 结束 | chat/settings 全形态组件化 + 消息链路落地 |
| 可插拔 | P4 结束 | 运行期导入/校验闭环 |
| 落地完成 | P5 结束 | §17.5 全绿，MR 合入 |

并行：P2 与 P3 可双线（令牌层 P1 交付后即分叉）；P3 内部 P3a→P3f 串行但 P3d/P3e 可再并。
**不建议 P4 提前**——导入/校验依赖 P3 已封口的组件出口，过早会让 M1 真实模型被 P3 未定接口回拉。

## 4. 风险与回滚

- **风险 A（令牌层回归）**：P1 换色引入未知视觉变化 → 立即对照 P0 快照定位，多半是 §9.1 槽位映射漏项，回退映射并补断言；
- **风险 B（骨架迁移回归）**：chat/settings 迁移后细节差 → 用 showcase html 逐像素 diff，未过 P3c 不得进入 P3d；
- **风险 C（lint 误伤新包）**：白名单保持 `com.deepcode.designsystem` 全包 + `:lint` 自身（§十一），新容器/行为包天然豁免；
- **风险 D（聚合逻辑耦合 Compose）**：`AppBlockGroupReducer` 若混入 UI 状态会破坏 M0 铁律 → 强制纯 Kotlin(`core:uistate`)，由 JVM 单测兜底；
- 回滚策略：每阶段以独立 commit 落地，命中门禁红即 `git revert` 该阶段，不跨阶段滚。

## 5. 待决项（需在设计评审确认后开工）

- `AppBlockGroupReducer` 的截断/聚组边界定稿（§6.8.8 已给方向，落地前需红过审）；
- 消息链路流式渲染与上游 token 流契约（`MarkdownRenderer` 零 layout shift）对齐 `RenderBlock` 增量语义。