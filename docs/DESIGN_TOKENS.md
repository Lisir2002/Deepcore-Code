# DESIGN_TOKENS.md — UI 令牌体系与主题包设计（designsystem v3）

> 状态：**设计定稿 v3（2026-09-01：深化 + brand 色板 + 行为层三系统），未实施**。
> 实施清单见 十四（T8.1–T8.5）。决策记录见 1.3（D5–D15）；本文档是 `designsystem`
> 模块从「单主题组件库」演进为「多风格包运行时」的唯一权威设计。

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

### 3.5 动效档位（编排的输入，见 五）

| 令牌 | 基线 | 用途 |
| --- | --- | --- |
| `fast=120ms` / `normal=220ms` / `slow=320ms` | 微交互 / 展开·切换 / 转场 |
| `easingStandard` / `easingEmphasized` | Compose 标准曲线 |

全部经 `MotionResolver`（10.3）生效；theme.json v1 不开放动效覆盖。

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

### 4.2 实现契约：`Modifier.appStateLayer()`

```kotlin
// designsystem 内部唯一实现；组件只传 InteractionSource 与形状
fun Modifier.appStateLayer(
    interactionSource: InteractionSource,
    shape: Shape,
    enabled: Boolean = true,
): Modifier
```

- overlay 色 = 组件当前**内容前景色**的 α 叠加（onColor 模型）——任何主题包、任何
  语义底色下自动成立，**交互态因此不需要进颜色令牌面板**；
- `pressed` 缩放与 `dragged` 抬升为内置常量，不暴露给组件层；
- **弃用 ripple（D12）**：`AppButton` 等封装里 M3 组件的 `indication` 参数统一传
  `appStateLayer` 生成的 indication，ripple 不再出现；
- 交互态常量（8%/12%/38%/scale 值）定义在 designsystem/theme/behavior，属行为层
  常量非颜色令牌，改动走评审（与令牌同流程）。

### 4.3 接入清单

`AppPrimaryButton` / `AppTextButton` / `AppCard`（可点击变体）/ `AppTextField` /
`AppSwitch` / `AppStatusChip` / `AppTopTabs`（六.2）/ `AppNavBar`（六.3）/
`AppDropdownMenu` 触发器与条目（6.6.2）/ `AppBanner`（6.6.3）/
`AppCheckbox` / `AppRadio` / `AppSearchField`（6.7.2）——
T8.5 全量接入；新增组件 PR 必须含 appStateLayer 接入，否则 design-guard 红。

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
- Expanded（>840dp）：`ChatScaffold` 预留会话列表侧栏位（双列），`maxContentWidth`
  居中；`NavScaffold` 底栏转侧栏（rail）——**v1 只做布局位预留与居中，侧栏/rail
  实施排 T8.5 之后按需启动**（当前无平板验收设备，避免无验收的复杂度）。

### 6.5 结构组件清单（T8.5 新建/补全）

| 组件 | 性质 | 说明 |
| --- | --- | --- |
| `AppTopTabs` / `AppNavBar` / `AppModalSheet` | 新建 | 顶栏 tab / 底栏导航 / 模态壳（转场接 5.2） |
| `AppDialog`（含三语义变体，见 6.6）/ `AppMediaBlock` | 新建 | 对话框壳（禁裸 Dialog，lint）/ 媒体比例块 |
| `AppScaffold`（五型化）/ `AppInputBar` | 改造 | 拆分为骨架变体；insets 统一进骨架 |

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

#### 6.6.1 `AppDialog`：一个壳，三个语义变体

统一壳规格（三变体共享）：宽 `min(内容, 320dp)`（Expanded 上限 420dp）、
圆角 `radiusXL`、遮罩 `black @ 40%`、转场 `DialogShow`（5.2，fast 档）、
内容边距 24dp、标题 `titleMedium` + 正文 `bodyMedium`、海拔 `surfaceElevated`。

| 变体 | 语义 | 布局契约 | 状态色 |
| --- | --- | --- | --- |
| **确认** `confirm()` | 破坏性/不可逆操作前置确认 | 双钮横排右对齐：文字钮「取消」+ 实心钮「确认」；两钮间距 `spaceS`；危险操作钮 ≤1 个 | `danger=true` 时实心钮转 `danger` 底白字（删除 MCP 服务器/撤销授权/清空会话） |
| **状态** `status()` | 进度/成功/失败回执 | `state: Progress/Success/Failure` 单枚举驱动；Progress 只渲染转圈 + 文案**且不出按钮**（防等待中误触），Success/Failure 出单「知道了」钮；图标位 40dp 圆底 + 白图标 | Progress→`primary` / Success→`success` / Failure→`danger` |
| **提示** `notice()` | 纯信息告知（版本说明/条款） | 单「知道了」钮或自定义 actions；可承载富内容槽（长文滚动区 `maxHeight = 60%` 屏高） | 中性，不着状态色 |

三钮以上一律竖排右对齐文字钮（不再横排拥挤）；变体间**不允许混合发明**
（「确认 + 进度条」混合体 = 违规，评审兜底）。

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
- 禁裸 `ExposedDropdownMenuBox` / `DropdownMenu`（lint `ForbiddenRawDropdown`）。

#### 6.6.3 轻提示体系：`AppToast` / `AppBanner`

