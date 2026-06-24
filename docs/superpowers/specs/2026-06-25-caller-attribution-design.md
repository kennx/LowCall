# NoCall 来电归属地与运营商显示 — 设计规格

<!-- Last updated: 2026-06-25 -->

## 1. 目标

在来电拦截流程中，为每个来电查询归属地（省份+城市）和运营商，并展示在：

- **系统来电面板**（已放行来电）：号码下方副标题显示归属地信息
- **拦截通知**：通知内容增加归属地信息
- **首页「最近拦截」卡片**：号码下方显示归属地+运营商
- **通话记录列表**：每条记录增加归属地+运营商信息

## 2. 数据源

使用 `phone.dat` 二进制数据库（与 `xluohome/phonedata` 相同格式），纯离线查询。

- **数据来源**: [EeeMt/phone-number-geo](https://github.com/EeeMt/phone-number-geo)（数据版本 `202502`，同步自 `pangongzi/phone`）
- **文件**: `phone.dat`（约 4.7 MB），捆绑于 `app/src/main/assets/phone.dat`
- **格式**: 自定义二进制格式（与 `xluohome/phonedata` 兼容），~50 万条中国手机号段前缀记录
- **覆盖**: 中国移动/联通/电信/广电及虚拟运营商
- **查询性能**: 纳秒级二进制查找（二分搜索）
- **数据新鲜度**: 截至 2025 年 2 月

### 二进制格式规范

```
Offset  Size    Description
─────────────────────────────────────────────
0       4       Version number (e.g. 1701 = 2017年1月), int32 LE
4       4       First index offset (start of index area), int32 LE
8       …       Record area: concatenated \0-terminated strings
                Each record: "省份|城市|邮编|区号\0"
index   …       Index area: array of 9-byte entries
                Each entry: [prefix:4 LE][record_offset:4 LE][isp_code:1]
```

### ISP Code 映射

| Code | 运营商 |
|------|--------|
| 1 | 中国移动 |
| 2 | 中国联通 |
| 3 | 中国电信 |
| 4 | 中国电信虚拟运营商 |
| 5 | 中国联通虚拟运营商 |
| 6 | 中国移动虚拟运营商 |
| 7 | 中国广电 |
| 8 | 中国广电虚拟运营商 |

### 二分查找算法

1. 读取 header: version (bytes 0-3), index_offset (bytes 4-7)
2. 取查询号码前 7 位转为 int
3. 在 index 区域执行二分查找，比较 4-byte 前缀
4. 命中后读取 record_offset（绝对偏移量），在该偏移处读取 \0 结尾的记录字符串
5. 按 `|` 分割得到 province, city, zipCode, areaCode

## 3. 架构

```
phone.dat (assets, ~3.9 MB)
  │
  │  AppContainer 创建时加载到 ByteArray（一次性）
  ▼
PhoneAttribution.kt  (新增 — 顶层函数)
  │  lookupPhoneAttribution(phoneNumber: String): AttributionResult?
  │
  ├─► BlockingCallScreeningService.onScreenCall()
  │     │
  │     ├─ 已放行 (ALLOWED):
  │     │   CallResponse.Builder()
  │     │     .setCallerId("广东深圳 · 中国移动")  // API 29+
  │     │     .build()
  │     │   → 系统来电面板号码下方显示
  │     │
  │     └─ 已拦截 (BLOCKED):
  │         归属地信息写入 CallLog + 系统通知
  │         （来电面板不显示，被 setDisallowCall 抑制）
  │
  └─► CallLog (新增字段)
        location: String?  —   "广东深圳"  （省份+城市拼接）
        carrier: String?   —   "中国移动"
```

### setCallerId 注意事项

- `CallResponse.Builder.setCallerId()` 要求 API 29+
- `< API 29` 时忽略该调用（不会崩溃）
- 该文字会显示在系统来电界面号码下方（原先是空白的副标题位置）
- 无需额外权限，无需用户设置

## 4. 改动清单

### 4.1 新增文件

| 文件 | 职责 |
|------|------|
| `data/PhoneAttribution.kt` | 顶层函数 `lookupPhoneAttribution(phone)` 和 `loadPhoneDat(context)` |

### 4.2 修改文件

| 文件 | 改动内容 |
|------|---------|
| `data/model/CallLog.kt` | 新增 `location: String?` 和 `carrier: String?` 字段 |
| `data/db/AppDatabase.kt` | 新增 MIGRATION_2_3, version → 3 |
| `AppContainer.kt` | 新增 `phoneAttribution: PhoneAttribution` 属性 |
| `service/BlockingCallScreeningService.kt` | 所有分支增加归属地查询 + ALLOWED 分支增加 `setCallerId()` |
| `service/NotificationHelper.kt` | 通知文本增加归属地信息参数 |
| `ui/home/HomeScreen.kt` | RecentBlockedItem 增加归属地副标题行 |
| `ui/history/CallHistoryScreen.kt` | CallLogItem 增加归属地信息行 |
| `res/values/strings.xml` | 新增归属地格式字符串 |

### 4.3 新增 Assets

| 文件 | 来源 |
|------|------|
| `app/src/main/assets/phone.dat` | https://github.com/EeeMt/phone-number-geo/raw/master/src/main/resources/phone.dat |

## 5. 数据模型变更

### CallLog 实体 (v3)

```kotlin
@Entity(
    tableName = "call_logs",
    indices = [Index(value = ["timestamp"])]
)
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "matched_rule_id") val matchedRuleId: Long? = null,
    @ColumnInfo(name = "matched_rule_pattern") val matchedRulePattern: String? = null,
    @ColumnInfo(name = "action") val action: CallAction,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    // 新增字段
    @ColumnInfo(name = "location") val location: String? = null,   // "广东深圳"
    @ColumnInfo(name = "carrier") val carrier: String? = null      // "中国移动"
)
```

新增字段使用 `DEFAULT NULL` 以保证与 v2 数据的向后兼容。Room 的 `ALTER TABLE ADD COLUMN` 在 SQLite 默认就是 NULL。

### 数据库迁移

```sql
-- MIGRATION_2_3
ALTER TABLE `call_logs` ADD COLUMN `location` TEXT;
ALTER TABLE `call_logs` ADD COLUMN `carrier` TEXT;
```

## 6. 核心实现

### 6.1 PhoneAttribution

```kotlin
// 单文件，约 100 行
// 不使用第三方依赖，直接用 ByteBuffer 解析 phone.dat

data class AttributionResult(
    val province: String,  // "广东"
    val city: String,      // "深圳"
    val carrier: String    // "中国移动"
)

class PhoneAttribution(data: ByteArray) {
    private val buffer = ByteBuffer.wrap(data)
        .asReadOnlyBuffer()
        .order(ByteOrder.LITTLE_ENDIAN)

    private val indexOffset: Int

    init {
        // 跳过 version (4 bytes)
        buffer.position(4)
        indexOffset = buffer.int
    }

    fun lookup(phoneNumber: String): AttributionResult? {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.length < 7) return null
        val prefix = normalized.substring(0, 7).toIntOrNull() ?: return null

        // 二分查找
        var left = indexOffset
        var right = buffer.capacity()
        while (left < right) {
            val mid = alignToIndex(left + (right - left) / 2 / 9 * 9)
            if (mid >= right) return null

            buffer.position(mid)
            val midPrefix = buffer.int
            when {
                midPrefix == prefix -> {
                    val recordOffset = buffer.int
                    val ispCode = buffer.get().toInt() and 0xFF
                    val record = readRecord(recordOffset)
                    val parts = record.split("|")
                    if (parts.size < 4) return null
                    return AttributionResult(
                        province = parts[0],
                        city = parts[1],
                        carrier = ispToCarrier(ispCode)
                    )
                }
                midPrefix > prefix -> right = mid
                else -> left = mid + 9
            }
        }
        return null
    }

    // … 辅助方法: alignToIndex, readRecord, ispToCarrier
}
```

### 6.2 AppContainer 集成

```kotlin
// AppContainer.kt
val phoneAttribution: PhoneAttribution by lazy {
    val data = context.assets.open("phone.dat").use { it.readBytes() }
    PhoneAttribution(data)
}
```

### 6.3 CallScreeningService 改动

```kotlin
// BlockingCallScreeningService.kt — 在 runBlocking 内增加

val attribution = container.phoneAttribution.lookup(phoneNumber)

// ALLOWED 分支:
val response = CallResponse.Builder().apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && attribution != null) {
        setCallerId("${attribution.province}${attribution.city} · ${attribution.carrier}")
    }
}.build()

// CallLog 写入增加字段:
CallLog(
    phoneNumber = phoneNumber,
    action = CallAction.ALLOWED,
    location = attribution?.let { "${it.province}${it.city}" },
    carrier = attribution?.carrier,
    timestamp = System.currentTimeMillis()
)
```

### 6.4 Notification 改动

`NotificationHelper.showBlockedCallNotification()` 新增参数 `location: String?`, `carrier: String?`：

```
标题: 拦截来电
内容: 号码: 13800138000 · 广东深圳 · 中国移动
展开: 号码: 13800138000 · 广东深圳 · 中国移动
      规则: 骚扰电话
```

## 7. UI 改动

### 7.1 首页「最近拦截」卡片

```
┌────────────────────────────────────┐
│ 🚫  13800138000                  已拦截│
│    广东深圳 · 中国移动            14:00│
│    规则: 骚扰电话 · 响铃 0 秒          │
│    [加入白名单]                        │
└────────────────────────────────────┘
```

改动：在 `RecentBlockedItem` 的号码行和规则描述行之间插入归属地行。

### 7.2 通话记录列表

```
┌────────────────────────────────────┐
│ 📞  13800138000           已放行  14:00│
│    广东深圳 · 中国移动                 │
│    已放行 · 白名单匹配                  │
└────────────────────────────────────┘
```

改动：`CallLogItem` 在号码行下方显示归属地信息（当 `location` 和 `carrier` 均非 null 时）。

### 7.3 空/null 处理

当归属地查询失败（某些号段 phone.dat 可能不覆盖）时：
- `location` 和 `carrier` 为 null
- UI 不显示归属地行（保持现状）
- 通知不显示归属地信息

## 8. 字符串资源

```xml
<!-- 归属地格式: "广东深圳 · 中国移动" -->
<string name="attribution_format">%1$s · %2$s</string>
```

## 9. 验证计划

| 验证项 | 方法 |
|--------|------|
| phone.dat 解析正确性 | 单元测试：已知号段 → 预期归属地 |
| 二分查找边界 | 单元测试：边界前缀、不存在的号段 |
| 数据库迁移 | 从 v2 数据库升级到 v3，验证现有数据不丢失 |
| CallerId 显示 | 真机测试：放行来电 → 系统面板显示归属地 |
| 通知显示 | 真机测试：拦截来电 → 通知含归属地 |
| UI 显示 | 单元测试 + 真机截图对比 |

## 10. 不包含的内容

- 在线 API 兜底（v1 仅离线）
- phone.dat 自动更新机制
- 国际号码归属地（phone.dat 仅覆盖中国号码）
- 自定义来电界面覆盖（InCallService）
- 携号转网检测
