# DESIGN_TOKENS.md — UI 令牌体系与主题包设计（designsystem v4.2）

> 状态：**设计定稿 v4.2（2026-09-01：新增 6.8 消息链路 UI——八类消息块 + 执行组聚组 +
> 工具卡注册表 + 进度移动化变体，决策 D19/D20；含 v4 体系调研 + v4.1 子组件专项），未实施**。
> 实施清单见 十四（T8.1–T8.5，消息链路组件并入 T8.5）。决策记录见 1.3（D5–D20）；本文档是 `designsystem`
> 模块从「单主题组件库」演进为「多风格包运行时」的唯一权威设计。
>
> **落地细化（v4.2.1，2026-09-01 深化）**：在 v4.2 策略定稿之上，补齐可实施粒度——新增
> `十七 落地实施映射（文件级）`（目标目录树、现状→目标差距表、每 T8 步 new/modify/test 文件清单、
> 依赖与 CI 门禁），并给 § 7.1/7.3、+ 六.8/六.5 补上完整 API 签名与数据契约。
> **本文档 = 语义令牌/主题包的唯一 source of truth；落地映射 = 把设计平移成可评审的代码清单。**

## 一、定位与铁律

### 1.1 四层模型与各层准入标准

在三层令牌（Primitive / Semantic / Component）之上，本设计补齐**行为层**
（交互态、动效编排、页面骨架）——令牌回答「长什么样」，行为层回答「怎么动、怎么响应、
怎么搭」。**令牌只允许从上往下单向依赖；行为层只消费语义层，不新增颜色**：

```
Primitive（原始层）    与语义无关的原子值：灰阶/品牌/状态色板、字号字重、间距圆角、动效时长
     ↓ 取值
Semantic（语义层）     按「用途」命名：color.surface、type.heading、radius.card、motion.page
                       —— 业务代码唯一允许引用的层；明暗两套取值由主题包提供
     ↓ 消费
Behavior（行为层）     交互态 overlay / 转场模式绑定 / 页面骨架槽位（四、五、六章）
     ↓ 消费
Component（组件层）    AppCard / AppScaffold 等组件内部，只读语义层与行为层封装
```

**准入标准**（评审逐条对照，防止体系劣化）：

| 层 | 准入标准 | 反例（不允许入层） |
| --- | --- | --- |
| Primitive | ① 与用途无关 ② 有明确物理单位 ③ 被至少两个语义引用 | `brandPurple300`（带用途的"原始值"实为语义） |
| Semantic | ① 用途唯一且长期稳定 ② **组合优先于新增** ③ 明暗两套可给可校验的值 ④ 不绑定组件 | `settingsCardBackground`、`hoverBlue`（绑交互态+颜色，交互响应走四章 overlay，不是颜色令牌） |
| Behavior | ① 只消费语义层与常量（透明度/时长/曲线）② 全组件统一 ③ 不引入新颜色 | 某组件私有的按态变色逻辑 |
| Component | 不新增令牌与行为常量，只消费 | 组件内部参数默认值写死颜色/尺寸 |

### 1.2 铁律

1. **业务层只写语义名**：页面代码出现 `Color(0x…)` / 裸 `dp` / 自拼 `TextStyle` 即为违规
   （lint 范围见 十一）。
2. **令牌先注册后使用**：新增语义令牌必须在 designsystem/theme 与本文档对应表**同时**登记。
3. **组件库是唯一出口**：业务层不经 designsystem 触碰任何 Compose 原生样式 API。
4. **主题包是数据不是代码**：风格差异全部落在令牌取值；不允许携带逻辑、代码、图片（v1）。
5. **A11y 是硬约束不是建议**：对比度、触控目标、字号缩放、reduce-motion 底线
   风格包不可越（十），机器保证而非人工抽查。
6. **行为层零特例**：任何交互组件（含未来新增）必须走四章统一状态机与五章统一转场，
   「这个按钮的按下效果特殊」不是理由——特殊 = 加行为常量档位，不是绕过封装。

### 1.3 决策记录

| # | 决策 | 理由 |
| --- | --- | --- |
| D5 | 多主题形态 = **风格包：可切换 + 可插拔 + 高度自定义**；运行时机制，载体向 Agent Skills 看齐 | `SkillLoader` 已有同型先例 |
| D6 | **dynamicColor（Material You 取色）默认关，参数删除** | 品牌一致性优先；与令牌体系冲突 |
| D7 | 节奏 = 先定稿后实施，T8 分步，每步 CI 全绿 | 全局性改动避免大爆炸重构 |
| D8 | **语义令牌唯一 source of truth；MaterialTheme 是单向派生视图**（全槽位映射 + 镜像断言单测） | 杜绝「M3 默认值缝隙」与两套真理 |
| D9 | theme.json v1 简化自有格式（delta 覆盖），字段组织与 W3C DTCG 机械可映射 | DTCG 无 delta 语义；保留 Figma 生态接口 |
| D10 | **A11y（reduce-motion/字体缩放/触控）由系统解析，风格包不可覆盖** | 无障碍底线机器保证 |
| D11 | **语义令牌新增走评审**；组合优先于新增 | 防语义面板失控 |
| D12 | **交互态统一 State Overlay 模型，弃用 M3 ripple**：响应 = 前景色透明度叠加（+可选微缩放），由 `Modifier.appStateLayer()` 统一封装 | ① ripple 墨水扩散是 Material 品牌语言，与黑白灰冷灰气质不符，且 ripple 色 M3 默认下不可控；② 交互态不进颜色面板（避免「态×色」组合爆炸），overlay 全部用 onColor α 叠加，天然适配任何主题包；③ 行为一次封装全组件一致 |
| D13 | **动效编排 = 转场模式 × 档位绑定表（五.2），全局一处配置**；所有动效经 MotionResolver | 转场散落各处必然失谐；档位已在 3.5 定义，编排表是唯一接线处 |
| D14 | **页面骨架 = 结构组件槽位化（六），五型模板起步**；业务页面只做槽位填充，安全区 insets 骨架层统一消化 | 「顶栏/tab/内容/底栏」的切割以组件契约固化，页面不再手拼布局；insets 各页面自理是深浅色与全面屏适配 bug 的头号来源 |
| D15 | **子组件收编 = 浮层（弹窗/菜单/提示）与表单控件统一 App 前缀组件 + 语义变体参数化**（6.6/6.7），业务层禁裸用对应 Material/平台组件，lint 拦截 | 弹窗「确认/状态/提示」三语义变体各差一个状态色与按钮布局，裸用必然各页各发明；平台 Toast 无主题不可控；变体参数化让「加一种弹窗 = 加一个枚举值」而不是复制一个文件 |
| D16 | **动效升级为 spring 物理双轨（3.5）**：spatial（允许过冲）/ effects（禁过冲）二分 × 三档速度；tween 时长档仅兜底；参数吸收 M3 Expressive 官方 token | spring 可中断、速度感知——流式 AI 输出场景高频重定目标，tween 会重启动画；时长曲线难以全局一致，spring 参数收敛在 6 个 token 内 |
| D17 | **视觉趋势立场 = 克制专业路线（13.4 白名单/黑名单）**：采纳 Bento 网格排布/强调字体/功能微交互/深色自适应；拒绝重度玻璃拟态/新拟态/高饱和撞色/装饰性粒子动效 | 目标产品是 AI 工具类（专业工具赛道），2026 趋势调研确认低饱和冷灰系+克制动效正是该赛道主流；趋势只进「排布与微交互」，不进「色板与骨架」——骨架与色板已定稿，不为趋势翻新 |
| D18 | **弹窗与提示按钮行为 = M3 官方契约硬对齐（6.6.1/6.6.3）**：dialog 最多两钮（第三操作走内联展开）、取消永远在确认左、确认钮可选前置禁用/取消永不禁用、标题三禁、scrim 32%；toast 4s + 横滑关闭 + 单条顶替；输入框 Outlined 为默认形态、error 态自动 ⚠ 图标 + live region 播报 | v4.1 子组件专项全网调研（M3 dialog/snackbar/text field 官方 guidelines）修正 v3.1 三处偏差：三钮竖排违反 M3 上限、scrim 40% 非官方值、输入框形态分工与官方相反——组件级细节以平台规范为准绳，不自行发明 |
| D19 | **消息链路 = 八类消息块 + 执行组聚组（6.8）**：AI 全宽文档流/用户右对齐气泡；工具卡注册表模式、**运行中展开完成即折叠**；思考块**默认折叠一行摘要**；连续 thinking/tool_use 聚为执行组 | 四项均经用户拍板（2026-09-01）；依据：业界已收敛的流式七模式 + 工具卡七模式（ChatGPT/Claude/Cursor/Anthropic Console）；工具卡注册表与 core:mcp 工具注册同构，MCP 外来工具灰色兜底 |
| D20 | **进度面板移动化变体 = 置顶摘要条 + 时间线抽屉（6.8.6）**，不做独立第二面板 | Claude Code/Cursor/Cline/Aider 四家趋同的进度面板在 Compact 端不成立（挤压会话区、与五型骨架冲突）；「可导航的日志不是进度条」原则保留：抽屉内每步可折叠可审计 |

## 二、现状盘点

### 2.1 已就位（不用改）

- `Dimens`（间距 7 档/圆角 5 档/触控/最大宽度）、`TypeScale`（字号 10 档）；
- `AppColors` 9 个聊天业务色（明暗两套）；`AppTextStyle`/`AppTextTone` 语义枚举；
- `AppTheme` 全 App 唯一入口；`AppScaffold`（title/onBack/actions/content 槽位）、`AppInputBar`；
- `:lint`：拦 Material3 import / 自建被禁组件 / 硬编码 dp·sp（已生效）。

### 2.2 缺口（本设计补齐）

1. 品牌主色无入口（`lightColorScheme()` 空参，品牌色从未配置）；
2. 字体令牌只有字号（无字重/行高/字族）；
3. 通用语义色面板缺失（secondary/error 等 M3 槽位全是出厂值）；
4. 主题写死单套（无主题参数）；
5. **交互态无统一设计**：ripple 用 M3 默认（色不可控），hover/selected/disabled 各组件自定；
6. **动效无编排**：只有 3.5 时长档位定义，转场未接线（Navigation 默认转场 = 平台样板）；
7. **骨架不完整**：无顶栏 tab、无底栏导航组件，insets 未统一处理；
8. lint 不拦颜色字面量。

### 2.3 现状 → 目标差距表（落地映射，锚点 17.3）

> 每一行 = 现有实现文件 → 目标形态，标出**改/增**，是评审与排期的依据。具体文件级清单见 17.3。

