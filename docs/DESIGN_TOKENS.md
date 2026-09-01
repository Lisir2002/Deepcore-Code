# DESIGN_TOKENS.md — UI 令牌体系与主题包设计（designsystem v2）

> 状态：**设计定稿 v2（2026-09-01 深化），未实施**。实施清单见 十一（T8.1–T8.4）。
> 决策记录见 1.3（D5–D11）；本文档是 `designsystem` 模块从「单主题组件库」演进为
> 「多风格包运行时」的唯一权威设计。

## 一、定位与铁律

### 1.1 三层令牌模型与各层准入标准

延续业界通用分层（Material 3 / W3C Design Tokens 的实践收敛），
**令牌只允许从上往下单向依赖**：

```
Primitive（原始层）    与语义无关的原子值：色阶、字号/字重/字族、间距基数、圆角档、动效时长
                       —— Dimens / TypeScale 已是这一层，补字重字族
     ↓ 取值
Semantic（语义层）     按「用途」命名：color.surface、type.heading、radius.card、motion.page
                       —— 业务代码唯一允许引用的层；明暗两套取值由主题包提供
     ↓ 消费
Component（组件层）    AppCard / AppScaffold 等组件内部，只读语义层（经 M3 派生，见 六）
```

**准入标准**（评审时逐条对照，防止体系劣化）：

| 层 | 准入标准 | 反例（不允许入层） |
| --- | --- | --- |
| Primitive | ① 与用途无关（换一个语义仍可复用）② 有明确物理单位 ③ 被至少两个语义引用才值得存在 | `brandPurple300`（带用途的"原始值"实为语义） |
| Semantic | ① 用途唯一且长期稳定 ② **组合优先于新增**（见 十.1）③ 明暗两套都能给出可校验的值 ④ 不绑定具体组件 | `settingsCardBackground`（绑组件）、`hoverBlue`（绑交互态+颜色，应组合 status 色 + 交互态 API） |
| Component | 不新增令牌，只消费语义层 | 组件内部参数默认值写死颜色/尺寸 |

### 1.2 铁律

1. **业务层只写语义名**：页面代码出现 `Color(0x…)` / 裸 `dp` / 自拼 `TextStyle` 即为违规
   （lint 拦截范围见 八）。
2. **令牌先注册后使用**：新增语义令牌必须在 designsystem/theme 与本文档三.1 表**同时**登记
   （含明暗默认值与对比度配对声明），PR 缺文档即不合并。
3. **组件库是唯一出口**：业务层不经 designsystem 组件与令牌触碰任何 Compose 原生样式 API
   （沿用 agent.md 原铁律）。
4. **主题包是数据，不是代码**：风格差异全部落在令牌取值上；布局结构差异走组件参数，
   风格包不允许携带逻辑、代码、图片（v1）。
5. **A11y 是硬约束不是建议**：对比度、触控目标、字号缩放、reduce-motion 四类底线
   风格包不可越（见 七），违反即拒载对应令牌，机器保证而非人工抽查。

### 1.3 决策记录