| 组件 | 位置 | 消失规则 | 承载内容 |
| --- | --- | --- | --- |
| `AppToast` | 底部悬浮（NavScaffold 内置在底栏上方 inset，业务零感知） | **3s 自动**；同屏最多 1 条（新 toast 直接顶替旧条，不排队）；点击 toast 本体不消失 | 单行回执 + 可选动作钮（「撤销」）；`level: neutral/success/danger` 三色 |
| `AppBanner` | 内容区顶部（骨架槽位，`BannerHost` 统一管理） | **常驻**直到用户关闭或状态解除；不自动消失 | 标题 + 可选正文 + 可选动作；`level: info/success/warning/danger` 四色 + 40dp 图标位 |

- Toast 形态：圆角 `radiusM`、深色反相底（`inverseSurface` 系，浅深色下都够对比）、
  高 ≥44dp、左右边距 `screenPaddingHorizontal`、多行截断 2 行省略；
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

#### 6.7.1 `AppTextField` 完整规范（当前仅有壳，T8.5 补全）

| 维度 | 规格 |
| --- | --- |
| 形态 | `Filled`（默认，FormScaffold 内）/ `Outlined`（彩色底嵌入时）/ `Bare`（`AppInputBar` 内部，无框） |
| 高度 | 单行 52dp；多行 `minLines=1, maxLines` 按场景（备注 4 / 对话输入 6），随内容增高不跳动（baseline 对齐） |
| 圆角 | `radiusM`；搜索变体 `radiusXL`（全圆） |
| 标签 | Filled = 浮动标签（focus/有值时上浮 10sp）；Outlined = 常驻左上外挂 12sp |
| 附属 | `helperText`（12sp `textTertiary`，位于下 4dp）/ `errorText`（12sp `danger`，**出现时替换 helper** 且 100ms fade，同时边框+标签转 `danger`）/ 字符计数（trailing，超限转 `danger`，仅传入 `maxLength` 时启用） |
| 图标 | leading 24dp（`textTertiary`）；trailing = clear 按钮（有值且 focused 时出现，44dp 触控区） |
| 状态 | default / focused（`primary` 2dp 描边）/ error（`danger` 1dp 描边 + 文案）/ disabled（38%，走 4.1）——**无第五种状态**，改动走评审 |
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

## 七、主题包（ThemePack）机制

### 7.1 运行时形态

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

```kotlin
@Composable
fun AppTheme(
    spec: AppThemeSpec = LocalStyleController.current.spec.collectAsState().value,
    content: @Composable () -> Unit,
)
// 页面级局部换肤（不同设计的页面）：
AppTheme(ThemePacks.console) { ToolMarketScreen() }   // 组件层零改动
```

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

## 十一、lint 守卫扩展

| 规则 | 拦截对象 | 提示 |
| --- | --- | --- |
| `DirectColorLiteral`（新） | 业务层 `Color(0x…)` / `Color(red=…)` | 「请使用 appTokens().colors.<语义名>」 |
| `RawTextStyleConstruction`（新） | 业务层 `TextStyle(...)` 直接构造 | 「请使用 AppTextStyle 角色」 |
| `ForbiddenWindowComponent`（新） | 业务层裸用 `Dialog` / `Popup` / `ModalBottomSheet` | 「请使用 AppDialog / AppModalSheet」 |
| `ForbiddenPlatformToast`（新） | 业务层裸用平台 `android.widget.Toast` / Compose `Snackbar` | 「请使用 AppToast / AppBanner（6.6.3）」 |
| `ForbiddenRawDropdown`（新） | 业务层裸用 `DropdownMenu` / `ExposedDropdownMenuBox` | 「请使用 AppDropdownMenu（6.6.2）」 |
| `ForbiddenRawTextField`（新） | 业务层裸用 `TextField` / `OutlinedTextField` | 「请使用 AppTextField（6.7.1）」 |
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

## 十四、实施清单（T8）

| 步骤 | 内容 | 验收 |
| --- | --- | --- |
| T8.1 令牌层扩展 | `AppTokens` 聚合 + 3.1–3.6 全部令牌（brand 色板落地）+ 角色枚举 + `dynamicColor` 删除 + **12.1/12.2 单测** | design-guard 全绿；M3 槽位按 9.1 全量映射 |
| T8.2 主题包机制（编译期） | `AppThemeSpec` + brand 包 + `StyleController` + 设置页切换 UI + `AppTheme(spec)` 参数化 + **12.4/12.5 测试** | 切换即时生效零状态丢失；镜像断言绿 |
| T8.5 行为层落地 | `appStateLayer()` 统一封装 + 全组件接入（4.3 清单）+ `AppTransitions` + Navigation 转场接线 + `AppTopTabs`/`AppNavBar`/`AppModalSheet`/`AppMediaBlock` + 骨架五型化 + insets 统一 + **浮层与表单子组件（6.6/6.6.4/6.7）：AppDialog 三变体 / AppDropdownMenu / AppMultiSelectSheet / AppToast / AppBanner / AppTextField 补全 / 选择族 / AppSearchField** + **12.7/12.8/12.9 测试** | 全组件交互态一致；五型骨架承载 chat/settings 全页面回归；lint 四条新规则随 T8.4 就绪 |
| T8.3 运行时可插拔 | theme.json v1 解析 + 7.3 合并器 + 7.4 校验器 + `ThemePackLoader`（双根）+ 设置页导入 + **12.3 表驱动** | 手写 delta 包切换成功；非法包拒载并出报告 |
| T8.4 lint 扩展 | 十一 表三条新规则 | design-guard 四 job 绿 |

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