| 现状（现有文件） | 差距 | 目标（落点文件） | T8 步 |
| --- | --- | --- | --- |
| `theme/AppTheme.kt`：`AppColors` 仅 9 个聊天业务色；`lightColorScheme()`/`darkColorScheme()` 空参（品牌色从未进 M3）；`dynamicColor` 仍默认开 | 无品牌主色、无通用语义面板、品牌色未映射到 M3 槽位 | → 重构为 `theme/` 下 `AppTokens`/`AppBrandTokens`/`AppThemeSpec`/`AppTheme(spec)`；M3 全槽位按 §九.1 映射 | T8.1/T8.2 |
| `theme/Dimens.kt`：`TypeScale` 仅字号；`radius` 5 档固定 | 无字重/字族/行高角色；radius 不可被风格包覆盖 | → `TypeScale` 扩展为角色 `TextStyle`（§3.3）；radius 派生 `Shapes` 供 theme.json 覆盖 | T8.1/T8.3 |
| `components/AppComponents.kt`：按钮/卡片/状态片用 M3 ripple；各组件自写 hover/selected | 交互态无统一模型 | → 全部接入 `Modifier.appStateLayer()`（§4）；新建 `behavior/AppInteraction.kt` | T8.5 |
| `components/AppScaffold.kt`：单骨架、`Scaffold` 直用、insets 未统一、snackbar 挂平台 | 无五型骨架/无 tab/无底栏 nav | → 拆分子组件 + 五型骨架（§6.1）；`AppToast`/`AppBanner` 取代平台 snackbar | T8.5 |
| `components/AppInputs.kt`：`AppTextStyle` 7 枚举（Display/TitleLarge/Title/BodyLarge/Body/Label/Caption，缺 SectionHeader/Code，且 Style×Tone 分离已做） | 角色未对齐 §3.3 六角色；`AppTextField` 无浮动标签/error 图标/形态分工 | → 角色定稿六类（§3.3）；`AppTextField` 补全（§6.7.1） | T8.1/T8.5 |
| `render/RenderBlockView.kt`：`UserMessage`/`AssistantText` 均为气泡；`ThinkingView` 折叠但无紫身份色；`ToolInvocationView` 一次性卡片（非注册表/非展开折叠）；审批行内三钮 | 消息链路未对齐 §6.8：AI 全宽流、思考块「✦ 摘要」、工具卡注册表+运行中展开/完成折叠、执行组聚组、审批卡、进度条 | → 新增 `components/messaging/`：`AppToolCard`/`AppBlockGroup`/`AppApprovalCard`/`AppProgressSummary` + 流式 Composer；`RenderBlockView` 按 §6.8 路由 | T8.5 |
| `:lint`：`DesignSystemDetector.kt` 3 条规则，行为层未拦 | lint 未覆盖新的语义/组件出口 | → 扩 6 条新规则（§十一），含颜色字面量/裸表单/裸工具卡 | T8.4 |

## 三、令牌体系类型设计

### 3.1 类型安全：让错误用法编译期炸掉

```kotlin
/** 语义令牌面板：具名属性即令牌；加令牌 = 加属性 + 明暗值 + 配对声明。 */
@Immutable
data class AppColors(
    // 品牌
    val primary: Color, val onPrimary: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    // 表面
    val surface: Color, val surfaceVariant: Color, val surfaceElevated: Color,
    // 文本
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val textInverse: Color,
    // 边线
    val divider: Color, val border: Color,
    // 状态
    val success: Color, val warning: Color, val danger: Color, val info: Color,
    // 业务保留（聊天场景）
    val diffAdd: Color, val diffRemove: Color,
    val toolRunning: Color, val toolSuccess: Color, val toolFailed: Color,
    val toolAwaiting: Color,
    val thinking: Color, val codeSurface: Color, val codeBorder: Color,
)

@Immutable
data class AppTypographyTokens(
    val fontSans: FontFamily, val fontMono: FontFamily,
    // 角色即令牌：页面选角色，不自由组合（见 3.3）
    val title: TextStyle, val sectionHeader: TextStyle,
    val body: TextStyle, val label: TextStyle,
    val caption: TextStyle, val code: TextStyle,
)

@Immutable
data class AppMotion(
    val fast: Duration, val normal: Duration, val slow: Duration,
    val easingStandard: Easing, val easingEmphasized: Easing,
)

@Immutable
data class AppTokens(
    val colors: AppColors, val type: AppTypographyTokens,
    val motion: AppMotion,
    // spacing/radius 维持 Dimens 现有 object（编译期常量，无明暗之分）；
    // 风格包可经 theme.json radius 覆盖组生成派生 Shapes
)
```

要点：

- **data class 具名属性 = 编译期完整性**：拼错令牌名、漏配明暗值都是编译失败；
- `@Immutable` 全覆盖：Spec 跨重组稳定引用（配合 八.1）；
- 颜色不引入 value class：安全边界在 lint（业务层禁构造 Color），不在类型层。

### 3.2 颜色语义面板

**brand 包色板定稿**（2026-09-01 用户定调：**黑白灰主调 · 蓝紫点缀 · 红绿状态**，
其余按体系约束发挥）：

**Primitive 灰阶（冷灰，承担 95% 界面）与明暗落位**

| Primitive | 值 | Light 落位 | Dark 落位 |
| --- | --- | --- | --- |
| `gray-0` | `#FFFFFF` | `surface` / `surfaceElevated` | — |
| `gray-50` | `#F4F5F7` | `surfaceVariant` / `codeSurface` | — |
| `gray-100` | `#E8EAEF` | `divider` / `codeBorder` | — |
| `gray-200` | `#D9DCE3` | `border` | — |
| `gray-400` | `#9CA1AC` | `textTertiary` | — |
| `gray-600` | `#5C6270` | `textSecondary` | — |
| `gray-750` | `#353943` | — | `border` |
| `gray-800` | `#2A2D34` | — | `divider` |
| `gray-850` | `#1C1E23` | — | `surfaceVariant` / `codeSurface` |
| `gray-900` | `#131417` | — | `surface` |
| `ink-900` | `#17181C` | `textPrimary` | — |
| `ink-50` | `#F2F3F5` | — | `textPrimary` |
| `ink-300` | `#A6ABB7` | — | `textSecondary` |
| `ink-500` | `#6B7180` | — | `textTertiary` |

**品牌点缀与状态**

| 组 | Light | Dark | 用途边界 |
| --- | --- | --- | --- |
| 蓝（操作） | `primary #2563EB`（白字 5.1:1）/ `primaryContainer #EFF3FF` | `primary #7A93FF`（深字 `#0D1230`，6.0:1）/ `primaryContainer #1B2A5E` | 只出现在「可操作」处：主按钮/链接/选中/运行中 |
| 紫（AI 身份） | `#7C3AED` / 容器 `#F3EFFE` | `#A78BFA` / 容器 `#221B38` | 专属 AI 语义：思考流/技能徽标/agent 标识 |
| `success` | `#16A34A`（底 `#E9F9EF`） | `#4ADE80`（底 `#12291B`） | 状态 |
| `warning` | `#D97706`（底 `#FDF3E3`） | `#FBBF24`（底 `#2E2210`） | 等待/确认 |
| `danger` | `#DC2626`（底 `#FDECEC`） | `#F87171`（底 `#331A1A`） | 失败/删除 |
| `info` | v1 复用 `primary` 蓝 | 同左 | 预留槽位 |

**业务色映射**：`toolRunning→primary`、`toolSuccess→success`、`toolFailed→danger`、
`toolAwaiting→warning`、`thinking→紫`（AI 身份色核心落点）、`diffAdd/diffRemove→
success/danger 浅底形态`、`codeSurface/codeBorder→gray-50/100（L）· gray-850/750（D）`。

设计原则：灰阶负责结构与留白，**蓝 = 可操作，紫 = AI，红绿黄 = 状态**，三者互不越界。
深色 primary 取「亮蓝 + 深字」（6.0:1），弃「亮蓝+白字」（4.0:1 不达标）。

**深色海拔体系（v4 吸收 M3 dark theme 规则）**：
暗色下阴影几乎不可见，层级表达改用「**抬升变亮**」——高层级表面比低层级更亮：

| 海拔（语义） | 表面 | 相对 `surface` 的亮度处理 |
| --- | --- | --- |
| 0 页面底 | `surface`（L gray-50 / D gray-950 系） | 基准 |
| 1 卡片/列表容器 | `surfaceVariant` | 灰阶 +1~2 档 |
| 2 浮层（菜单/输入坞/底栏） | `surfaceElevated` | 灰阶 +3~4 档 |
| 3 模态（Dialog/Sheet） | `surfaceElevated` + 遮罩 | 灰阶 +3~4 档 + 遮罩压暗四周 |

- 对比度校验基准 **15.8:1**：`textPrimary ↔ surfaceElevated` 必须达标
  （M3 要求白色正文在最高层级表面上仍过 WCAG AA 4.5:1，倒推底面亮度上限）；
- **深色模式去饱和规则**：暗底上的状态色/品牌色必须比亮色模式去饱和
  （高饱和色在暗底产生光学振动，M3 明令避免）——brand 暗板已按此调；
- 禁止用「光晕/辉光」替代阴影表达海拔（暗色下 glow 不能传达层级，只会加噪）；
- 亮色模式海拔照常用阴影（shadow 常量表进实现），暗色模式全部走上表，**两套
  海拔表达都封装在 `AppSurface(elevation=…)` 单组件内**，业务零感知。

（下表为语义面板完整清单与配对声明——**配对矩阵是 10.1 校验与 12.2 单测的依据**）

| 组 | 令牌 | 说明与配对声明（↔ 为校验对） |
| --- | --- | --- |
| 品牌 | `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer` | `onPrimary ↔ primary` ≥4.5 |
| 表面 | `surface` / `surfaceVariant` / `surfaceElevated` | 三者相互差异 ≥1.2:1（软约束告警） |
| 文本 | `textPrimary` / `textSecondary` / `textTertiary` / `textInverse` | `textPrimary ↔ surface` ≥4.5；`textSecondary ↔ surface` ≥4.5；`textTertiary ↔ surface` ≥3（弱信息豁免档） |
| 边线 | `divider` / `border` | 与 surface 差异 ≥1.2 告警 |
| 状态 | `success` / `warning` / `danger` / `info` | 作文本 ↔ surface ≥4.5；作底色配固定 onStatus 黑/白（v1 不开放覆盖） |
| 业务保留 | diff/tool 四态/thinking/code 两组 | 名不变；tool 态默认映射状态组，风格包可单独覆盖 |

### 3.3 字体与角色设计

| 维度 | 令牌 | 基线 |
| --- | --- | --- |
| 字族 | `fontSans` / `fontMono` | 系统默认；等宽用于代码块与命令输出 |
| 字重 | `wRegular=400` / `wMedium=500` / `wSemibold=600` / `wBold=700` | 只在角色内部使用 |
| 行高 | 标题 1.2 / 正文 1.4 / 代码 1.5 | 收敛现有 `code 12.5/18≈1.44` → 1.5 |

**角色枚举是字体的唯一出口**（`AppTextStyle.Title/SectionHeader/Body/Label/Caption/Code`），
角色内部组合字号×字重×行高×字族。角色与 `AppTextTone`（颜色）**保持分离**：
`AppText(style = Title, tone = Tertiary)`——色与形正交，避免 6×5 组合枚举爆炸。

### 3.4 间距与圆角

维持 `Dimens` 现有档位不重命名（存量零迁移）。语义别名按需追加进同文件同 lint 保护。
圆角进 theme.json 覆盖组时只允许四档 + bubble。

### 3.5 动效档位（编排的输入，见 五）——v4 升级：spring 物理双轨

> 调研来源：M3 Expressive 运动物理系统（2025，46 项研究 / 18k 参与者）。
> 核心结论：**spring 物理取代固定时长+曲线**——spring 动画可中断、速度感知，
> 手势中途接管时平滑续接（tween 会重启动画）；这正是 Agent 类应用高频流式
> 更新场景最需要的特性。

**双轨制**：spring 档为主（一切可中断的动效），tween 时长档为兜底（不可中断的
一次性序列，如引导页）。`MotionResolver` 统一出口；reduce motion 时 spring 退化
为直切（10.3）。

**Spring 档（主轨）**——按 `dampingRatio（回弹衰减）/ stiffness（刚度，越高越快）`：

| 档 | Spatial 类（位置/尺寸/圆角——**允许 ≤6% 过冲回弹**） | Effects 类（色彩/透明度——**禁止过冲，damping=1.0**） |
| --- | --- | --- |
| `fast` | `spring(0.6, 800)`——小元素：开关/按钮/芯片，最弹的一档 | `spring(1.0, 3800)` |
| `normal` | `spring(0.8, 380)`——默认档：卡片/列表项/骨架转场 | `spring(1.0, 1600)` |
| `slow` | `spring(0.8, 200)`——全屏级：页面转场/大面板 | `spring(1.0, 800)` |

- Spatial 与 Effects 二分是硬规则：**「expression 属于运动，不属于颜色」**
  （M3 原文）——色彩/透明度回弹会显得抖动廉价，一律 damping=1.0；
- 参数吸收自 M3 Expressive 官方 spring token（expressive scheme；工具性页面如
  设置分组可用 standard 备选组 spatial `0.9/300·700·1400`，回弹更收敛）；