| # | 决策 | 理由 |
| --- | --- | --- |
| D5 | 多主题形态 = **风格包：可切换 + 可插拔 + 高度自定义**；运行时机制，载体与加载模式向 Agent Skills 看齐 | 第三方/后续页面可自带设计；`SkillLoader` 已有同型先例 |
| D6 | **dynamicColor（Material You 取色）默认关，参数从 AppTheme 删除** | 品牌一致性优先；与令牌体系冲突。将来做成某风格包的可选属性再议 |
| D7 | 节奏 = 先定稿后实施，T8.1–T8.4 分步，每步 CI 全绿再进下一步 | UI 层全局性改动，避免大爆炸重构 |
| D8 | **语义令牌是唯一 source of truth；MaterialTheme 是单向派生视图**。designsystem 组件内部照常读 MaterialTheme，但 M3 全部槽位必须由语义令牌映射填充，并做镜像断言单测（九.4） | 组件代码保持 Compose 惯用法零迁移成本；同时杜绝「M3 默认值缝隙」（未映射槽位悄悄用 M3 出厂色）。两套真理是主题体系最常见的腐化起点，规则必须一次写死 |
| D9 | theme.json v1 用**简化自有格式**（扁平 + delta 覆盖），但字段组织与 W3C DTCG 保持机械可映射；v2 如需 Figma 直通，加导入器而非改 v1 格式 | DTCG 无继承/delta 语义、无校验扩展点，直接采用会拖累 4.3 的兜底设计；但放弃 DTCG 生态（Figma Tokens / Style Dictionary）可惜，可映射性是两全解 |
| D10 | **reduce-motion / 字体缩放 / 触控目标由系统解析，风格包不可覆盖**；风格包动效令牌必须经 MotionResolver | 无障碍底线。风格作者可以不懂 A11y，但体系必须兜住 |
| D11 | **语义令牌新增走评审**（本文档登记 + 明暗值 + 配对声明三件套）；组合优先于新增 | 语义面板失控（200 个语义色）是所有设计系统的死法，防线前移到准入 |

## 二、现状盘点

### 2.1 已就位（不用改）

- `Dimens`：间距 7 档、圆角 5 档、图标 3 档、`minTouchTarget`、`maxContentWidth`；
- `TypeScale`：字号 10 档（含 code 字号与行高）；
- `AppColors`：9 个聊天业务语义色，明暗两套；`AppTextStyle` / `AppTextTone` 语义枚举；
- `AppTypography` / `AppShapes` → Material3 桥接；`AppTheme` 全 App 唯一入口；
- `:lint` 守卫：拦 Material3 import / 自建被禁组件 / 硬编码 dp·sp（已生效）。

### 2.2 缺口（本设计补齐）

1. 品牌主色无入口（`lightColorScheme()` 空参，品牌色从未配置）；
2. 字体令牌只有字号（无字重/行高/字族）；
3. 通用语义色面板缺失（只有聊天业务色）；
4. 主题写死单套（无主题参数）；
5. 动效令牌为零；
6. lint 不拦颜色字面量；
7. M3 槽位未全覆盖（D8 所述「缝隙」此时已存在：secondary/tertiary/error 等全是 M3 出厂值）。

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
    // spacing/radius 维持 Dimens 现有 object（编译期常量，无明暗之分），不进运行时面板——
    // 少数风格包想改圆角，进 theme.json 的 radius 覆盖组（v1），解析后生成派生 Shapes
)
```

要点：

- **data class 具名属性 = 编译期完整性**：`appTokens().colors.tetxtPrimary` 拼错直接编译失败；
  新令牌漏配明暗值 = 构造函数缺参编译失败。这是「先注册后使用」的机器保证，
  优于 JSON schema 校验（JSON 是风格包的形态，Kotlin 是体系的形态）。
- `@Immutable` 全覆盖：Spec 对象跨重组稳定引用，配合 五.1 的 CompositionLocal 选型零额外开销。
- 颜色类型不引入 value class 包装：`Color` 本身不可变且 Compose 惯用；**安全边界在 lint**
  （业务层禁止构造 Color），不在类型层（过度设计）。

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
| `gray-900` | `#131417` | — | `surface` / `codeBorder`(底) |
| `ink-900` | `#17181C` | `textPrimary` | — |
| `ink-50` | `#F2F3F5` | — | `textPrimary` |
| `gray-500d` | `#A6ABB7` | — | `textSecondary` |
| `gray-600d` | `#6B7180` | — | `textTertiary` |

（暗侧文本/边线值与亮侧同名令牌共用槽位，上表 `*d` 为 Dark 取值的别名标注。）

**品牌点缀与状态**

