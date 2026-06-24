# LowCall

LowCall 是一个 Android 来电拦截应用，通过可配置的匹配规则（精确、通配符、正则表达式）自动拦截骚扰电话。

## 功能

- **规则拦截**：支持精确匹配、通配符（`*`/`?`）和正则表达式三种规则类型
- **白名单**：手动添加号码白名单，或授权读取通讯录自动放行联系人
- **来电归属地**：拦截时显示号码归属地和运营商信息
- **通话记录**：记录所有被拦截和放行的来电
- **规则导入/导出**：JSON 格式批量管理规则
- **测试匹配**：输入号码验证是否会被拦截
- **通知控制**：可关闭拦截通知，静默拦截

## 技术栈

| 类别 | 方案 |
|------|------|
| UI 框架 | [Jetpack Compose](https://developer.android.com/compose) + [Material 3](https://m3.material.io/) |
| 数据库 | [Room](https://developer.android.com/training/data-storage/room) |
| 导航 | [Navigation Compose](https://developer.android.com/guide/navigation) |
| 偏好存储 | [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) |
| 来电筛选 | [CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService) (API 24+) |
| 编译处理 | [KSP](https://github.com/google/ksp) (Room 注解处理) |
| 语言 | [Kotlin](https://kotlinlang.org/) + Coroutines |

## 开源数据

### 来电归属地

归属地数据来自 [EeeMt/phone-number-geo](https://github.com/EeeMt/phone-number-geo)（MIT License），其数据源为 [xluohome/phonedata](https://github.com/xluohome/phonedata) 提供的 `phone.dat` 数据库。

LowCall 使用自定义 Kotlin 实现对 `phone.dat` 二进制格式的解析，支持按 7 位号段前缀二分查找归属地（省份、城市）和运营商信息。

## 开发

### 环境要求

- Android Studio 最新版
- JDK 11+
- Android SDK 24+

### 构建

```bash
./gradlew :app:assembleDebug
```

### 测试

```bash
./gradlew :app:test
./gradlew :app:lintDebug
```

## License

本项目代码以 MIT License 发布。归属地数据文件 `phone.dat` 的版权属于原始数据提供方。