- theme.json v1 不开放动效覆盖（同前），未来 v2 开放时只允许在两组 scheme
  预设间切换，不允许裸调 damping/stiffness（防风格包把动效调炸）。

**Tween 档（兜底轨）**：`fast=120ms` / `normal=220ms` / `slow=320ms` +
`easingStandard` / `easingEmphasized`。仅用于不可中断序列（引导/首启动）；
五章转场模式表标注各行归属。

### 3.6 布局断点与比例

| 令牌/档位 | 值 | 用途 |
| --- | --- | --- |
| `Compact` | < 600dp | 单列（手机竖屏默认） |
| `Medium` | 600–840dp | 单列 + 更宽留白（横屏/小屏折叠） |
| `Expanded` | > 840dp | `maxContentWidth`（720dp）居中 + 双列预留（六.4） |
| 媒体比例档 | `16:9` / `4:3` / `1:1` | 图片/媒体渲染块（AppMediaBlock 内部约定） |
| 代码块高度 | `maxLines=30` 上限（现状） | 长输出折叠 |

断点用 `WindowSizeClass` 官方库读取，经 `LocalWindowSize` 提供；骨架组件（六）按断点
决定内部布局（如 Expanded 下 ChatScaffold 侧栏预留），业务页面零感知。

## 四、交互态系统（Behavior 之一，D12）

### 4.1 八态状态机

所有可交互组件统一以下状态词汇表（`InteractionSource` 收集，全局唯一实现）：

| 态 | 触发 | 视觉响应（overlay 常量） |
| --- | --- | --- |
| `default` | — | 无叠加 |
| `hovered` | 指针悬停（桌面/平板/折叠屏外设） | 前景色 @ **8%** overlay |
| `pressed` | 按下 | 前景色 @ **12%** overlay ＋ `scale(0.98)` |
| `focused` | 键盘/焦点导航 | `primary` 2dp 描边（键盘可达性，触屏不出现） |
| `selected` | 选中（tab/chip/列表项） | `primary` @ 12% 底 ＋ 内容转 `primary` / 指示器 |
| `disabled` | 禁用 | 内容 `onSurface @ 38%`，移除全部交互响应 |
| `loading` | 处理中 | 进度指示器接管（按钮内嵌，尺寸 = 图标档） |
| `dragged` | 拖拽中 | `surfaceElevated` + `scale(1.02)` + 阴影抬升 |

### 4.2 实现契约：`Modifier.appStateLayer()`（完整签名，落点 `behavior/AppInteraction.kt`）

```kotlin
// designsystem 内部唯一实现；组件只传 InteractionSource 与形状
// 完整签名（T8.5 实现定稿）：
fun Modifier.appStateLayer(
    interactionSource: InteractionSource,
    shape: Shape,
    enabled: Boolean = true,
    selected: Boolean = false,        // 显式 selected：主色 12% 底 + 内容主色（4.1.select）
    overlayColor: Color? = null,      // 默认 = 组件当前内容前景色（onColor 模型）；仅特殊组可传
    scalePressed: Float = 0.98f,      // Draggable 组件可关：scalePressed = 1f
): Modifier

// 用法：AppButton 等封装统一把 overlay + indication 交给 appStateLayer，
// M3 组件的 indication 参数传它返回的 Indication（ripple 由此消失）：
val interactionSource = remember { MutableInteractionSource() }
Surface(
    onClick = { ... },
    interactionSource = interactionSource,
    indication = rememberIndication(interactionSource, shape),   // appStateLayer 派生的 Indication
)
```

- `appStateLayer` 内部 = `Modifier.graphicsLayer`（叠加 overlay 色）+ `Modifier.scale`（pressed/dragged）+
  生成 `Indication`（`LocalIndication`）；**一个 Modifier 同时吃 overlay 与 indication，组件层只调一次**；
- overlay 色取值：`InteractionSource` 合成 `pressed/hovered/selected` 为**二值叠加表**，按 §4.1 常量
  （8% / 12% / 38%）在前景色上算 α——**任何主题包、任何语义底色下自动成立，交互态因此不进颜色面板**；
- `pressed` 缩放与 `dragged` 抬升、`focused` 描边为内置常量，不暴露给组件层；
- **弃用 ripple（D12）**：`AppButton` 等封装里 M3 组件的 `indication` 参数统一传 `appStateLayer` 生成的
  indication，ripple 不再出现；
- **行为常量集中定义** `behavior/AppInteraction.kt`：`val OVERLAY_HOVER = 0.08f`、`OVERLAY_PRESS = 0.12f`、
  `ALPHA_DISABLED = 0.38f`、`SCALE_PRESS = 0.98f`、`SCALE_DRAG = 1.02f`，**改走评审**（非颜色令牌）；

### 4.3 接入清单

`AppPrimaryButton` / `AppTextButton` / `AppCard`（可点击变体）/ `AppTextField` /
`AppSwitch` / `AppStatusChip` / `AppTopTabs`（六.2）/ `AppNavBar`（六.3）/
`AppDropdownMenu` 触发器与条目（6.6.2）/ `AppBanner`（6.6.3）/
`AppCheckbox` / `AppRadio` / `AppSearchField`（6.7.2）——
T8.5 全量接入；新增组件 PR 必须含 appStateLayer 接入，否则 design-guard 红。

### 4.4 功能微交互三件套（v4 吸收：微交互是沟通，不是装饰）

2026 交互趋势共识：微交互从「装饰」转为「替代文案的沟通工具」。收编三类
（全部走 spring 档、全部在组件内实现、业务零感知）：

| 微交互 | 触发 | 规格 | 沟通了什么 |
| --- | --- | --- | --- |
| 错误抖动 `shake` | 输入框校验失败 / 表单提交被拒 | 水平 ±4dp × 2 回摆（spatial fast） | 「这里错了」比 errorText 更先被感知 |
| 完成打勾 `checkReveal` | 工具执行成功 / 保存成功 | success 图标从 60% 缩放弹出（spatial fast 过冲） | 「已完成」；省掉一条 toast |
| 数字滚动 `countUp` | token 计数/进度百分比变化 | 数值 tween fast + 等宽数字（`tabular`） | 变化量可感知，避免数字跳变盲区 |

原则：**动效服务信息层级，不做装饰**（Calm Interface）——同类微交互新增需
评审（防 4.4 膨胀成动效玩具箱）。

## 五、动效编排（Behavior 之二，D13）

### 5.1 原则

转场**模式**与**参数**分离：模式（怎么动）全局枚举，参数（多快、什么曲线）只引用
3.5 档位。任何页面不允许自造第五种转场模式——需要新模式 = 扩展五.2 表 + 评审。

### 5.2 转场模式 × 档位绑定表（唯一接线处）

| 模式 | 规格 | 时长/曲线 | 实现载体 |
| --- | --- | --- | --- |
| `PagePush` | 新页自右 8% 位移 + fade in；旧页 4% 位移 + fade out | `slow` / `emphasized` | Navigation Compose graph 全局配置 |
| `PagePop` | `PagePush` 镜像 | `slow` / `emphasized` | 同上 |
| `ModalSheet` | 底部上滑 + 遮罩 fade | `normal` / `emphasized` | AppModalSheet（六.5） |
| `DialogShow` | fade + `scale 0.92→1` | `fast` / `standard` | AppDialog |
| `MenuShow` | fade + `scaleY 0.95→1`（锚点=触发器底边） | `fast` | AppDropdownMenu（6.6.2） |
| `ToastShow` | 底部滑入 + fade；退场 fade | `fast` | AppToast / AppBanner（6.6.3） |
| `TabSwitch` | crossfade（内容区，顶栏指示器滑动另计） | `fast` / `standard` | AppTopTabs / 页面内容 |
| `ListInsert/Remove` | 高度 expand/collapse + fade | `normal` / `standard` | LazyColumn animateItem |
| `ThoughtExpand` | 思考流高度展开 | `normal` / `standard` | RenderBlockView |
| `StateSwap` | 同位置状态内容切换（如按钮 loading→正常） | `fast` / `standard` | Crossfade |

- **转场参数只在 `AppTransitions` 常量对象中出现一次**，Navigation graph 与骨架组件
  全部引用它——改一处全局生效；
- 全部动效经 `MotionResolver`（10.3）：系统 reduce-motion 时一律直切；
- 顶栏 tab 指示器随 `TabSwitch` 滑动（`fast`/`emphasized`），不做 tab 内容位移动画
  （对话流场景 crossfade 观感优于平移）。

### 5.3 高宽比例与尺寸动画

- 媒体渲染块比例档（3.6）内定死，动画只做尺寸进出场（expand），不做比例形变；
- 骨架折叠（DetailScaffold 大标题收合）进度与滚动联动，曲线 `standard`，
  不独立计时（跟手优先）。

## 六、页面骨架与子组件系统（Behavior 之三，D14/D15）

### 6.1 三段式切割与五型模板

所有页面 = **顶栏（TopBar）+ 内容（Content）+ 底栏（BottomBar）**三段式切割，
每段为骨架组件的槽位。五型起步：

| 骨架 | 顶栏 | 内容 | 底栏 | 适用页面 |
| --- | --- | --- | --- | --- |
| `ChatScaffold` | 紧凑栏（返回/标题/操作） | 对话流（`maxContentWidth` 居中，底部随键盘避让） | `AppInputBar` | 聊天/Agent 会话 |
| `TabbedScaffold` | 紧凑栏 ＋ **`AppTopTabs`**（可与内容滚动联动收合） | 内容区 | 无 | 工具市场/技能库/设置分组 |
| `NavScaffold` | 无（各 tab 页自带顶栏） | tab 内容 | **`AppNavBar`**（3–5 tab） | 主导航容器 |
| `DetailScaffold` | 可折叠大标题（滚动收合为紧凑栏） | 详情流 | 操作条（可选） | 会话详情/文档详情 |
| `FormScaffold` | 紧凑栏 | 表单分组（group 间距 = `spaceL`） | 确认条（主按钮 + 副操作） | 表单/向导 |

**业务页面 = 选骨架 + 填槽位**；骨架之外禁止自行拼 `MaterialScaffold`/`Row` 结构
（lint FORBIDDEN 已拦 Scaffold，拼装问题由评审兜底）。

### 6.2 顶栏与 `AppTopTabs`

- 紧凑栏：高 56dp、内边距 `screenPaddingHorizontal`、标题 `Title` 角色、
  返回/操作图标 `iconL`；底部 `divider` 细线（内容滚动时出现）；
- `AppTopTabs`：tab 高 44dp（触控冗余至 48dp）、文字 `Label` 角色、选中态走四章
  `selected`（primary 12% 底）；**指示器** = 底部 3dp 圆角短条（宽 = 文字宽 60%，
  居中），随切换滑动（5.2）；
- 大标题折叠（DetailScaffold）：`displaySmall` 24sp → 收合为 17sp 紧凑栏，
  折叠进度与滚动 1:1 跟手，字号/内边距插值在骨架内部完成。

### 6.3 底栏与 `AppNavBar`

- 高 64dp ＋ 底部安全区 insets（骨架内部 `windowInsetsPadding`，业务零感知）；
- tab 项：图标 24dp（选中 `iconL` 同尺寸 + `primary` 色，未选中 `textTertiary`）＋
  11sp 标签（`Label` 角色）；选中态含 4.1 `selected` overlay（primary 12% 圆角底）；
- badge（未读/计数）：右上角，红底白字（`danger`/白），数字超 99 显示 99+；
- 3–5 tab 硬上限（超过 = 信息架构问题，不是 UI 问题）。

### 6.4 断点行为与双列预留

- Compact：全部骨架单列；
- **拇指区规则（v4 吸收触控人因研究）**：单手拇指自然弧覆盖下半屏——高频操作
  （底栏 tab / 输入坞 / 主 CTA / 悬浮操作）一律落拇指区；顶栏只放低频操作
  （返回/菜单/次要动作）。指尖平均 1.6–2cm、拇指按压区约 2.5cm → 最低触控
  48dp 之外，**相邻可点元素间距 ≥8dp**（防误触，进组件默认值）；