| 组 | Light | Dark | 用途边界 |
| --- | --- | --- | --- |
| 蓝（操作） | `primary #2563EB`（白字 5.1:1）/ `primaryContainer #EFF3FF` | `primary #7A93FF`（深字 `#0D1230`，6.0:1）/ `primaryContainer #1B2A5E` | 只出现在「可操作」处：主按钮/链接/选中/运行中 |
| 紫（AI 身份） | `#7C3AED` / 容器 `#F3EFFE` | `#A78BFA` / 容器 `#221B38` | 专属 AI 语义：思考流/技能徽标/agent 标识；与蓝形成功能区分 |
| `success` | `#16A34A`（底 `#E9F9EF`） | `#4ADE80`（底 `#12291B`） | 状态 |
| `warning` | `#D97706`（底 `#FDF3E3`） | `#FBBF24`（底 `#2E2210`） | 等待/确认 |
| `danger` | `#DC2626`（底 `#FDECEC`） | `#F87171`（底 `#331A1A`） | 失败/删除 |
| `info` | v1 复用 `primary` 蓝 | 同左 | 预留槽位 |

**业务色映射**（聊天气质由此而来）：`toolRunning→primary`、`toolSuccess→success`、
`toolFailed→danger`、`toolAwaiting→warning`、`thinking→紫`（AI 身份色的核心落点）、
`diffAdd/diffRemove→success/danger 的浅底形态`、`codeSurface/codeBorder→gray-50/100（L）·
gray-850/750（D）`。

设计原则：灰阶负责结构与留白，**蓝 = 可操作，紫 = AI，红绿黄 = 状态**，三者互不越界。
深色模式 primary 弃用「亮蓝+白字」（4.0:1 不达标），取「亮蓝 #7A93FF + 深字」
（6.0:1）。所有配对过 7.1 矩阵校验（9.2 单测锁死）。

（下表为语义面板的完整清单与配对声明——）

| 组 | 令牌 | 说明与配对声明（↔ 为对比度校验对，见 7.1） |
| --- | --- | --- |
| 品牌 | `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer` | 主操作色；`onPrimary ↔ primary` ≥4.5 |
| 表面 | `surface` / `surfaceVariant` / `surfaceElevated` | 页面底 / 卡片底 / 浮层底；三者相互差异 ≥1.2:1（软约束，告警不拒载） |
| 文本 | `textPrimary` / `textSecondary` / `textTertiary` / `textInverse` | `textPrimary ↔ surface` ≥4.5；`textSecondary ↔ surface` ≥4.5；`textTertiary ↔ surface` ≥3（大字号/弱信息豁免档） |
| 边线 | `divider` / `border` | 无对比度硬约束，与 surface 差异 ≥1.2:1 告警 |
| 状态 | `success` / `warning` / `danger` / `info` | 作文本使用时 ↔ surface ≥4.5（loader 校验），作底色时配 `onStatus` 文本由组件固定白/黑（v1 不开放覆盖） |
| 业务保留 | `diffAdd` / `diffRemove` / `toolRunning` / `toolSuccess` / `toolFailed` / `toolAwaiting` / `thinking` / `codeSurface` / `codeBorder` | 聊天场景名不变（避免大迁移）；实现上 `toolSuccess` 等默认映射自状态组，风格包可单独覆盖 |

### 3.3 字体与角色设计

| 维度 | 令牌 | 基线 |
| --- | --- | --- |
| 字族 | `fontSans` / `fontMono` | 系统默认；等宽用于代码块与命令输出。自定义字体文件 v1 不开放（许可与包体），见 开放问题 |
| 字重 | `wRegular=400` / `wMedium=500` / `wSemibold=600` / `wBold=700` | 只在角色定义内部使用，页面不触达 |
| 行高 | 与字号配对：标题 1.2 / 正文 1.4 / 代码 1.5 | 收敛现有 `code 12.5/18≈1.44` → 1.5 |

**角色枚举是字体的唯一出口**（`AppTextStyle.Title/SectionHeader/Body/Label/Caption/Code`），
角色内部组合字号×字重×行高×字族。禁止页面自由组合是为了把组合爆炸拦在设计层——
六角色 × 明暗 × 缩放已经是全矩阵，开放组合后无法保证每个组合都被测过。
角色与 `AppTextTone`（颜色语义）**保持分离**：`AppText(style = Title, tone = Tertiary)`
——色与形正交，避免 6×5=30 个组合枚举。

