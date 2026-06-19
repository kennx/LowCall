# NoCall UI Redesign — Design Spec

> **Date:** 2026-06-19  
> **Source:** OpenDesign project `4a0fdbd6-89b2-41f8-b94b-128a6deb4351`  
> **Approach:** 方案 A — 最小改动 redesign，不修改数据模型

---

## Goal

按照 OpenDesign 设计文件重新设计 NoCall 应用界面，保持现有功能不变，不添加未实现的功能。引入底部导航、首页、分类 Tab、搜索等交互增强。

---

## Architecture

### Navigation

- **Before:** 单层导航，RulesScreen 为启动页，其他页面通过顶部按钮进入
- **After:** 底部导航栏（NavigationBar）+ 5 个顶层目的地，HomeScreen 为启动页
- **Implementation:** `NavHost` + `NavigationBar` (Material3)，使用 `rememberNavController()`

### Data Layer Changes (Minimal)

- `CallLogDao`: add count queries (`getTotalBlockedCount`, `getTodayBlockedCount`, `getRecentBlocked`)
- `BlockRuleDao`: add `searchRules(query: String)`
- `WhitelistDao`: add `searchEntries(query: String)`
- **No schema changes** — no database migration needed

---

## Screen Designs

### 1. 首页 (HomeScreen) — NEW

**Reference:** `home.html`

**Layout:**
```
┌─────────────────────────┐
│  下午好，防护已开启        │  ← Hero (primary bg, rounded bottom)
│  128 通骚扰来电被拦截      │
│  今日已拦截 12 通...      │
├─────────────────────────┤
│ ┌─────┐ ┌─────┐         │
│ │规则 │ │历史 │         │  ← 2×2 Quick Cards
│ │12条 │ │30天 │         │
│ ├─────┤ ├─────┤         │
│ │测试 │ │添加 │         │
│ │验证 │ │规则 │         │
│ └─────┘ └─────┘         │
├─────────────────────────┤
│ 最近拦截                  │
│ ┌─────────────────────┐ │
│ │ 95013  营销骚扰 10分钟│ │  ← Recent 3 blocked calls
│ └─────────────────────┘ │
└─────────────────────────┘
```

**Components:**
- Hero section: `primary` background, `extra-large` bottom corners, greeting + stat number + subtitle
- Quick grid: 2×2 cards with icon + label + hint, `filled` tonal cards
- Recent list: last 3 blocked `CallLog` entries, list items with `block` icon
- FAB: add rule

**Data:**
- Total blocked count: `CallLogDao.getTotalBlockedCount()`
- Today blocked count: `CallLogDao.getTodayBlockedCount()`
- Recent blocked: `CallLogDao.getRecentBlocked(limit = 3)`

---

### 2. 拦截规则 (RulesScreen) — REDESIGN

**Reference:** `rules.html`

**Layout:**
```
┌─────────────────────────┐
│ ← 拦截规则      🔍 ⋮    │  ← AppBar with search + more
├─────────────────────────┤
│ 全部 │ 精确 │ 通配 │ 正则 │  ← ScrollableTabRow
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ 95013              ✓ │ │  ← Elevated rule card
│ │ 营销号段    [精确]   │ │
│ └─────────────────────┘ │
│ ...                     │
└─────────────────────────┘
```

**Changes:**
- Add `ScrollableTabRow` with tabs: 全部 / 精确 / 通配 / 正则 (using existing `RuleType`)
- Rule cards: `ElevatedCard` with pattern name, description, type chip, enable switch
- Search: AppBar search button toggles search field, filters rules by pattern/description
- Keep existing: edit on click, toggle, FAB add rule

---

### 3. 拦截历史 (CallHistoryScreen) — REDESIGN

**Reference:** `history.html`

**Layout:**
```
┌─────────────────────────┐
│ ← 拦截历史      ⚙ ⋮    │
├─────────────────────────┤
│ 今天                     │
│ ┌─────────────────────┐ │
│ │ 🚫 95013   已拦截 10:11│ │  ← Date-grouped list
│ │    营销骚扰 · 响铃0秒   │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ ✅ 138****   已放行 09:30│ │
│ └─────────────────────┘ │
│ 昨天                     │
│ ...                     │
└─────────────────────────┘
```

**Changes:**
- Group by date: "今天" / "昨天" / "M月d日"
- Each item: circular avatar (blocked=errorContainer, allowed=primaryContainer), number, meta, status tag, time
- Keep existing: "加入白名单" button for blocked entries

---

### 4. 白名单 (WhitelistScreen) — REDESIGN + SEARCH

**Reference:** `whitelist.html`