- Expanded（>840dp）：`ChatScaffold` 预留会话列表侧栏位（双列），`maxContentWidth`
  居中；`NavScaffold` 底栏转侧栏（rail）——**v1 只做布局位预留与居中，侧栏/rail
  实施排 T8.5 之后按需启动**（当前无平板验收设备，避免无验收的复杂度）。

### 6.5 结构组件清单（T8.5 新建/补全）

| 组件 | 性质 | 说明 |
| --- | --- | --- |
| `AppTopTabs` / `AppNavBar` / `AppModalSheet` | 新建 | 顶栏 tab / 底栏导航 / 模态壳（转场接 5.2） |
| `AppDialog`（含三语义变体，见 6.6）/ `AppMediaBlock` | 新建 | 对话框壳（禁裸 Dialog，lint）/ 媒体比例块 |
| `AppScaffold`（五型化）/ `AppInputBar` | 改造 | 拆分为骨架变体；insets 统一进骨架 |

#### 6.5.1 五型骨架函数契约（落点：`components/scaffold/`）

> 骨架 = 顶栏 + 内容 + 底栏三段槽位。业务页面只选骨架 + 填槽位，**不自己拼 `Scaffold`/`Row`**（§6.1）。
> insets（`NavigationBars`/`Ime`）全部在骨架内部 `windowInsetsPadding` 消化，业务零感知。

```kotlin
// 统一 ContentScaffold 基底（内部吃 insets、衬 background、包 LimitedWidth for Expanded）
@Composable
fun ChatScaffold(
    topBar: @Composable () -> Unit,               // 紧凑栏（返回/标题/操作）
    inputBar: @Composable () -> Unit,             // AppInputBar 槽（含 Ime 避让）
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    progressSticky: @Composable () -> Unit = {},  // 6.8.6 摘要条 / 6.8.4 审批卡 sticky 位
    content: @Composable (PaddingValues) -> Unit, // TranscriptList（AI 全宽文档流，中线居中）
)

@Composable
fun TabbedScaffold(
    tabs: List<TabItem>,                          // AppTopTabs 数据
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    topBarExtra: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, PaddingValues) -> Unit,
)

@Composable
fun NavScaffold(
    tabs: List<NavItem>,                          // 3–5 项，AppNavBar
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, PaddingValues) -> Unit,
)
// navScaffoldHost = AppToast 内置位（底栏上方 inset，业务零感知，§6.6.3）

@Composable
fun DetailScaffold(
    title: String, largeTitle: String,            // 滚动收合：displaySmall→17sp（与滚动 1:1 跟手）
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomActions: @Composable () -> Unit = {},   // 可选操作条
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
)

@Composable
fun FormScaffold(
    title: String, onBack: (() -> Unit)? = null,
    confirm: @Composable () -> Unit = {},         // 确认条：主按钮 + 副操作
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit),// group 间距 = spaceL，Expanded 双列短字段
)

// 数据模型
data class TabItem(val id: String, val text: String, val icon: ImageVector? = null, val badge: Int? = null)
data class NavItem(val id: String, val text: String, val icon: ImageVector, val badge: Int? = null)

// 组件级（`components/`）：
@Composable fun AppTopTabs(tabs: List<TabItem>, selectedIndex: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier)
@Composable fun AppNavBar(tabs: List<NavItem>, selectedIndex: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier)
@Composable fun AppModalSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,                // 拖拽柄 32×4dp、居顶 22dp、嵌套滚动接底
    content: @Composable ColumnScope.() -> Unit,
)
```

> `AppScaffold`（现状单骨架）改造为内部转发到最接近的骨架变体；`AppScaffoldWithState` 保留
> 作为「有 UiState 的便捷壳」，底层仍走上述骨架。**存量页面（Chat/Settings）零视觉回归**。

### 6.6 浮层子组件规范（Behavior 级，D15）

页面骨架（6.1）解决「切割」，本节解决「段内浮层」——弹窗、菜单、轻提示全部
收编为 App 前缀组件 + 语义变体参数化，业务层零裸用（lint 拦截，见 十一）。

**浮层选型金字塔**（按打断程度从轻到重，选型错误 = 交互债务）：

```
inline（输入框下 helper/error）  ←  输入校验，最轻
banner（页面内常驻横幅）        ←  持久状态/引导，可关闭
toast（底部悬浮短提示）         ←  操作结果回执，3s 自动消失
dialog（模态决策）              ←  需要用户确认/决策，打断
modalSheet（底部模态面板）      ←  重决策 + 富内容表单
```

#### 6.6.1 `AppDialog`：一个壳，三个语义变体（v4.1 对照 M3 官方规范修订）

统一壳规格（三变体共享）：宽 `min(内容, 320dp)`（Expanded 上限 420dp）、
圆角 `radiusXL`、遮罩 `black @ 32%`（**M3 官方 scrim 值，v4.1 修正**）、
转场 `DialogShow`（5.2，fast 档）、内容边距 24dp、标题 `titleMedium` +
正文 `bodyMedium`、海拔 `surfaceElevated`（对应 M3 `surfaceContainerHigh`）。

| 变体 | 语义 | 布局契约 | 状态色 |
| --- | --- | --- | --- |
| **确认** `confirm()` | 破坏性/不可逆操作前置确认 | **双钮横排右对齐（M3 上限即两钮）**：文字钮「取消」在左 + 确认钮最靠边；两钮间距 `spaceS` | `danger=true` 时实心钮转 `danger` 底白字（删除 MCP 服务器/撤销授权/清空会话） |
| **状态** `status()` | 进度/成功/失败回执 | `state: Progress/Success/Failure` 单枚举驱动；Progress 只渲染转圈 + 文案**且不出按钮**（防等待中误触），Success/Failure 出单「知道了」钮（单钮 = acknowledgement，M3 允许的唯一单钮形态）；图标位 40dp 圆底 + 白图标 | Progress→`primary` / Success→`success` / Failure→`danger` |
| **提示** `notice()` | 纯信息告知（版本说明/条款） | 单「知道了」钮或自定义 actions；可承载富内容槽（长文滚动区 `maxHeight = 60%` 屏高） | 中性，不着状态色 |

**按钮排列铁律（v4.1 吸收 M3 dialog guidelines，D18）**：

- **最多两个按钮**：一钮必须是 acknowledgement；两钮 = 确认 + 取消。
  「了解更多」类第三操作**不允许成钮**——用正文内联展开承载（M3 明确不推荐
  第三钮导航离场，会留下未完成的对话框任务）；
- **取消（dismissive）永远在确认（confirm）左侧**、确认钮最靠 trailing 边；
  RTL 语言自动镜像；需要竖排（按钮文案过长）时**确认在上、取消在下**；
- **确认钮在选择做出前禁用**（如多选未勾选时「删除」灰置）；**取消钮永不禁用**；
- **标题三禁**：禁道歉语（「抱歉打断」）、禁警报词（「警告！」）、禁模糊问句
  （「确定吗？」）——标题必须是具体、简短的陈述或问句（正例：「删除 MCP 服务器？」）；
- **重要性策略**（M3 官方 messaging 策略表）：低/中优先级信息**禁止用 dialog**，
  一律降级 toast/snackbar——dialog 是打断成本最高的组件，只留给「必须决策」。

变体间**不允许混合发明**（「确认 + 进度条」混合体 = 违规，评审兜底）。

#### 6.6.2 `AppDropdownMenu`：单选下拉

- **触发器**两形态：`Field` 形态（内嵌 `AppTextField` 外观，见 6.7，表单内用）/
  `Button` 形态（筛选条/顶栏操作用，实心或描边）；
- **面板**：圆角 `radiusM`、`surfaceElevated` 海拔、宽 = 触发器宽（最小 120dp）、
  条目高 44dp（触控冗余 48dp）、条目态走 4.1（selected = primary 12% 底 +
  `check` 图标 primary 色）、条目分块（分隔线 `divider` + 「分组标题」
  `labelMedium textTertiary`）；
- 转场 `MenuShow`（5.2）：fade + `scaleY 0.95→1`，`fast` 档，锚点为触发器底边；
- **多选不走菜单**：Compact 端多选 = `AppModalSheet` + checkbox 列表（菜单撑不下
  且触控质量差），组件名 `AppMultiSelectSheet`（T8.5 一并建）；
- **`AppModalSheet` 统一规格（v4.1 补）**：顶部拖拽柄 **32×4dp 全圆角、水平居中、
  距顶 22dp**（M3 modal bottom sheet 官方值）；嵌套滚动接底（内容先滚到底再拖关）；
- 禁裸 `ExposedDropdownMenuBox` / `DropdownMenu`（lint `ForbiddenRawDropdown`）。
  注：M3 官方中 ExposedDropdown 本就是 text field 的一种模式——我们的 `Field`
  形态与其对齐，`Field` 形态可带输入过滤（autocomplete 语义，可选能力）。

#### 6.6.3 轻提示体系：`AppToast` / `AppBanner`

| 组件 | 位置 | 消失规则 | 承载内容 |
| --- | --- | --- | --- |
| `AppToast` | 底部悬浮（NavScaffold 内置在底栏上方 inset，业务零感知） | **4s 自动（M3 `LENGTH_SHORT` 官方值，v4.1 对齐）**；带「撤销」类动作钮延长至 6s；同屏最多 1 条（新 toast 直接顶替旧条——M3 官方行为）；**支持横滑关闭（swipe-to-dismiss）** | 单行回执 + 可选动作钮（「撤销」，动作色 = primary 反相版）；`level: neutral/success/danger` 三色 |
| `AppBanner` | 内容区顶部（骨架槽位，`BannerHost` 统一管理） | **常驻**直到用户关闭或状态解除；不自动消失 | 标题 + 可选正文 + 可选动作；`level: info/success/warning/danger` 四色 + 40dp 图标位 |

- Toast 形态：圆角 `radiusM`、深色反相底（`inverseSurface` 系——M3 官方
  `colorSurfaceInverse`，浅深色下都够对比）、高 ≥44dp、外边距 8dp（M3 官方）、
  海拔 6dp、左右边距对齐 `screenPaddingHorizontal`、多行截断 2 行省略；
- Banner 形态：圆角 `radiusM`、level 容器色浅底（3.2 状态色 `容器` 列）+ level 色
  图标/按钮、左图标 24dp；
- **禁裸 Android `Toast`**（lint `ForbiddenPlatformToast`）——平台 toast 无主题、
  无品牌圆角、无法接色板，一律走 Compose 自绘 `AppToast`；
- 层级规则（同 6.6 金字塔）：回执 → toast；引导/持久 → banner；校验 → inline；
  决策 → dialog。**禁止用 dialog 做操作回执**（打断成本最高，最重的错）。

#### 6.6.4 浮层组件清单（T8.5 新建）

| 组件 | 说明 |
| --- | --- |
| `AppDialog`（`confirm`/`status`/`notice` 三变体） | 模态决策壳；lint `ForbiddenWindowComponent` 已规划拦截裸 Dialog |
| `AppDropdownMenu`（Field/Button 两形态） | 单选下拉；lint `ForbiddenRawDropdown` 拦裸用 |
| `AppMultiSelectSheet` | 多选 = ModalSheet + checkbox 列表 |
| `AppToast` / `AppBanner` + `ToastHost` / `BannerHost` | 轻提示双件套；lint `ForbiddenPlatformToast` 拦平台 toast |
| `AppStatusChip`（已有）→ 补 `busy/loading` 态 | 运行中状态片，走 4.1 `loading` 态 |

### 6.7 表单与选择控件规范

#### 6.7.1 `AppTextField` 完整规范（v4.1 对照 M3 text field 规范修订）