### 3.4 间距与圆角

维持 `Dimens` 现有档位不重命名（存量零迁移）。语义别名按需追加进同文件同 lint 保护。
圆角进 theme.json 覆盖组时只允许四档 + bubble（风格作者不需要第 7 档自由度）。

### 3.5 动效

| 令牌 | 基线 | 用途 |
| --- | --- | --- |
| `fast=120ms` / `normal=220ms` / `slow=320ms` | 微交互 / 展开·切换 / 转场 |
| `easingStandard` / `easingEmphasized` | Compose 标准曲线 |

全部经 `MotionResolver`（7.3）：系统 reduce-motion 时一律归零直切。
theme.json v1 **不开放**动效覆盖（v2 再议），令牌只内置于 brand 包。

### 3.6 布局结构

不做栅格（对话流不适用）。结构一致性 = 结构组件槽位约定（AppScaffold）+ 尺寸令牌
（`screenPaddingHorizontal` / `maxContentWidth` / `BLOCK_GAP`）。
新增结构模式 = 给 designsystem 加结构组件，不是加令牌。

## 四、主题包（ThemePack）机制

### 4.1 运行时形态

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

### 4.2 theme.json v1 与 DTCG 的关系（D9）

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

与 W3C DTCG（`{"color":{"primary":{"$value":"#…","$type":"color"}}}`）的关系：
**字段组织同构（组名/令牌名一致），机械映射可行**——转换器是一个纯函数脚本
（`scripts/dtcg_to_theme.py` 设计占位）。v1 不直接采用 DTCG 的三个理由：
① DTCG 无 delta/继承语义，风格包将被迫全量声明（60+ 行起步，劝退作者）；
② DTCG 的 `$extensions`/别名引用链超出我们需要的复杂度；③ 校验钩子（4.3）需要
自有 schema。若未来要接 Figma Tokens 直通，加导入器（DTCG → 内部模型），
v1 文件格式不动。

### 4.3 delta 合并语义（精确规则）

合并发生在 **Spec 构造期**（loader 一次性完成），不在重组期：

1. light 与 dark **各自独立**与 brand 对应模式合并，互不渗透
   （dark 缺 `surface` 时继承 **brand 的 dark surface**，而非 light 的）；
2. 令牌级覆盖：组名+令牌名都匹配 → 覆盖；组名匹配、令牌名未知 → **告警忽略**
   （进导入报告，前向兼容新版本令牌）；组名未知 → 同上；
3. 类型冲突（color 组里出现 spacing 组令牌 / 值不是合法 hex）→ **拒载整个包**，
   导入报告给出精确键路径（`dark.color.primary: 非法 hex "abc"`）；
4. **不允许显式 null**（`"primary": null`）：「回退」语义统一走「删掉这个键」，
   避免 unset 语义在 delta 体系里产生两层含义；
5. 合并结果必须通过 4.4 校验，任何**硬约束**失败 → 该令牌回退 brand 值并记报告；
   整包失败（JSON 解析/schemaVersion 过高）→ 整包拒载。

### 4.4 校验与兜底

| 级别 | 内容 | 失败动作 |
| --- | --- | --- |
| 结构校验 | JSON 合法、schemaVersion ≤ 当前、必填字段（id/name） | 整包拒载 |
| 值校验 | hex 合法、radius/spacing 数值范围（0–40dp 内） | 整包拒载 |
| **A11y 硬约束** | 对比度配对（7.1 矩阵）、触控目标（不可被风格包触达，天然安全）、字号下限（body 类令牌 ≥13sp） | **该令牌回退 brand 值**，报告说明 |
| 可用性软约束 | 表面三色差异、divider 边界感 | 告警保留，报告提示 |

校验器为纯 Kotlin 数学（sRGB 线性化 + WCAG 公式，不依赖 Android API），
放 `designsystem/theme/validator/`，可直接 JVM 单测。

### 4.5 版本化与废弃策略

