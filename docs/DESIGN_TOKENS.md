# DESIGN_TOKENS.md — UI 令牌体系与主题包设计（designsystem v2）

> 状态：**设计定稿（2026-09-01），未实施**。实施清单见 七（T8.1–T8.4）。
> 决策记录见 1.3；本文档是 `designsystem` 模块从「单主题组件库」演进为
> 「多风格包运行时」的唯一权威设计。

## 一、定位与铁律

### 1.1 三层令牌模型

延续业界通用分层（Material 3 / W3C Design Tokens 的实践收敛），
**令牌只允许从上往下单向依赖**：

```
Primitive（原始层）    与语义无关的原子值：色阶、字号/字重/字族、间距基数、圆角档、动效时长
                       —— Dimens / TypeScale 已是这一层，补字重字族
     ↓ 取值
Semantic（语义层）     按「用途」命名：color.surface、type.heading、radius.card、motion.page
                       —— 业务代码唯一允许引用的层；明暗两套取值由主题包提供
     ↓ 消费
Component（组件层）    AppCard / AppScaffold 等组件内部，只读语义层，不自造值
                       —— 现状已达成，lint 继续兜底
```

### 1.2 铁律

1. **业务层只写语义名**：页面代码出现 `Color(0x…)` / 裸 `dp` / 自拼 `TextStyle` 即为违规
   （lint 拦截范围见 六）。
2. **令牌先注册后使用**：新增语义令牌必须在 designsystem/theme 里登记（含明暗默认值），
   不允许页面私加。
3. **组件库是唯一出口**：业务层不经 designsystem 组件与令牌触碰任何 Compose 原生样式 API
   （沿用 agent.md 原铁律，本设计不放松）。
4. **主题包是数据，不是代码**：风格差异全部落在令牌取值上；布局结构差异走组件参数，
   不允许风格包携带逻辑。

### 1.3 已确认的三项决策（2026-09-01）

| # | 决策 | 理由 |
| --- | --- | --- |
| D5 | 多主题形态 = **风格包：可切换 + 可插拔 + 高度自定义**；运行时机制，载体与加载模式向 Agent Skills 看齐（清单文件 + 目录扫描 + 校验兜底） | 第三方/后续页面可自带设计；skill 体系已有同型先例（`SkillLoader`），认知与代码模式可复用 |
| D6 | **dynamicColor（Material You 壁纸取色）默认关** | 品牌一致性优先；多风格包下系统取色会与令牌体系冲突。将来若需要，做成某个风格包的可选属性，不走全局开关 |
| D7 | 节奏 = **先定稿后实施**（本文档即定稿），实施按 T8.1–T8.4 分步，每步 CI 全绿再进下一步 | UI 层是全局性改动，避免大爆炸式重构 |

## 二、现状盘点

### 2.1 已就位（不用改）

- `Dimens`：间距 7 档（XXS–XXL）、圆角 5 档、图标 3 档、`minTouchTarget`、`maxContentWidth`；
- `TypeScale`：字号 10 档（含 code 字号与行高）；
- `AppColors`：9 个聊天业务语义色（diff/工具四态/thinking/code 面·边），明暗两套；
- `AppTypography` / `AppShapes` → Material3 桥接；`AppTextStyle` / `AppTextTone` 语义枚举；
- `:lint` 守卫：拦业务层 Material3 import、拦自建被禁组件、**拦硬编码 dp/sp 字面量**（已生效）；
- `AppTheme` 全 App 唯一入口（页面禁止自组 MaterialTheme）。

### 2.2 缺口（本设计补齐）

1. 品牌主色无入口：`lightColorScheme()` 空参默认，品牌色从未配置；
2. 字体令牌只有字号：无字重 / 行高 / 字族；
3. 通用语义色面板缺失：只有聊天业务色，无 surface/text/divider/状态色通用面板；
4. 主题写死单套：`AppTheme` 无主题参数，多设计页面只能改同一个文件；
5. 动效令牌为零；
6. lint 只拦尺寸字面量，不拦颜色字面量。

## 三、令牌清单

> 下表为 v1 面板基线。**加令牌 = 语义层加一个具名属性 + 明暗默认值各一**，
> 清单与代码同步维护；不搞配置文件驱动（编译期由 Kotlin 类型保证完整性）。

### 3.1 颜色（语义面板）