| 维度 | 规格 |
| --- | --- |
| 形态 | `Outlined`（**默认——M3 官方默认形态，密集表单低强调**）/ `Filled`（高强调、稀疏布局的关键项，如设置页顶部 API Key）/ `Bare`（`AppInputBar` 内部特例，无框，不进表单） |
| 高度 | 单行 52dp；多行 `minLines=1, maxLines` 按场景（备注 4 / 对话输入 6），随内容增高不跳动（baseline 对齐） |
| 圆角 | `radiusM`；搜索变体 `radiusXL`（全圆） |
| 描边 | 常态 1dp（`border` 色）→ **focused 2dp（`primary`）→ error 同 2dp（`danger`）**——描边宽度只在 focused/error 升到 2dp（M3 `boxStrokeWidthFocused` 模型） |
| 标签 | 浮动标签（focus/有值时上浮缩至 12sp）；focused 标签色 = `primary`（M3 `hintTextColor` 模型）；error 时标签同步转 `danger` |
| 附属 | `helperText`（12sp `textTertiary`，位于下 4dp）/ `errorText`（12sp `danger`，**出现时替换 helper** 且 100ms fade；**注册为 accessibility live region——读屏自动播报**）/ 字符计数（超限转 `danger` 独立 overflow 样式，仅传入 `maxLength` 时启用）/ `prefix`/`suffix` 槽（API Key 场景 `sk-` 前缀常显） |
| 图标 | leading 24dp（`textTertiary`，须填 contentDescription）；trailing 三种内建：clear（有值且 focused 时出现，44dp 触控区）/ **error ⚠（error 态自动出现，M3 `errorIconDrawable` 模型）** / password 可见性切换（密码字段内建） |
| 状态 | default / focused（`primary` 2dp 描边）/ error（`danger` 2dp 描边 + error 图标 + 文案）/ disabled（38%，走 4.1）——**无第五种状态**，改动走评审 |
| 键盘 | `keyboardOptions` 由调用方传；安全校验（数字/URL）由调用方语义决定，组件不管业务 |

#### 6.7.2 选择控件族

| 组件 | 规格 |
| --- | --- |
| `AppSwitch`（已有） | 52×32dp 轨道；选中 `primary`，关闭 `surfaceVariant`；态走 4.1 |
| `AppCheckbox` | 20dp；选中 `primary` 底 + 白勾；indeterminate 态（半选）预留 |
| `AppRadio` | 20dp；选中 `primary` 外环 + 内点；禁裸 `RadioButton` |
| `AppSearchField` | `AppTextField` 变体（`Outlined` + 全圆 + leading search 图标 + trailing clear），M2 会话列表直接复用 |

- 选择族禁裸用 M3 对应组件（`Checkbox`/`RadioButton`/`Switch` 裸 import 被
  现有 Material3 import 规则连带拦截）；接入 4.3 清单（appStateLayer + 交互态）。
- 表单布局：FormScaffold 内 label 左对齐顶置、字段全宽；两列仅 Expanded 端
  （6.4 断点）且限短字段（开关/选择）；错误文案不改变行高（预留 20dp helper 槽）。

### 6.8 消息链路 UI（Agent 对话流专项，D19/D20）

> 依据 2026-09 全网调研（ChatGPT/Claude/Perplexity/Cursor/Anthropic Console 模式库 +
> Claude Code/Cline/Aider 趋同分析）+ 用户四项拍板（工具卡运行中展开/思考折叠/
> 进度移动化变体/AI 全宽文档流）。消费 M0「事件→渲染块归约器」的输出。

#### 6.8.1 八类消息块

| # | 块类型 | 形态契约 |
| --- | --- | --- |
| 1 | **用户消息** | **右对齐气泡**（max 82% 宽，`primary` 底白字——会话内唯一气泡），附件预览条；长按 → 内联编辑重发（原消息保留删除线标记，M1 后排期） |
| 2 | **AI 正文** | **全宽文档流**（不进气泡——业界已收敛；`maxContentWidth` 居中由骨架管），markdown 稳定渲染（6.8.5）+ 活光标 |
| 3 | **思考块** | AI 紫（3.2 身份色）；**默认折叠一行**：「✦ 思考中…」（脉冲动画）→ 完成后「✦ 已思考 Ns」；点开全文（浅紫底卡） |
| 4 | **工具卡** | 见 6.8.2 注册表模式；状态徽章走 tool 四态色映射 |
| 5 | **审批卡** | 见 6.8.4，内联 PermissionGate |
| 6 | **阶段状态行** | 流式期间一行轻状态（「正在读取 event_store.kt…」），spatial fast 淡入淡出；阶段用领域语言命名 |
| 7 | **错误块** | 两型：**可恢复**（重试按钮 + 原因一行）/ **中断**（建议换策略 + 详情折叠）；danger 容器浅底 |
| 8 | **空状态** | 问候语 + 一句话定位 + **3 个建议 chips 竖排**（移动端横滑 chips 显廉价——调研结论）；新增会话首屏即此 |

#### 6.8.2 工具调用卡：注册表模式（核心组件 `AppToolCard`）

静态注册表 = 工具名 → (图标, 人类标题, 参数摘要提取器) 的单一映射（与
`core:mcp` 工具注册同构，MCP 外来工具走灰色兜底 + 名称字面量）：

| 工具族 | 标题模式 | 图标语义 |
| --- | --- | --- |
| 文件类（read/write/edit） | 读取/写入/编辑 + 路径副标题 | 文档/笔/diff |
| shell | 终端 + 命令首行截断 | 终端 |
| 搜索/网络 | 搜索 + query 截断 50 字 | 放大镜/地球 |
| MCP 外来 | 服务器名·工具名 | 插头 |

- **卡片三区**：头（图标 + 标题 + 状态徽章）/ 身（**关键参数摘要一行**，全文折叠）/ 脚（耗时 + 失败时重试链接）；**默认高度 ≤120dp**（10 步运行一屏可见）；
- **展开策略（已拍板）：运行中展开 + 结果流式进卡（替代黑盒 spinner），完成即自动折叠**为一行摘要；用户手动展开的卡片不自动收起（尊重显式意图）；
- 结果内联渲染按类型路由：diff（增删行用 success/danger 浅底形态）/ 代码块（mono + codeSurface）/ 文件路径卡 / 表格；纯文本结果截断 800 字符 + 省略（shell/文件 5000）——截断即信号，全文点开；
- **未审计内联 JSON 是反模式**：一切原始 JSON 只允许出现在折叠区内。

#### 6.8.3 执行组：连续块聚组（`AppBlockGroup`）

归约器输出的**连续 thinking/tool_use 块聚为一组**（text 块独立截断分组——
业界验证的 BlockGroup 算法）：

- 组壳：左缘 2dp 紫色条（AI 在场）+ 步数徽标「3 步」；
- 组内折叠态 = 全部子块折叠后的堆叠摘要；**任一子块运行中 → 组自动展开**；
- 会话流被 10+ 工具卡撑爆的问题由聚组兜底（默认一屏 = N 个折叠组）。

#### 6.8.4 审批卡（PermissionGate 内联形态）

- 触发：危险等级 ≥ 高的工具调用（M1 PermissionGate 设计）；
- **知情决策而非盲确认**：命令完整预览（mono）/ diff 侧栏对比（域视觉语义）/ 目标文件卡；
- 三选择竖排：拒绝（次钮）/ 仅本次允许（次钮）/ 本会话允许（实心主钮）——破坏性
  操作时主钮转 `danger` 并附后果一句话（6.6.1 标题三禁同样适用：不说「警告！」）；
- 审批卡**不因滚动消失**：置顶 sticky 于输入坞上方直到决策。

#### 6.8.5 流式渲染契约（ChatScaffold 内容区全局）

| 规则 | 规格 |
| --- | --- |
| 活光标 | 2dp×18dp 竖条 `primary` 色，脉冲 800ms；**流结束才移除**（非最后 token） |
| 稳定布局 | markdown 渐进渲染**零 layout shift**：代码块/表格/引用在起始 token 即渲染空壳（min-height 骨架），内容填充；禁「文本流重排跳变」 |
| Stop 同槽 | 发送键 ↔ stop 键**同槽位替换**（不新增按钮）；stop 触达面积 = 发送键同尺寸（42dp） |
| 停止保留 | 停止后已生成部分**保留在会话流**（附「已停止」状态行），非消失 |
| 首响占位 | 首 token 前：阶段状态行（6.8.1#6）+ 骨架 shimmer——**禁裸 spinner 空等** |
| 断流两型 | 可恢复（SSE 中断→自动重连提示 + 重试）/ 中断（超时→错误块 #7），UI 词汇区分 |

#### 6.8.6 进度摘要条 + 时间线抽屉（移动化进度面板，D20 已拍板）

Claude Code/Cursor/Cline/Aider 四家趋同的「进度面板」在 Compact 端的翻译
（独立第二面板在手机不成立——挤压会话区且与五型骨架冲突）：

- **置顶摘要条 `AppProgressSummary`**：多步任务运行时 sticky 于输入坞上方
  （与审批卡互斥，审批优先）；内容 =「N 步完成 · 当前动作领域语言短语」+
  细进度线（`primary`）；完成即整体淡出；
- **时间线抽屉**：点摘要条 → `AppModalSheet` 时间线（每步 = 折叠的工具卡，可
  逐个展开审计，底部「下载运行日志」）——「可导航的日志，不是进度条」；
- 单工具短任务（<3s）不出现摘要条，工具卡自足。

#### 6.8.7 空状态与追问 chips

- 空状态 = 问候（`display` 角色）+ 一句话产品定位（`body` `textTertiary`）+
  3 建议 chips 竖排（`surfaceV` 底、`radiusM`、44dp 高、左侧图标）；
- 每轮 AI 回复尾部可选追问 chips（≤3 个，`AppTopTabs` 同款滑动语义）——
  **v1 不做**（M2 首页一并排期），仅预留组件位。

#### 6.8.8 消息链路组件函数契约（落点：`components/messaging/`）

> 消费 `core:uistate` 的 `RenderBlock`（现状已产出 ViewModel 块）。**工具卡注册表与
> `core:mcp` 工具注册同构**；MCP 外来工具走灰色兜底（未命中注册表的工具名）。
> 折叠/展开状态由组件内部 `rememberSaveable` 持有（单一意图显式展开不自动收起，§6.8.2）。

```kotlin
// ── 工具卡注册表（静态单一映射）──
data class ToolCardSpec(
    val icon: ImageVector,
    val title: (icon?, name, localArgs) -> String,      // 人类标题模式
    val summary: (Map<String, JsonElement>) -> String,  // 关键参数摘要提取器（§6.8.2）
)
data class ToolCardRegistry(val specs: Map<String, ToolCardSpec>) // onUnknown 灰色兜底 + 名称字面量

@Composable
fun AppToolCard(
    block: RenderBlock.ToolInvocation,
    registry: ToolCardRegistry,
    modifier: Modifier = Modifier,                // 三区：头/身/脚；默认高 ≤120dp
    onApprove: (ToolCall, ApprovalScope) -> Unit,
    onDeny: (ToolCall) -> Unit,
)
// 展开策略：visible && RUNNING → 展开并流式进卡；完成后自动折叠为一行摘要；用户手动展开则不自动收起
// 结果内联按 ToolOutput 类型路由（diff/代码块/文件路径/表格）；原始 JSON 仅入折叠区

@Composable
fun AppBlockGroup(
    blocks: List<RenderBlock>,                    // 连续 thinking/tool_use 聚组（AppBlockGroupReducer 输出）
    registry: ToolCardRegistry,
    onApprove: (ToolCall, ApprovalScope) -> Unit,
    onDeny: (ToolCall) -> Unit,
    modifier: Modifier = Modifier,                // 组壳：左缘 2dp 紫条 + 「N 步」徽标；任子块 RUNNING → 自动展开
)

@Composable
fun AppApprovalCard(
    call: ToolCall, spec: ToolSpec, riskLabel: String,
    onApprove: (ApprovalScope) -> Unit, onDeny: () -> Unit,
    modifier: Modifier = Modifier,                // 三选择竖排；破坏性 main → danger + 后果一句；sticky 由骨架槽位承载
)

@Composable
fun AppProgressSummary(
    doneSteps: Int, totalSteps: Int?,
    currentLabel: String,                          // 阶段领域语言短语
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,                // 置顶摘要条 + 细进度线；完成整体淡出；<3s 短任务不出现
)
// 时间线抽屉 = AppModalSheet + 每步折叠工具卡 + 「下载运行日志」

@Composable
fun StreamEmittedCursor(active: Boolean, modifier: Modifier = Modifier)
// 活光标：2dp×18dp 竖条 primary 色，脉冲 800ms；流结束才移除（非最后 token，§6.8.5）
```