- `schemaVersion` 单调递增；loader 对「高于当前版本」整包拒载（明确报错，不做降级猜测）；
- 语义令牌**改名**：旧名保留 @Deprecated 两个发版周期，loader 维护旧名→新名兼容映射，
  到期移除并升 schemaVersion；
- 令牌**语义变更**（同名不同义）禁止——加新令牌、废弃旧令牌，两步走。

## 五、运行时行为与性能

### 5.1 CompositionLocal 选型：`staticCompositionLocalOf`

读取处不订阅重组、值变更时**整棵树一次重建**——这正是主题切换的期望语义：
- 日常读取零订阅开销（每帧读取令牌的组件极多，若用 `compositionLocalOf`，
  切换时分散重组的簿记成本反而更高，且令牌对象 @Immutable 没有细粒度收益）；
- 切换 = 一次原子重建，不存在「半新半旧」的中间态帧。

### 5.2 主题切换语义

- **全局切换**：`StyleController.spec` → 根部 `AppTheme` 收集 → 树重建。
  走 CompositionLocal 更新而非 Activity 重建（`recreate()`）——状态零丢失、无黑屏闪烁、
  无进程级开销。`rememberSaveable` 不受影响。
- **深浅三态**：FOLLOW_SYSTEM（`isSystemInDarkTheme()` 动态响应系统切换）/ LIGHT / DARK，
  由 `StyleController.darkMode` 决定传给 AppTheme 的 `darkTheme` 值。
- **局部换肤嵌套**：允许页面级 `AppTheme(spec)` 覆盖，**至多一层**（评审约定，不做 lint）——
  嵌套两层以上的局部换肤必然伴随组件复用混乱，是设计问题不是技术问题。
- 跨主题过渡动画（color lerp 全树过渡）：v1 不做，成本高收益低，见开放问题。

### 5.3 Dialog / Popup 作用域

Compose 的 Dialog/Popup 创建独立 composition 但**继承 CompositionLocal**，
AppTokens 自然可达。风险点在 window 级样式（dim 遮罩、输入法适配）不走令牌——
对策：结构组件 `AppDialog` 封装（进 lint FORBIDDEN_COMPOSABLES，业务禁裸用 Dialog），
window 参数由组件内部消化。

### 5.4 解析与构建线程模型

- theme.json 解析 + 合并 + 校验：`Dispatchers.Default`（导入时）；
- **冷启动走构造期同步读**（brand 包是编译期常量，用户包与 AndroidMcpServerConfigStore
  同模式：Koin single 构造时同步读小 JSON，失败降级 brand）——首帧即有正确主题，
  不存在「先白屏再换肤」；
- Spec 构建产物不可变，UI 线程只读。

## 六、Material3 桥接：source of truth 规则（D8）

### 6.1 权威映射表

| Material3 槽位 | 来源（语义令牌） |
| --- | --- |
| `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer` | `colors.primary` 组 |
| `surface` / `surfaceVariant` | `colors.surface` / `surfaceVariant` |
| `surfaceContainer*`（各档） | `colors.surfaceVariant` / `surfaceElevated` 派生 |
| `onSurface` | `colors.textPrimary` |
| `onSurfaceVariant` | `colors.textSecondary` |
| `outline` / `outlineVariant` | `colors.border` / `divider` |
| `error` | `colors.danger` |
| `background` / `onBackground` | `colors.surface` / `colors.textPrimary` |
| `secondary*` / `tertiary*` | brand 包固定派生自 primary（降饱和），**风格包不单独控制**——减少作者负担，需要差异化时是加语义令牌的信号 |
| `typography.*`（8 槽） | type 角色映射（titleLarge→Title 等） |
| `shapes.*`（5 档） | radius 档（现有 AppShapes 规则） |

### 6.2 谁读谁

- designsystem 组件内部：**照常读 MaterialTheme**（Compose 惯用法，组件代码零迁移）——
  因为 M3 槽位已全量由语义令牌填充，读 M3 ≡ 读语义层；
- 业务层：只读 `appTokens()` / `AppTextStyle` 角色枚举（lint 保证不碰 M3）；
- **镜像断言单测**（九.4）锁死「M3 槽位 == 语义令牌」，缝隙即 CI 红。

