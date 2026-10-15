# 独立语音识别库验证记录

日期：2026-10-15。

本次在 `/Users/duanhu/Documents/PrivateLibrary/EasyIFlyAndroid` 中直接构建验证。工程的源码、讯飞 SDK、Gradle Wrapper、测试、示例与主题均位于本目录，构建不读取原 xbd_library 工程。

## 构建与接入

| 检查 | 结果 |
| --- | --- |
| 独立工程 Release AAR | 通过 |
| 号码过滤单元测试 | 8 项通过 |
| 识别会话单元测试 | 13 项通过 |
| Kotlin 1.8.0 直接消费 AAR | 通过 |
| Lint | 0 errors，6 warnings |
| 示例直接引用 AAR 的 Debug 构建 | 通过 |
| 示例 R8 Release 构建 | 通过 |
| 工程内本地 Maven 发布 | 通过 |
| Maven AAR 与 dist AAR 字节一致 | 通过 |
| Maven POM 的 kotlin-stdlib 依赖、源码 JAR | 通过 |
| 讯飞 SDK、两种 ARM ABI、主题类 | 均包含在 AAR 中 |

验证命令：`ANDROID_HOME=/path/to/android-sdk ./tools/build-speech.sh --offline`。本地 Maven 仓库位于 `dist/maven`，发布没有访问外部 Maven 服务。

AAR：`dist/xbd-speech-recognition-1.0.0.aar`。大小：3,083,882 字节。包含 364 个类。

SHA-256：`61cb0cc7308be72070798824a52b9baed2721a6e6aa8361c6d9c38abea4e6c9c`。

Lint 警告涉及固定 Kotlin 1.8 版本、绘制时分配对象及硬编码中文提示，没有阻断项。

## 功能与真机范围

- 保留 Swift 对齐的位数过滤、首次匹配立即提交、失败片段重试和取消后旧回调失效逻辑。
- 默认蓝色主题保留原有色值；红色主题主色为 `#FF3B30`，标题为 `#FFF5E6` 米黄色背景、黑色文字，其余元素使用红色调。
- 已在 vivo V2278A 检查红色弹窗的标题、声波、麦克风和结束按钮配色，截图位于 `screenshots/red-theme.png`。`screenshots/blue-theme.png` 记录蓝色面板。
- 真机验证曾使用与 SDK 匹配的 Android App ID。当前提交已移除真实 App ID，示例通过构建参数或环境变量读取，默认启动仅显示配置提示。
- 用户已允许恢复真机验证。2026-10-15 20:10，示例进入 LISTENING 后返回 ERROR，错误码 `10407`，提示“用户校验失败”；未取得有效识别结果。准确率、耗时、断网及识别期间取消验收尚未完成。SDK 超时参数的实际有效范围也尚待验收。

主题预览关闭自动录音；恢复后的错误复现启用自动录音，没有据此声称完成云端语音识别验收。

## 10407 排查记录

- 历史复现使用与 SDK 不匹配的 App ID，错误日志为 `error code=10407 message=用户校验失败`。
- 原 xbd_library 示例初始化配置与所带 SDK 的标记对应。
- 原模块标记所带 SDK 版本为 V1143。
- 新库的 Msc.jar、arm64-v8a/libmsc.so、armeabi-v7a/libmsc.so 与原模块文件逐一 SHA-256 一致。以上支持优先排查 App ID／SDK 绑定不匹配，但尚未通过更换匹配 SDK 验证根因。
- [讯飞官方文档](https://static.xfyun.cn/doc/asr/offline_iat/Android-SDK.html) 说明，App ID 必须与下载的 SDK 保持一致，否则可能出现 10407。
- 示例新增 SpeechSample 日志，记录状态、错误码及结果事件，不记录识别文本。新增日志后的 Debug 与 R8 Release 构建通过并覆盖安装 Debug。
- 用户确认使用匹配的 Android App ID 后已重建安装，未再复现 10407。真实值不纳入本次提交，库本身由宿主传入 App ID。

## 匹配 App ID 的真机复验

- 2026-10-15 20:17，vivo V2278A 运行新示例，日志进入 `LISTENING`，持续测试期间未再出现 `10407` 或其他 ERROR。红色弹窗显示正常，截图见 `screenshots/appid-listening.png`。
- 用户对测试号码 `13800138000` 反馈“已自动关闭，号码正确”。该项记为用户手工反馈。
- ADB 随后取得的日志仍为 LISTENING／PROCESSING／重试，抓图仍为聆听弹窗，未捕获对应 STOPPED／result 事件。因此自动化证据尚未交叉确认该次提交，不据此宣称完整回归通过。
- 随后点击“结束录入”，取消后的持续观察被新任务中断，尚未完成。静音时观察到约 10.5 秒进入处理并重试，不能将传入的 86400000 毫秒解释为 Android 上已验证的实际静音等待时长。
- 待完成：手机号／虚拟号／文字各模式的稳定结果回传、取消后无旧回调、断网和恢复、准确率与耗时。

## 1.0.0 版本定稿

- 库 Maven 版本和示例 versionName 均为 `1.0.0`，Git 标签为 `v1.0.0`。
- 示例默认不内置 App ID，通过 Gradle 参数 `iflytekAppId` 或环境变量 `IFLYTEK_APP_ID` 注入；未配置时仅显示提示，不初始化 SDK。
- 移除默认 App ID 后，Debug 与 R8 Release 构建通过；检查两种变体的生成配置，App ID 均为空字符串，APK 元数据 versionName 均为 `1.0.0`。
- 本次未覆盖真机安装无 App ID 的示例，保留设备上先前的测试应用。