**状态机（render-side，`RenderBlockView` 改造）**：
`UserMessage`/`AssistantText` → 保留；`Thinking` → 折叠摘要「✦ 已思考 Ns」+ 点开全文；连续
thinking/tool_use 经 `AppBlockGroupReducer` 聚为 `RenderBlock.Group`（text 块独立截断分组）。

## 七、主题包（ThemePack）机制

### 7.1 运行时形态（落地契约：完整类型定义）

```
AppThemeSpec（运行时不可变数据）＝ 一套语义令牌取值
    ├─ id / name / schemaVersion
    ├─ light: AppTokens        ├─ dark: AppTokens
    └─ source: BUILT_IN | USER_IMPORTED

StyleController（:app 装配；designsystem 只定义接口与默认实现）
    ├─ spec: StateFlow<AppThemeSpec>
    ├─ darkMode: StateFlow<DarkMode>   // FOLLOW_SYSTEM | LIGHT | DARK（用户三态）
    └─ 持久化到 TableModule
```

**完整类型定义（落点：`theme/AppThemeSpec.kt`、`theme/StyleController.kt`）**

> 具名属性 = 编译期完整性：加令牌 = 加属性 + 明暗值 + 配对声明（§10.1 校验矩阵同步登记）。
> `@Immutable` 全覆盖，Spec 跨重组稳定引用（§8.1）。**可空字段只允许出现在 `Pair` 声明上，
> 不允许出现在 `AppTokens` 本体**——style 包差别的二元组，`AppColors` 只存解析完成的定值。

```kotlin
/** 明/暗两套值的载体；缺省 = 回退 brand 对应模式（§7.3.1） */
@Immutable
data class TokenPair<T>(val light: T, val dark: T)

@Immutable
data class AppThemeSpec(
    val id: String,
    val name: String,
    val schemaVersion: Int = 1,
    val light: AppTokens,
    val dark: AppTokens,
    val source: Source = Source.BUILT_IN,   // BUILT_IN | USER_IMPORTED
) { enum class Source { BUILT_IN, USER_IMPORTED } }

/** 一对角色渲染：AppTextStyle 枚举（§3.3，type 色）由 AppTextTone 决定，故这里只给字号行高 */
@Immutable
data class AppTokens(
    val colors: AppColors,        // §3.1 语义色面板（明暗已落在 TokenPair 外，此处为单板）
    val type: AppTypographyTokens,
    val motion: AppMotion,
    val radius: AppRadius,        // card/bubble 等 5 档——theme.json radius 覆盖组入口
)

/** 主题切换的读取源（designsystem 只定义接口 + 默认实现，装配在 :app） */
interface StyleController {
    val spec: StateFlow<AppThemeSpec>
    val darkMode: StateFlow<DarkMode>
    suspend fun setSpec(id: String)            // 从已加载的 ThemePackRegistry 取
    suspend fun setDarkMode(mode: DarkMode)
    suspend fun import(pack: ThemePackLoader.Result)  // 走 7.3 合并 + 7.4 校验
    fun registerPack(spec: AppThemeSpec)       // 进程内登记（含 brand 与解析成功的 import）
}

enum class DarkMode { FOLLOW_SYSTEM, LIGHT, DARK }

/** 默认实现：StateFlow + 持久化到 TableModule（§DATA_LAYER 注册制），:app 装配 */
val LocalStyleController = staticCompositionLocalOf<StyleController> {
    error("StyleController 未装配：请先在根 AppTheme 之前提供实例")
}

@Immutable
data class AppRadius(
    val card: Dp, val listItem: Dp, val chip: Dp, val sheet: Dp, val bubble: Dp,
) {
    fun toShapes(): Shapes = Shapes(
        extraSmall = RoundedCornerShape(card), small = RoundedCornerShape(card),
        medium = RoundedCornerShape(listItem), large = RoundedCornerShape(sheet),
        extraLarge = RoundedCornerShape(bubble),
    )
}
```

**装配与局部换肤（入口契约，落点 `theme/AppTheme.kt`）**

```kotlin
@Composable
fun AppTheme(
    spec: AppThemeSpec = LocalStyleController.current.spec.collectAsState().value,
    content: @Composable () -> Unit,
)
// 页面级局部换肤（不同设计的页面，至多一层，§8.2）：
AppTheme(ThemePacks.console) { ToolMarketScreen() }   // 组件层零改动
```

- `ThemePacks`：编译期内置包注册表（brand 常驻 + 预留 console 演示包），落点 `theme/ThemePacks.kt`；
- `AppTheme(spec)` 内部：`TokenPair` 按 `darkMode/spec.dark` 决出单板 → 经 `AppThemeBridge.domesticate(spec, board)`（§九）填满 M3 全槽位 → `CompositionLocalProvider`（`AppTokens` + 语义色 + 行为常量）→ `MaterialTheme(…)`。

### 7.2 theme.json v1 与 DTCG 的关系（D9）

v1 格式（扁平 + delta 覆盖）：

```json
{
  "id": "midnight",
  "name": "午夜",
  "schemaVersion": 1,
  "light": {
    "color": { "primary": "#7C5CFF", "surface": "#F7F7FA", "toolFailed": "#C2410C" },
    "radius": { "card": 20, "bubble": 22 }
  },
  "dark": {
    "color": { "primary": "#9B85FF", "surface": "#131318" }
  }
}
```

与 W3C DTCG 的关系：字段组织同构、机械映射可行（转换器 `scripts/dtcg_to_theme.py`
设计占位）。v1 不直接采用 DTCG 的三个理由：① 无 delta/继承语义（风格包被迫全量
声明）；② `$extensions`/别名引用链超出需要；③ 校验钩子需自有 schema。未来接
Figma Tokens 直通时加导入器，v1 格式不动。

### 7.3 delta 合并语义（精确规则）

合并发生在 **Spec 构造期**（loader 一次性完成），不在重组期：

1. light 与 dark **各自独立**与 brand 对应模式合并，互不渗透（dark 缺 `surface`
   继承 **brand 的 dark surface**）；
2. 令牌级覆盖：组名+令牌名匹配 → 覆盖；令牌名/组名未知 → **告警忽略**（进导入报告，
   前向兼容）；
3. 类型冲突（组错位/非法 hex）→ **整包拒载**，报告给出键路径（`dark.color.primary:
   非法 hex "abc"`）；
4. **不允许显式 null**：「回退」语义统一走「删掉这个键」；
5. 合并结果过 7.4 校验：**硬约束**失败 → 该令牌回退 brand 值并记报告；整包级失败
   （JSON 坏/schemaVersion 过高）→ 整包拒载。

### 7.4 校验与兜底

| 级别 | 内容 | 失败动作 |
| --- | --- | --- |
| 结构校验 | JSON 合法、schemaVersion ≤ 当前、必填字段 | 整包拒载 |
| 值校验 | hex 合法、radius/spacing 数值范围（0–40dp） | 整包拒载 |
| **A11y 硬约束** | 对比度配对（10.1）、字号下限（body ≥13sp） | 该令牌回退 brand 值，报告说明 |
| 可用性软约束 | 表面三色差异、divider 边界感 | 告警保留 |

校验器为纯 Kotlin（sRGB 线性化 + WCAG 公式），`designsystem/theme/validator/`，可 JVM 单测。

### 7.5 版本化与废弃策略

- `schemaVersion` 单调递增；高于当前 → 整包拒载（明确报错，不降级猜测）；
- 令牌**改名**：旧名 @Deprecated 两个发版周期，loader 维护兼容映射，到期移除并升版本；
- 令牌**语义变更**（同名不同义）禁止——加新令牌、废弃旧令牌，两步走。

## 八、运行时行为与性能

### 8.1 CompositionLocal 选型：`staticCompositionLocalOf`

读取处不订阅重组、值变更时**整棵树一次重建**——正是主题切换的期望语义：
日常读取零订阅开销；切换 = 一次原子重建，不存在半新半旧的中间态帧。

### 8.2 主题切换语义

- **全局切换**：`StyleController.spec` → 根部 `AppTheme` 收集 → 树重建。走
  CompositionLocal 更新而非 Activity 重建——状态零丢失、无黑屏闪烁；
- **深浅三态**：FOLLOW_SYSTEM / LIGHT / DARK，由 `StyleController.darkMode` 决定；
- **局部换肤嵌套**：允许页面级 `AppTheme(spec)` 覆盖，**至多一层**（评审约定）；
- 跨主题过渡动画：v1 不做（见开放问题）。

### 8.3 Dialog / Popup 作用域

Compose 的 Dialog/Popup 独立 composition 但**继承 CompositionLocal**，AppTokens
自然可达。window 级样式（遮罩、输入法）不走令牌——对策：`AppDialog`/`AppModalSheet`
封装（六.5，业务禁裸用，lint 拦截）。

### 8.4 解析与构建线程模型

- theme.json 解析 + 合并 + 校验：`Dispatchers.Default`（导入时）；
- 冷启动走构造期同步读（brand 包是编译期常量；用户包 Koin single 构造时同步读小 JSON，
  失败降级 brand）——**首帧即正确主题**；
- Spec 产物不可变，UI 线程只读。

## 九、Material3 桥接：source of truth 规则（D8）

### 9.1 权威映射表

| Material3 槽位 | 来源（语义令牌） |
| --- | --- |
| `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer` | `colors.primary` 组 |
| `surface` / `surfaceVariant` | `colors.surface` / `surfaceVariant` |
| `surfaceContainer*`（各档） | `colors.surfaceVariant` / `surfaceElevated` 派生 |
| `onSurface` | `colors.textPrimary` |
| `onSurfaceVariant` | `colors.textSecondary` |
| `outline` / `outlineVariant` | `colors.border` / `colors.divider` |
| `error` | `colors.danger` |
| `background` / `onBackground` | `colors.surface` / `colors.textPrimary` |
| `secondary*` / `tertiary*` | brand 固定派生自 primary（降饱和），**风格包不单独控制** |
| `typography.*`（8 槽） | type 角色映射 |
| `shapes.*`（5 档） | radius 档 |

### 9.2 谁读谁

- designsystem 组件内部：照常读 MaterialTheme（槽位已全量填充，读 M3 ≡ 读语义层）；
- 业务层：只读 `appTokens()` / `AppTextStyle` 角色（lint 保证）；
- **镜像断言单测**（12.4）锁死「M3 槽位 == 语义令牌」，缝隙即 CI 红。

## 十、可访问性工程（A11y 为硬约束，D10）

### 10.1 对比度（WCAG 2.1 相对亮度模型）

```
L = 0.2126·R' + 0.7152·G' + 0.0722·B'   （sRGB 线性化）
ratio = (L_light + 0.05) / (L_dark + 0.05)
```

配对矩阵见 3.2 表（`↔` 标注），loader 强制校验，brand 包由 12.2 单测锁死。

### 10.2 字体缩放（sp 与系统字号）

- 字号令牌全部 `sp`，天然跟随系统缩放；
- **铁律：包文本的容器高度必须 wrap / min-height，禁止固定 height**（组件内部保证）；
- body 类令牌下限 13sp（loader 校验）。

### 10.3 reduce motion（动效减弱）

- 读系统动画缩放（`ANIMATOR_DURATION_SCALE == 0`），`MotionResolver` 提供有效时长，
  ContentResolver 监听刷新；