## 七、可访问性工程（A11y 为硬约束，D10）

### 7.1 对比度（WCAG 2.1 相对亮度模型）

```
L = 0.2126·R' + 0.7152·G' + 0.0722·B'   （sRGB 线性化：c≤0.03928 ? c/12.92 : ((c+0.055)/1.055)^2.4）
ratio = (L_light + 0.05) / (L_dark + 0.05)
```

配对矩阵（loader 强制校验，Kotlin 侧用 `Color.luminance()` 或自实现同公式）：

| 配对 | 下限 | 备注 |
| --- | --- | --- |
| textPrimary ↔ surface | ≥ 4.5 | 正文 |
| textSecondary ↔ surface | ≥ 4.5 | 次要文本同正文标准（不豁免） |
| textTertiary ↔ surface | ≥ 3.0 | 仅限弱信息（时间戳/占位），且组件侧只允许配大字号角色 |
| onPrimary ↔ primary | ≥ 4.5 | 按钮 |
| success/warning/danger/info 作文本 ↔ surface | ≥ 4.5 | 作底色时 onStatus 固定黑/白，不开放覆盖 |
| 表面三色相互、divider ↔ surface | ≥ 1.2 | 软约束告警 |

### 7.2 字体缩放（sp 与系统字号）

- 字号令牌全部用 `sp`——天然跟随用户系统字号缩放；
- **铁律：包文本的容器高度必须 wrap / min-height，禁止固定 height**——designsystem
  组件内部保证；业务层经结构组件亦难触达；lint 不拦（静态难判定），组件评审保证；
- body 类令牌下限 13sp（loader 校验），保证 1.3x 缩放后仍可读。

### 7.3 reduce motion（动效减弱）

- 读系统动画缩放（`ANIMATOR_DURATION_SCALE == 0`），经 `MotionResolver` 提供有效时长；
  系统设置变化时 ContentResolver 监听刷新；
- 所有动效令牌消费前必须过 resolver——包括将来 v2 风格包自定义的动效；
- Compose 侧 `LocalReduceMotion` 提供 `@Composable fun duration(m: AppMotion, level): Duration`。

### 7.4 触控与读屏

- `minTouchTarget=48dp` 不可被风格包覆盖（不在 theme.json 可覆盖组内，天然安全）；
- 结构组件统一 contentDescription 约定（AppScaffold 的导航返回、AppIconButton 必填），
  属组件层契约，不在本设计展开，实施 T8.1 时在组件 KDoc 里强制。

## 八、lint 守卫扩展

| 规则 | 拦截对象 | 提示 |
| --- | --- | --- |
| `DirectColorLiteral`（新） | 业务层 `Color(0x…)` / `Color(red=…)` | 「请使用 appTokens().colors.<语义名>」 |
| `RawTextStyleConstruction`（新） | 业务层 `TextStyle(...)` 直接构造 | 「请使用 AppTextStyle 角色」 |
| `ForbiddenWindowComponent`（新） | 业务层裸用 `Dialog` / `Popup` / `ModalBottomSheet` | 「请使用 designsystem 结构组件（AppDialog 等）」 |
| 现有三条 | Material3 import / 被禁 Composable / 裸 dp·sp | 不变 |

白名单：`com.deepcode.designsystem` 全包（令牌定义与组件实现地）、`:lint` 自身。

## 九、测试策略（把底线变成机器保证）