| 组 | 令牌 | 说明 |
| --- | --- | --- |
| 品牌 | `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer` | 主操作色；映射进 Material3 ColorScheme 主色组 |
| 表面 | `surface` / `surfaceVariant` / `surfaceElevated` | 页面底 / 卡片底 / 浮层底（dialog、菜单） |
| 文本 | `textPrimary` / `textSecondary` / `textTertiary` / `textInverse` | 正文 / 次要 / 弱化（时间戳、占位）/ 反色（深底上的文字） |
| 边线 | `divider` / `border` | 分隔线 / 描边 |
| 状态 | `success` / `warning` / `danger` / `info` | 语义状态色；现有 `toolRunning/toolSuccess/toolFailed/toolAwaiting` 改为映射到本组 |
| 业务保留 | `diffAdd` / `diffRemove` / `thinking` / `codeSurface` / `codeBorder` | 聊天场景特有，维持现有名（避免大迁移） |

### 3.2 字体

| 维度 | 令牌 | 基线值 |
| --- | --- | --- |
| 字族 | `fontSans` / `fontMono` | 系统默认 sans；等宽用于代码块与命令输出 |
| 字重 | `wRegular=400` / `wMedium=500` / `wSemibold=600` / `wBold=700` | 标题 600、按钮/标签 500、强调 700 |
| 行高 | 与字号配对：标题 1.2、正文 1.4、代码 1.5 | 现有 `code 12.5sp/18sp≈1.44` 收敛到 1.5 |

角色枚举 `AppTextStyle` 扩展为带字重的语义角色（`Title/SectionHeader/Body/Label/Caption/Code`），
**页面永远选角色，不自由组合字号×字重**——组合爆炸在设计层拦死。

### 3.3 间距与圆角

维持 `Dimens` 现有档位不重命名（存量代码零迁移成本）。语义别名按需追加
（如 `Dimens.cardPadding = spaceL`），别名进 Dimens 同文件、同 lint 保护。

### 3.4 动效

| 令牌 | 基线值 | 用途 |
| --- | --- | --- |
| `motionFast` / `motionNormal` / `motionSlow` | 120ms / 220ms / 320ms | 微交互 / 展开·切换 / 页面转场 |
| `easingStandard` / `easingEmphasized` | Compose 标准曲线 | 列表项、抽屉等 |

进 theme.json 从 v2 开始（v1 风格包不带动效，见 4.2）。

### 3.5 布局结构

**不做栅格系统**（对话流应用不适用）。布局一致性由两件事承载：
结构组件（`AppScaffold` 的 header/actions 槽位约定）+ 尺寸令牌
（`screenPaddingHorizontal`、`maxContentWidth`、`BLOCK_GAP` 已有）。
新增结构模式 = 给 designsystem 加结构组件，不是加令牌。

## 四、主题包（ThemePack）机制

### 4.1 运行时形态

```
AppThemeSpec（运行时数据，非单例）＝ 令牌取值的不可变集合
    ├─ id / name / schemaVersion
    ├─ light: SemanticValues    ├─ dark: SemanticValues
    └─ source: BUILT_IN | USER_IMPORTED

StyleController（:app 装配，designsystem 只定义接口）
    └─ spec: StateFlow<AppThemeSpec>   ← 设置页切换；持久化到 TableModule
```

`AppTheme` 演进（兼容现有调用，默认参数 = brand 包）：

```kotlin
@Composable
fun AppTheme(
    spec: AppThemeSpec = ThemePacks.brand,   // 全局默认走 StyleController
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
)
// 页面级局部换肤（不同设计的页面）：
AppTheme(ThemePacks.console) { ToolMarketScreen() }   // 组件层零改动
```

`dynamicColor` 参数删除（D6），`LocalAppColors` 升级为 `LocalAppTokens`
（颜色/字体/动效一个入口），`appColors()` 改为 `appTokens().colors` 的过渡别名保留。

### 4.2 风格包载体与清单（theme.json，schemaVersion 1）

与 Agent Skills 的 `SKILL.md` 同哲学：**声明式清单 + 目录即包**，delta 覆盖语义——
只声明要改的令牌，缺省继承 brand 默认值（一个极简风格包可以只有 5 行）：

```json
{
  "id": "midnight",
  "name": "午夜",
  "schemaVersion": 1,
  "light": { "color": { "primary": "#7C5CFF", "surface": "#F7F7FA" } },
  "dark":  { "color": { "primary": "#9B85FF", "surface": "#131318" } }
}
```