- 五章全部转场模式与四章 pressed/dragged 缩放均经 resolver——系统减弱时一律直切。

### 10.4 触控与读屏

- `minTouchTarget=48dp` 不在 theme.json 可覆盖组（天然安全）；
- 结构组件统一 contentDescription 约定（返回/图标按钮必填），T8 实施时进组件 KDoc。

### 10.5 排版可访问性四要素（v4 吸收 W3C 文本间距标准）

大字号/无障碍排版模式下，文本间距按系统缩放后仍须满足（组件默认行高与字距
按此基线设定，字号缩放到 200% 不破版）：

| 要素 | 下限 | 落点 |
| --- | --- | --- |
| 行高 | ≥ 1.5 × 字号 | `body` 角色默认行高 1.5；标题 1.2 |
| 段后距 | ≥ 2.0 × 字号 | 富文本块（markdown 渲染）段落间距 |
| 字距 | ≥ 0.12 × 字号 | `label` 小字号角色（12sp 以下）默认带字距 |
| 词距 | ≥ 0.16 × 字号 | 西文混排断词兜底 |

另两条移动端排版铁律：**不依赖 hover 传达信息**（四章 hovered 态仅外设场景
增强，不是唯一通道）；**关键信息不进 placeholder**（placeholder 输入即消失，
读屏与老年模式不可靠——一律用常驻 label）。

## 十一、lint 守卫扩展

| 规则 | 拦截对象 | 提示 |
| --- | --- | --- |
| `DirectColorLiteral`（新） | 业务层 `Color(0x…)` / `Color(red=…)` | 「请使用 appTokens().colors.<语义名>」 |
| `RawTextStyleConstruction`（新） | 业务层 `TextStyle(...)` 直接构造 | 「请使用 AppTextStyle 角色」 |
| `ForbiddenWindowComponent`（新） | 业务层裸用 `Dialog` / `Popup` / `ModalBottomSheet` | 「请使用 AppDialog / AppModalSheet」 |
| `ForbiddenPlatformToast`（新） | 业务层裸用平台 `android.widget.Toast` / Compose `Snackbar` | 「请使用 AppToast / AppBanner（6.6.3）」 |
| `ForbiddenRawDropdown`（新） | 业务层裸用 `DropdownMenu` / `ExposedDropdownMenuBox` | 「请使用 AppDropdownMenu（6.6.2）」 |
| `ForbiddenRawTextField`（新） | 业务层裸用 `TextField` / `OutlinedTextField` | 「请使用 AppTextField（6.7.1）」 |
| `ForbiddenRawToolCard`（新） | 业务层裸用 `Card`/`Column` 手拼工具调用卡 / 审批卡 | 「请使用 AppToolCard / AppBlockGroup / AppApprovalCard（6.8）」 |
| `ForbiddenRawJsonRender`（新） | 业务层将工具原始 JSON/参数直接进 UI（未走注册表摘要提取） | 「工具结果须经注册表摘要路由，原始 JSON 仅入折叠区（6.8.2）」 |
| 现有三条 | Material3 import / 被禁 Composable / 裸 dp·sp | 不变 |

白名单：`com.deepcode.designsystem` 全包、`:lint` 自身。

## 十二、测试策略（把底线变成机器保证）

| # | 测试 | 层级 | 内容 |
| --- | --- | --- | --- |
| 12.1 | 面板完整性 | JVM 单测 | 反射遍历 AppColors/AppTokens 属性，断言 brand 明暗两套全部非默认值 |
| 12.2 | 对比度矩阵 | JVM 单测 | brand 包全配对跑 10.1 公式全绿；新令牌 PR 未更新矩阵即红 |
| 12.3 | loader 表驱动 | JVM 单测 | delta 合并（正常/未知键告警/类型冲突拒载/null 拒载/版本高拒载/越界回退）+ 导入报告内容 |
| 12.4 | M3 镜像断言 | Compose UI 测试 | `MaterialTheme.colorScheme.primary == appTokens().colors.primary` 等全槽位（9.1 表逐行） |
| 12.5 | 主题切换 | Compose UI 测试 | 切换 spec 生效、状态保留、FOLLOW_SYSTEM 响应 |
| 12.6 | 角色渲染快照 | 后期（可选） | 六角色 × 明暗快照（Paparazzi/Roborazzi） |
| 12.7 | 交互态一致 | Compose UI 测试 | 接入清单（4.3）组件逐个断言 pressed/hovered/disabled 的 overlay 行为存在（弃 ripple 后防回退） |
| 12.8 | 骨架槽位 | Compose UI 测试 | 五型骨架的槽位填充、insets 生效、AppTopTabs 指示器选中联动 |
| 12.9 | 浮层变体 | Compose UI 测试 | AppDialog 三变体（danger 钮色/Progress 无按钮/icon 色）参数化断言；AppToast 单条顶替 + 3s 消失；AppBanner 常驻 + 关闭；DropdownMenu 选中态与分组 |
| 12.10 | 消息链路组件 | Compose UI 测试 | AppToolCard 展开/折叠策略（运行中展开+完成折叠/手动不收起）、注册表命中 vs MCP 灰色兜底、AppBlockGroup 聚组 + 任子块 RUNNING 自动展开、AppProgressSummary 出现/淡出、活光标结束移除 |

## 十三、反模式与治理

### 13.1 语义膨胀（第一死因）

防线三道：**准入标准**（1.1）→ **组合优先于新增**（按钮禁用态 = `textTertiary` +
`surfaceVariant`；悬停/按下不是颜色令牌而是四章 overlay）→ **登记即文档**
（PR 改 theme 必须同步本文档对应表，否则不合并）。

### 13.2 原始值泄漏

业务层 lint 拦（十一）；designsystem 内部靠评审：字面量只允许出现在 theme 包的
brand 定义文件与 Primitive 层。

### 13.3 废弃周期

`@Deprecated(replaceWith = …)` → 存续两个发版周期（loader 兼容映射同步）→ 移除 +
schemaVersion 升 1。禁止原地改语义。

### 13.4 视觉趋势白名单 / 黑名单（v4 新增，D17）

基于 2026 移动 UI 趋势全网调研（M3 Expressive 官方、HIG 对照、行业趋势报告）。
立场：**趋势只进「排布方式与微交互」，不进「色板与骨架」**——后者已定稿，
不为流行翻新。

**白名单（按需采纳）**：

| 趋势 | 采纳方式 | 落点 |
| --- | --- | --- |
| Bento 模块化网格 | 内容区排布模式（大小错落圆角卡片），非新骨架 | `DetailScaffold`/`TabbedScaffold` 的内容槽排布选项；M2 首页/仪表盘启用 |
| 强调字体（emphasized） | 关键操作/未读信息字重 +1 档（可变字体 weight 轴） | `AppTextStyle` 增加 `emphasized` 修饰（标题/CTA/徽标可用，正文禁） |
| 功能微交互三件套 | shake / checkReveal / countUp（4.4） | 组件内实现 |
| 深色自适应 | 跟随系统 + 明暗双板（已有）+ OLED 纯黑可选（未来主题包） | 七章 theme.json 预留 `oled` 覆盖组 |
| 人性化微肌理 | AI 对话区背景可选极淡噪点纹理（<2% 对比度），中和生成式 UI 冰冷感 | 可选项，随 brand 包 beta 验证 |
| 形状对比引导焦点 | 强调元素打破相邻圆角节奏（如 FAB 全圆 vs 卡片 radiusL） | 评审执行，不进令牌 |

**黑名单（明确拒绝，评审直接打回）**：

| 趋势 | 拒绝理由 |
| --- | --- |
| 重度玻璃拟态 / Liquid Glass 大面积使用 | 文字密集屏对比度不可控；我们黑白灰体系靠清晰层级立身，毛玻璃与之一致性差；仅允许遮罩场景（AppDialog 遮罩已是半透明） |
| 新拟态（Neumorphism） | 可读性差、无障碍不友好、暗色难适配 |
| 高饱和多巴胺配色 / 大面积渐变 | 与「黑白灰主调、蓝紫点缀」品牌立场冲突 |
| 满屏粒子/超长炫技转场 | 违反 5.1「不自造转场模式」与 Calm Interface 原则 |

## 十四、实施清单（T8）

| 步骤 | 内容 | 验收 |
| --- | --- | --- |
| T8.1 令牌层扩展 | `AppTokens` 聚合 + 3.1–3.6 全部令牌（brand 色板落地）+ 角色枚举 + `dynamicColor` 删除 + **12.1/12.2 单测** | design-guard 全绿；M3 槽位按 9.1 全量映射 |
| T8.2 主题包机制（编译期） | `AppThemeSpec` + brand 包 + `StyleController` + 设置页切换 UI + `AppTheme(spec)` 参数化 + **12.4/12.5 测试** | 切换即时生效零状态丢失；镜像断言绿 |
| T8.5 行为层落地 | `appStateLayer()` 统一封装 + 全组件接入（4.3 清单）+ `AppTransitions` + Navigation 转场接线 + `AppTopTabs`/`AppNavBar`/`AppModalSheet`/`AppMediaBlock` + 骨架五型化 + insets 统一 + **浮层与表单子组件（6.6/6.6.4/6.7）：AppDialog 三变体 / AppDropdownMenu / AppMultiSelectSheet / AppToast / AppBanner / AppTextField 补全 / 选择族 / AppSearchField** + **消息链路组件（6.8）：`AppToolCard`/`AppBlockGroup`/`AppProgressSummary`/`AppApprovalCard`/流式活光标 Composer + 6.8.5 流式渲染契约** + **12.7/12.8/12.9/12.10 测试** | 全组件交互态一致；五型骨架承载 chat/settings 全页面回归；lint 八条新规则随 T8.4 就绪 |
| T8.3 运行时可插拔 | theme.json v1 解析 + 7.3 合并器 + 7.4 校验器 + `ThemePackLoader`（双根）+ 设置页导入 + **12.3 表驱动** | 手写 delta 包切换成功；非法包拒载并出报告 |
| T8.4 lint 扩展 | 十一 表**八**条新规则（§17.3） | design-guard 四 job 绿 |

依赖与节奏：T8.1 →（T8.2 ∥ T8.5）→ T8.3 → T8.4；T8.2 与 T8.5 相互独立可并行；
T8.3/T8.4 排在 M1 真实模型之后（真实模型优先级最高）。

## 十五、开放问题

1. 风格包分发渠道（v1 仅本地导入）；
2. 跨主题切换过渡动画——等真实需求评估；
3. 自定义字体文件进风格包（v2 议题）；
4. 大字号强制预览开关（验证 10.2，随 T8.2 顺手做）；
5. 图片类令牌（v1 不做）；
6. Expanded 双列/侧栏 rail 的实施时机（六.4：待平板验收场景出现）；
7. `AppTextStyle` 与 `AppTextTone` 合并与否——维持分离（色形正交），T8.1 终审；
8. 触觉反馈（长按/成功确认的 haptic）是否进行为层——待真实设备测试后定档。

## 十六、相关文档

- `ARCHITECTURE.md` §组件库是唯一出口 —— 本设计是该铁律的令牌化延伸
- `docs/TOOLS_SKILLS.md` §Skill 层 —— 风格包加载模式的参照系
- `designsystem/theme/`（令牌与 brand 包）、`designsystem/behavior/`（T8.5 新增：
  交互态/转场）、`designsystem/components/`（骨架五型）—— 实施落点

---

## 十七、落地实施映射（文件级）

> v4.2.1 深化新增。把前十六章的设计**平移成可评审的代码清单**：目标目录树、现状→目标
> 差距（§2.3）、每 T8 步的 new/modify/test 文件、依赖与 CI 门禁。**实现以本文 + 代码为准；
> 改设计先改本文，再改代码。**

### 17.1 目标目录树（designsystem 改造完形态）