| # | 测试 | 层级 | 内容 |
| --- | --- | --- | --- |
| 9.1 | 面板完整性 | JVM 单测 | 反射遍历 AppColors/AppTokens 属性，断言 brand 明暗两套全部非默认值（防漏配、防「加了属性忘了给值」） |
| 9.2 | 对比度矩阵 | JVM 单测 | brand 包全配对跑 7.1 公式全绿；任何新令牌 PR 未更新矩阵即红 |
| 9.3 | loader 表驱动 | JVM 单测 | delta 合并（正常/未知键告警/类型冲突拒载/null 拒载/版本高拒载/越界回退）逐例断言 + 导入报告内容 |
| 9.4 | M3 镜像断言 | Compose UI 测试 | `MaterialTheme.colorScheme.primary == appTokens().colors.primary` 等全槽位断言（6.1 表逐行），锁死 D8 |
| 9.5 | 主题切换 | Compose UI 测试（T8.2 内） | 切换 spec 后语义值生效、状态保留（rememberSaveable 存活）、FOLLOW_SYSTEM 响应 |
| 9.6 | 角色渲染快照 | 后期（可选） | 六角色 × 明暗渲染快照；引入 Paparazzi/Roborazzi 时补 |

## 十、反模式与治理

### 10.1 语义膨胀（第一死因）

防线三道：**准入标准**（1.1 表，评审逐条对照）→ **组合优先于新增**——
按钮禁用态 = `textTertiary` + `surfaceVariant`，不新增 `disabledButtonBackground`；
悬停态不是颜色令牌而是组件交互态 API（`AppButton(interactive = …)`）→
**登记即文档**：PR 改 theme 代码必须同步本文档 3.2 表与配对声明，否则不合并。

### 10.2 原始值泄漏

业务层由 lint 拦（八）；**designsystem 内部**靠评审（designsystem 自身不进 lint 白名单外
的规则——令牌定义地必须能写字面量）；内部纪律：字面量只允许出现在 theme 包的
brand 定义文件与 Primitive 层。

### 10.3 废弃周期

`@Deprecated(replaceWith = …)` 标记 → 存续两个发版周期（loader 兼容映射同步维护）→
移除 + schemaVersion 升 1。禁止原地改语义（同名换义）。

## 十一、实施清单（T8）

| 步骤 | 内容 | 验收 |
| --- | --- | --- |
| T8.1 令牌层扩展 | `AppTokens` 聚合 + 3.1–3.3 全部令牌 + 角色枚举扩展 + `dynamicColor` 删除 + **9.1/9.2 单测** | design-guard 全绿；M3 槽位按 6.1 全量映射 |
| T8.2 主题包机制（编译期） | `AppThemeSpec` + brand 包 + `StyleController`（spec/darkMode + 持久化）+ 设置页切换 UI + `AppTheme(spec)` 参数化 + **9.4/9.5 测试** | 切换即时生效零状态丢失；镜像断言绿 |
| T8.3 运行时可插拔 | theme.json v1 解析 + 4.3 合并器 + 4.4 校验器（纯 Kotlin）+ `ThemePackLoader`（assets/filesDir 双根）+ 设置页导入入口 + **9.3 表驱动** | 手写 delta 包切换成功；非法包拒载并出报告 |
| T8.4 lint 扩展 | 八 表三条新规则 | design-guard 四 job 绿 |

依赖与节奏：T8.1 → T8.2 → T8.3 → T8.4 严格串行；T8.1/T8.2 可与 M1 真实模型并行，
T8.3/T8.4 排在 M1 之后（真实模型优先）。

## 十二、开放问题

1. 风格包分发渠道（v1 仅本地导入；市场/远程下发不在范围）；
2. 跨主题切换过渡动画（color lerp 全树）——等真实需求出现再评估成本；
3. 自定义字体文件进风格包（许可审查、包体、字体回退链）——v2 议题；
4. 大字号强制预览（设置页开发者开关 1.3x）——验证 7.2 的手动手段，随 T8.2 顺手做；
5. 图片类令牌（背景/品牌图）——v1 明确不做，内容安全与包体议题；
6. `AppTextStyle` 与 `AppTextTone` 是否合并为单一角色——3.3 已给出分离设计
   （色形正交），倾向维持分离，T8.1 实施时终审。

## 十三、相关文档

- `ARCHITECTURE.md` §组件库是唯一出口 —— 本设计是该铁律的令牌化延伸
- `docs/TOOLS_SKILLS.md` §Skill 层 —— 风格包加载模式（清单+双根+校验）的参照系
- `designsystem/theme/` —— 实施落点；`lint/` —— T8.4 落点