**Layout:**
```
┌─────────────────────────┐
│ ← 白名单      👤 ⋮      │
├─────────────────────────┤
│ 🔍 搜索联系人或号码        │  ← Rounded search bar
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ 爸 爸爸 138**** 始终放行│ │  ← Contact item
│ └─────────────────────┘ │
│ ...                     │
└─────────────────────────┘
```

**Changes:**
- Add rounded search bar at top
- Search filters existing whitelist entries by name/number
- If `READ_CONTACTS` granted, also search contacts and show "add to whitelist" option
- Each item: circular avatar (first char), name, number, "始终放行" badge
- Keep existing: add/delete, contacts permission request card

---

### 5. 测试匹配 (RuleTestScreen) — REDESIGN

**Reference:** `test.html` (simplified)

**Layout:**
```
┌─────────────────────────┐
│ ← 测试匹配               │
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ 输入测试号码           │ │
│ │ 手机号、座机或号段      │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │        测试          │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │  ← Result card (animated)
│ │ 🚫 匹配规则: 950*    │ │
│ │ 拦截 950 开头的服务号段 │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

**Changes:**
- Result card styling: blocked = `errorContainer`, allowed = `surfaceContainerHigh`
- Add slide-up animation for result appearance
- Keep existing: phone input, test button, result logic

---

### 6. 设置 (SettingsScreen) — REDESIGN

**Reference:** `settings.html` (existing features only)

**Layout:**
```
┌─────────────────────────┐
│ ← 设置        🔍        │
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │  🛡 来电拦截          │ │  ← About card (static)
│ │     版本 2.4.0       │ │
│ └─────────────────────┘ │
│ 防护开关                  │
│ ┌─────────────────────┐ │
│ │ 🔔 拦截通知      [ON] │ │  ← Setting item with icon
│ │    每次拦截后显示通知   │ │
│ └─────────────────────┘ │
│ 规则与数据                 │
│ ┌─────────────────────┐ │
│ │ 📥 导出规则     →     │ │
│ ├─────────────────────┤ │
│ │ 📤 导入规则     →     │ │
│ ├─────────────────────┤ │
│ │ 🗑 清空历史记录  →     │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

**Changes:**
- Add about card at top: app icon + name + version
- Group settings with section titles: "防护开关", "规则与数据"
- Each setting item: icon in circular container + name + description + trailing (switch or arrow)
- Keep existing: notification toggle, import/export, clear history with confirmation dialog

---

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `MainActivity.kt` | Modify | Update NavHost, add BottomNavigation |
| `NavRoutes.kt` | Modify | Add `HOME` route |
| `ui/home/HomeScreen.kt` | Create | New home screen |
| `ui/home/HomeViewModel.kt` | Create | Home screen data |
| `ui/rules/RulesScreen.kt` | Modify | Redesign with tabs |
| `ui/history/CallHistoryScreen.kt` | Modify | Redesign with date grouping |
| `ui/whitelist/WhitelistScreen.kt` | Modify | Redesign with search |
| `ui/test/RuleTestScreen.kt` | Modify | Redesign result card |
| `ui/settings/SettingsScreen.kt` | Modify | Redesign with groups |
| `data/db/CallLogDao.kt` | Modify | Add count queries |
| `data/db/BlockRuleDao.kt` | Modify | Add search query |
| `data/db/WhitelistDao.kt` | Modify | Add search query |
| `res/values/strings.xml` | Modify | Add new strings |
| `ui/rules/RulesViewModel.kt` | Modify | Add tab filtering |
| `ui/whitelist/WhitelistViewModel.kt` | Modify | Add search logic |

---

## Theme

- Keep existing Material3 theme system
- Dynamic color (Android 12+) remains unchanged
- Fallback colors: `Purple40 #6650a4` (matches design reference in `test.html`)

---

## Constraints

- **minSdk 24:** All APIs must be compatible with API 24+
- **No DI framework:** Continue using `viewModel()` and manual construction
- **No schema changes:** Database migration not needed
- **Strings:** All user-visible text in `strings.xml`
- **Edge-to-edge:** Continue using `enableEdgeToEdge()`, handle insets properly

---

## Testing Checklist

- [ ] Home screen displays stats correctly
- [ ] Bottom navigation switches between all 5 tabs
- [ ] Rules tab filtering works (全部/精确/通配/正则)
- [ ] Rules search filters correctly
- [ ] History groups by date
- [ ] Whitelist search filters entries
- [ ] Whitelist contacts search works (with permission)
- [ ] Test screen result animation works
- [ ] Settings about card displays version
- [ ] Import/export still works
- [ ] Clear history still works with confirmation
- [ ] Notification toggle still works
- [ ] All existing functionality preserved