```
designsystem/src/main/kotlin/com/deepcode/designsystem/
├── theme/                          ← T8.1/T8.2/T8.3（令牌 + 主题包 + bridge）
│   ├── AppTheme.kt                 (改)  parameterized AppTheme(spec)；删 dynamicColor
│   ├── AppTokens.kt                (增)  AppColors 全语义面板 + AppTypographyTokens + AppMotion
│   ├── AppBrandTokens.kt           (增)  brand 明暗双板（含 Primitive 灰阶/状态，§3.2）
│   ├── AppThemeSpec.kt             (增)  AppThemeSpec/TokenPair/AppRadius（§7.1）
│   ├── StyleController.kt          (增)  接口 + 默认实现 + LocalStyleController（§7.1）
│   ├── ThemePacks.kt               (增)  编译期内置包注册表（brand 常驻 + console 演示）
│   ├── AppThemeBridge.kt           (增)  M3 全槽位映射（§九.1）+ 镜像断言辅助
│   ├── Dimens.kt                   (改)  TypeScale 扩展为六角色 TextStyle（§3.3）；radius→AppRadius
│   ├── TypeRoles.kt                (增)  AppTextStyle 角色定稿 + AppTextTone（§3.3）
│   └── validator/                  (增)  T8.3：ThemeJsonCodec + ThemePackMerger + ThemeValidator
│         └── Contrast.kt           (增)  sRGB 线性化 + WCAG 公式（§10.1）
├── behavior/                       ← T8.5（交互态 + 动效编排）
│   ├── AppInteraction.kt           (增)  appStateLayer + 行为常量（§4.2）
│   ├── AppTransitions.kt           (增)  转场模式×档位绑定表常量对象（§5.2）
│   └── MotionResolver.kt           (增)  unified 出口；reduce-motion 直切（§10.3）
├── components/
│   ├── AppComponents.kt            (改)  按钮/卡片/状态片接入 appStateLayer；AppStatusChip 补 busy（4.3/6.6.4）
│   ├── AppInputs.kt                (改)  AppText 六角色；AppTextField 补全（§6.7.1）
│   ├── AppInputBar.kt              (改)  发纹/stop 同槽位替换、Ime 避让
│   ├── scaffold/                    (增)  五型骨架 + 顶/底栏 + ModalSheet（§6.5.1）
│   │   ├── ChatScaffold.kt / TabbedScaffold.kt / NavScaffold.kt
│   │   ├── DetailScaffold.kt / FormScaffold.kt
│   │   ├── AppTopTabs.kt / AppNavBar.kt / AppModalSheet.kt
│   │   └── AppScaffoldCompat.kt     (增)  现状 AppScaffold/WithState → 转发骨架变体（存量零回归）
│   └── overlay/
│       ├── AppDialog.kt            (增)  confirm/status/notice 三变体（§6.6.1）
│       ├── AppDropdownMenu.kt      (增)  Field/Button 两形态 + AppMultiSelectSheet（§6.6.2）
│       └── AppToast.kt / AppBanner.kt + ToastHost / BannerHost（§6.6.3）
│   └── form/
│       └── AppSearchField.kt + 选择族 AppCheckbox/AppRadio（§6.7.2）
│   └── messaging/                   (增)  T8.5（§6.8.8）
│       ├── AppToolCard.kt + ToolCardRegistry
│       ├── AppBlockGroup.kt + AppBlockGroupReducer（core:uistate）
│       ├── AppApprovalCard.kt / AppProgressSummary.kt / StreamEmittedCursor.kt
├── render/
│   ├── RenderBlockView.kt          (改)  按 §6.8 路由到 messaging 组件；AI 全宽流
│   └── MarkdownRenderer.kt         (增)  零 layout shift 渐进渲染（§6.8.5）
└── state/UiState.kt                (改/不变) 补 Empty 建议 chips 等（可选）
```

### 17.2 每 T8 步拆解（new / modify / test / CI 门禁）

> 节奏：T8.1 →（T8.2 ∥ T8.5）→ T8.3 → T8.4。T8.2 与 T8.5 独立可并；T8.3/T8.4 在 M1 真实模型之后。

| 步 | new | modify | test | CI 门禁 |
| --- | --- | --- | --- | --- |
| **T8.1 令牌层** | `AppTokens`/`AppBrandTokens`/`TypeRoles`/`AppTypographyTokens`/`AppMotion`；§3.2 色板落地 | `AppTheme.kt`（删 dynamicColor）、`Dimens.kt`(TypeScale→6 角色) | 12.1 面板完整性、12.2 对比度矩阵 | design-guard 绿；M3 槽位 §9.1 全映射 |
| **T8.2 主题包(编译期)** | `AppThemeSpec`/`TokenPair`/`AppRadius`/`StyleController`/`ThemePacks`/`AppThemeBridge` | `AppTheme(spec)`；`:app` StyleController 装配 + 设置页风格切换 UI | 12.4 M3 镜像断言、12.5 主题切换 | 镜像断言绿；切换零状态丢失 |
| **T8.5 行为层** | `behavior/` 三件 + `scaffold/` + `overlay/` + `form/` + `messaging/`（§17.1） | 存量组件接入 appStateLayer(4.3)；`RenderBlockView` 按 §6.8 路由；AppInputBar 同槽位 | 12.7 交互态一致、12.8 骨架槽位、12.9 浮层变体、12.10 消息链路 | chat/settings 零视觉回归；八条新 lint(§11) 随 T8.4 就绪 |
| **T8.3 运行时可插拔** | `validator/`：`ThemeJsonCodec`+`ThemePackMerger`+`ThemeValidator` | `ThemePackLoader`(assets/filesDir, 复用 SkillLoader) + 设置页导入 | 12.3 loader 表驱动 | 手写 delta 包切换成功；非法包拒载出报告 |
| **T8.4 lint 扩展** | **八**条新规则（§十一表） | `DesignSystemDetector.kt` / `DesignSystemIssueRegistry.kt` | lint 自测 + design-guard 用例 | design-guard 四 job 全绿 |

### 17.3 每 T8 步文件清单（细粒度，逐文件）

> 「落点」为 **新文件路径**；「改动」为现状文件。验收 = 该步所有 CI 门禁 + 单测绿色。

**T8.1 令牌层（12.1/12.2 为 JVM 单测，放 `designsystem/src/test`，纯 Kotlin 不依赖 Compose）落地文件：**
- 改 `theme/AppTheme.kt`：`AppColors` 扩展为 §3.1 全语义面板（品牌/表面/文本/边线/状态/业务 6 组）；
  `lightColorScheme()/darkColorScheme()` 从 `AppBrandTokens` 取色，**品牌色首进 M3**；删 `dynamicColor`。
- 改 `theme/Dimens.kt`：`TypeScale` → 六角色 `TextStyle`（§3.3：fontSans/fontMono + 字重/行高）；radius → `AppRadius`。
- 增 `theme/TypeRoles.kt`：`AppTextStyle` 六角色 + `AppTextTone`（与 `AppInputs.kt` 现有枚举对齐合并）。
- 增 `theme/AppTokens.kt`、`theme/AppBrandTokens.kt`；`local/` 提供 `appTokens()` 读取。
- 测试：`AppTokensTest`（12.1 反射全属性非默认）+ `ContrastMatrixTest`（12.2 WCAG，§10.1 公式）。

**T8.2 主题包（编译期）（12.4/12.5）落地文件：**
- 增 `theme/AppThemeSpec.kt`、`theme/StyleController.kt`、`theme/ThemePacks.kt`、`theme/AppThemeBridge.kt`。
- 改 `theme/AppTheme.kt` → `AppTheme(spec: AppThemeSpec = …)`；`:app` DI 装配 `StyleController`（StateFlow + TableModule 持久化）。
- 测试：`M3MirrorTest`（Compose UI，12.4 全槽位镜像断言）+ `ThemeSwitchTest`（12.5 切换生效/FOLLOW_SYSTEM）。

**T8.5 行为层 + 组件库（12.7/12.8/12.9/12.10）落地文件：**
- `behavior/`：`AppInteraction.kt`（appStateLayer）、`AppTransitions.kt`、`MotionResolver.kt`。
- `components/scaffold/`：五型骨架 + `AppTopTabs`/`AppNavBar`/`AppModalSheet` + `AppScaffoldCompat`。
- `components/overlay/`：`AppDialog`/`AppDropdownMenu`/`AppMultiSelectSheet`/`AppToast`/`AppBanner`（+Host）。
- `components/form/`：`AppTextField` 补全 + `AppSearchField` + `AppCheckbox`/`AppRadio`。
- `components/messaging/`：`AppToolCard`+`ToolCardRegistry`/`AppBlockGroup`+/`AppBlockGroupReducer`（core:uistate）/
  `AppApprovalCard`/`AppProgressSummary`/`StreamEmittedCursor` + `render/MarkdownRenderer.kt`。
- 改：存量组件接入 appStateLayer（§4.3 清单）；`render/RenderBlockView.kt` 按 §6.8 路由（AI 全宽流、思考「✦ 摘要」、
  思考/tool 聚组）；`AppInputBar` 发/stop 同槽位 + Ime 避让。
- 测试：`InteractionContractTest`(12.7) / `SkeletonSlotTest`(12.8) / `OverlayVariantTest`(12.9) / `MessagingLinkTest`(12.10)。

**T8.3 运行时可插拔（12.3）落地文件：**
- `theme/validator/ThemeJsonCodec.kt`（theme.json v1 解析）+ `ThemePackMerger.kt`（§7.3 delta）+ `ThemeValidator.kt`（§7.4）+ `Contrast.kt`。
- 增 `theme/ThemePackLoader.kt`（assets/filesDir 双根，复用 `SkillLoader` 模式）；`:app` 装配 `ThemePackLoader` +
  设置页导入入口（AI 能力名单）。
- 测试：`ThemeLoaderTableTest`（12.3 表驱动：正常 delta / 未知键告警 / 类型冲突拒载 / 显式 null 拒载 /
  版本过高拒载 / 越界回退 + 导入报告内容）。

**T8.4 lint 扩展（seq：T8.5 之后）落地文件：**
- 改 `lint/.../DesignSystemDetector.kt` + `DesignSystemIssueRegistry.kt`：注册 §十一 6 条新规则
  （`DirectColorLiteral`/`RawTextStyleConstruction`/`ForbiddenWindowComponent`/`ForbiddenPlatformToast`/
  `ForbiddenRawDropdown`/`ForbiddenRawTextField`/`ForbiddenRawToolCard`/`ForbiddenRawJsonRender`）。
- 测试：lint 单元用例 + 在 `feature/*` 引入违规样例断言 design-guard 红。

### 17.4 交叉与依赖

- `AppBlockGroupReducer` 属**纯 Kotlin**（`core:uistate`，可 JVM 单测）——对齐 M0 铁律，聚合逻辑不进 Compose；
- `ThemePackLoader` 复用 `SkillLoader` 的 assets/filesDir 双根模式（D5 显式对齐）；
- `RenderBlock.ToolInvocation` 已携带 `kind`（ToolKind）——工具卡图标可先按 kind 兜底，注册表按 name 精确覆盖；
- 消息链路消费 `core:uistate` 的 `RenderBlock`，UI 层**零改 core**；只新增 `RenderBlock.Group` 以承载聚组，
  需在 `core:uistate` 补块类型 + reducer 分支（纯 Kotlin）。
- lint 白名单保持 `com.deepcode.designsystem` 全包 + `:lint` 自身（§十一），新容器/行为包天然豁免。

### 17.5 验收口径（全量落地完成判定）

1. `designs-guard` 四 job（core-test / android-build / design-guard / release-build）全绿；
2. `ChatScreen`/`SettingsScreen` 迁移到新骨架后**零视觉回归**（用 `design-system-showcase.html` 做基线对照）；
3. 手写 theme.json delta 包导入成功、非法包拒载并出报告（12.3 通过）；
4. 八条新 lint 规则分别在 `feature/*` 植入违规样例可按预期命中；
5. 语义面板/对比度矩阵单测绿（12.1/12.2），M3 镜像断言绿（12.4）。