- v1 可覆盖：`color.*` / `radius.*` / `spacing.*`；字体字族 v1 只允许在
  `sans`/`mono` 内二选一（自定义字体文件涉及许可与包体，v2 再议）；
- 放置位置：内置 `assets/themes/<id>/theme.json` + 用户导入 `filesDir/themes/<id>/`
  （与 `skills/` 的双根模式一致，`ThemePackLoader` 复用同一扫描思路）；
- 风格包**不含代码、不含图片**（v1），纯数据，无执行面。

### 4.3 校验与兜底（designsystem 提供，loader 强制调用）

1. schema 校验：未知令牌名 → 告警忽略（前向兼容）；schemaVersion 高于当前 → 拒载；
2. 值校验：hex 合法性；**文本对比度下限**（正文类 ≥ 4.5:1，大字号 ≥ 3:1），
   不达标 → 该令牌回退 brand 值并记入导入报告；
3. 触控/可读性下限不受风格包影响：`minTouchTarget`、`minBodyFontSize`（13sp）为
   硬约束，风格包越界即拒载对应令牌。

### 4.4 与 Material3 的关系

`AppTheme` 内部仍把语义令牌映射进 `MaterialTheme(colorScheme, typography, shapes)`
——designsystem 组件内部允许读 MaterialTheme（现状不变），映射规则集中在
theme 包一个文件里，风格包作者无感知。

## 五、实施清单（T8）

| 步骤 | 内容 | 验收 |
| --- | --- | --- |
| T8.1 令牌层扩展 | `AppTokens` 聚合（colors/typography/motion）+ 3.1 语义面板补全 + 3.2 字重/行高/字族 + `AppTextStyle` 角色扩展；`dynamicColor` 删除 | design-system lint 全绿；明暗两套默认值齐；存量组件迁移到新面板 |
| T8.2 主题包机制（编译期） | `AppThemeSpec` + `ThemePacks.brand` + `StyleController` + 设置页风格切换 UI；`AppTheme(spec)` 参数化 | 切换即时生效；持久化重启保留；chat/settings 全页面回归 |
| T8.3 运行时可插拔 | `theme.json` v1 解析 + `ThemePackLoader`（assets + filesDir 双根）+ 4.3 校验器 + 设置页导入入口 | 导入一个手写 delta 包成功切换；非法包被拒并给出报告 |
| T8.4 lint 扩展 | 拦业务层 `Color(0x…)` 字面量（designsystem/theme 包白名单）；拦未走 `AppTextStyle` 的裸 `TextStyle` 构造 | design-guard 四 job 绿 |

依赖关系：T8.1 → T8.2 → T8.3 → T8.4 严格串行；每步独立发版不阻塞 M1 主线
（真实模型 Provider 优先级高于 T8.3/T8.4）。

## 六、lint 守卫扩展

| 规则 | 拦截对象 | 提示 |
| --- | --- | --- |
| `DirectColorLiteral`（新） | 业务层 `Color(0x…)` / `Color(red=…)` | 「请使用 appTokens().colors.<语义名>」 |
| `RawTextStyleConstruction`（新） | 业务层 `TextStyle(...)` 直接构造 | 「请使用 AppTextStyle 角色」 |
| 现有三条 | Material3 import / 被禁 Composable / 裸 dp·sp | 不变 |

白名单：`com.deepcode.designsystem.theme` 包（令牌定义地）、`:lint` 自身。

## 七、开放问题

1. 风格包分发渠道：v1 仅本地导入（文件/剪贴板粘贴 JSON）；应用内市场、远程下发不在本设计范围；
2. 动效令牌进清单的时机（倾向 v2，等真实页面动画需求出现再定档位）；
3. 图片类令牌（背景纹理 / 品牌 logo / 插画）是否允许风格包携带——涉及包体与内容安全，v1 明确不做；
4. `AppTextStyle` 角色与 `TextTone` 是否合并为单一角色枚举（倾向合并，T8.1 实施时定稿）。

## 八、相关文档

- `ARCHITECTURE.md` §组件库是唯一出口 —— 本设计是该铁律的令牌化延伸
- `docs/TOOLS_SKILLS.md` §Skill 层 —— 风格包加载模式（清单+双根+校验）的参照系
- `designsystem/theme/` —— 实施落点；`lint/` —— T8.4 落点
