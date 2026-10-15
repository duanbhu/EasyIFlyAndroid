# EasyIFlyAndroid

独立 Android 语音识别库，默认逻辑对齐 Swift EasyIFly。可单独打开、构建和维护，不需要 xbd_library 工程或上级目录中的文件。讯飞 SDK、两个 ARM ABI 的原生库及 UI 主题均包含在本工程内。

- 包名：`com.xbdlib.speech`
- Android：minSdk 23、compileSdk 34
- ABI：`arm64-v8a`、`armeabi-v7a`
- Kotlin API：1.8；独立 AAR 已合并讯飞类，无需额外添加 `Msc.jar`
- 示例 App ID：`YOUR_APP_ID`；库本身由宿主传入 App ID

## 工程结构

```text
library/       语音识别、号码过滤、弹窗和蓝色／红色／自定义主题
  libs/        讯飞 Msc.jar
  src/main/jniLibs/  arm64-v8a、armeabi-v7a 的 libmsc.so
sample/        直接引用构建后 AAR 的示例应用
compat/        Kotlin 1.8 消费者编译验证
screenshots/   真机主题截图
tools/         独立构建与验证脚本
gradle/        Gradle Wrapper
dist/          构建生成的交付物、本地 Maven 仓库和示例 APK
```

## 构建

准备 JDK 17、Android SDK Platform 34 和 Python 3。通过 `ANDROID_HOME` 指定 Android SDK，或在根目录的 `local.properties` 中设置 `sdk.dir`。

```bash
# 在 EasyIFlyAndroid 根目录执行；SDK 路径按实际安装位置设置。
export ANDROID_HOME="$HOME/Library/Android/sdk"
./tools/build-speech.sh

# 依赖已缓存时：
./tools/build-speech.sh --offline
```

脚本会优先使用 `SPEECH_JAVA_HOME` 或现有的 JDK 17，再尝试发现本机 JDK 17。可显式设置 `SPEECH_JAVA_HOME=/path/to/jdk17`。构建在 Gradle 进程内编译 Kotlin，避免复用其他 JDK 的编译守护进程。

脚本执行 Release AAR 构建、现有单元测试、Lint、Kotlin 1.8 消费者编译、本地 Maven 发布和示例 Debug／R8 Release 构建，并生成：

```text
dist/xbd-speech-recognition-1.0.0.aar
dist/artifact-info.json
dist/SHA256SUMS
dist/maven/com/xbdlib/speech-recognition/1.0.0/
dist/sample/sample-debug.apk
dist/sample/sample-release.apk
```

本地 Maven 发布仅写入本工程的 `dist/maven`。单独构建也可运行：

```bash
./gradlew :library:bundleAar
./gradlew :library:publishReleasePublicationToBundleRepository
./gradlew -PwithSample :sample:assembleDebug
```

直接调用 Gradle 时请使用 JDK 17。Windows 可使用 `gradlew.bat`；完整交付脚本需在 Bash 和 Python 3 环境运行。

`library/libs/Msc.jar`、两个 `libmsc.so` 及 `gradle/wrapper/gradle-wrapper.jar` 是必需的构建输入，应随源码维护。`local.properties`、构建缓存和 `dist/` 不纳入版本管理。

## 宿主接入

将 `dist/xbd-speech-recognition-1.0.0.aar` 复制到宿主的 `app/libs/`：

```groovy
dependencies {
    implementation files('libs/xbd-speech-recognition-1.0.0.aar')
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.8.0'
}
```

或使用生成的本地 Maven 仓库：

```groovy
// 宿主 settings.gradle 的 dependencyResolutionManagement.repositories 中：
maven { url = uri('/path/to/EasyIFlyAndroid/dist/maven') }

// 宿主 app/build.gradle：
implementation 'com.xbdlib:speech-recognition:1.0.0'
```

两种产物均包含讯飞类和 ARM 原生库。宿主负责麦克风运行时权限和生命周期释放。

```kotlin
val panel = SpeechRecognitionPanel(this).apply {
    setMode(SpeechMode.PHONE_OR_VIRTUAL)
    setTheme(SpeechRecognitionTheme.RED)
    setListener(object : SpeechRecognitionListener {
        override fun onResult(result: SpeechRecognitionResult) {
            val value = result.phone?.value ?: result.text
            // 首次有效结果自动回传，弹窗随后关闭。
        }
    })
    setAppId("YOUR_APP_ID")
}
setContentView(panel)

// 使用前申请 Manifest.permission.RECORD_AUDIO。
// 在宿主 onDestroy() 或 Compose AndroidView.onRelease 中调用 panel.release()。
```

`bind(owner)` 是兼容入口，只重建控制器，不会自动注册生命周期。设置 App ID 已会创建控制器。

## App ID 与 SDK 绑定

当前示例使用用户确认的 Android App ID `YOUR_APP_ID`，与原 xbd_library 示例及 SDK 注释 `Android_iat1143_YOUR_APP_ID (V1143)` 对应。此前使用 `YOUR_APP_ID` 时真机返回 `10407`（用户校验失败）；切换为 `YOUR_APP_ID` 后已进入聆听，未再出现该错误。用户反馈号码识别正确并自动关闭，完整自动化结果取证及准确率、耗时、断网验收仍待完成。

App ID 需与下载的 SDK 保持一致。继续使用 `YOUR_APP_ID` 时，请将对应 Android SDK 中的 `Msc.jar` 和两种 ARM ABI 的 `libmsc.so` 分别替换到 `library/libs/` 与 `library/src/main/jniLibs/`，重新运行构建脚本。错误证据和原配置位置见 `VERIFICATION.md`。

## 识别规则

| 模式 | Swift 对应入口 | 接受条件 |
| --- | --- | --- |
| `PHONE_ONLY` | `showPhoneInput` | 过滤后恰好 11 个数字字符 |
| `PHONE_OR_VIRTUAL` | `showVirtualNumberInput` | 11 个数字字符，或 14／15 个数字字符 |
| `FREE_TEXT` | `showSpeechInput(resultFilter: { $0 })` | 当前识别文本片段 |

号码模式移除非数字字符，按总长度判断，不校验手机号号段。虚拟号以 `-` 分隔，如 `13800138000-1234`。与 Swift `Character.isNumber` 一致，中文数字和全角数字保留原样，不做中文数字到阿拉伯数字的转换。

每个 SDK 片段单独过滤，不累积分段，也不等待 `isLast`。首次匹配立即停止并提交；不匹配或 JSON 解析失败则重新识别。虚拟号若先返回 11 位主号，会先提交主号，不等待分机。`SpeechRecognitionResult.text` 为过滤后的文本；`isFinal` 保留 SDK 标志，不能作为业务是否提交的判断条件。

SDK 错误会回调并停止，不自动重试错误。重启、停止和释放后会屏蔽旧回调及排队重试。

## 主题

默认 `SpeechRecognitionTheme.BLUE` 保留原始蓝色配色。`RED` 的主色为 `#FF3B30`，标题采用米黄色 `#FFF5E6` 背景、黑色文字，麦克风、声波和按钮使用红色调。示例顶部提供蓝／红切换。

```kotlin
panel.setTheme(SpeechRecognitionTheme.BLUE)
panel.setTheme(SpeechRecognitionTheme.RED)
panel.setTheme(SpeechRecognitionTheme(
    primaryColor = Color.parseColor("#008577"),
    titleBackgroundColor = Color.parseColor("#FFF5E6"),
    titleTextColor = Color.BLACK,
    waveformColor = Color.parseColor("#80CBC4"),
    buttonBackgroundColor = Color.parseColor("#E0F2F1"),
))
```

主题支持独立配置 `primaryColor`、`titleBackgroundColor`、`titleTextColor`、`waveformColor`、`microphoneColor`、`buttonBackgroundColor`、`buttonTextColor` 和 `backgroundColor`。`copy()` 只修改显式传入的字段；若希望各部位使用新主色默认值，请创建新的 `SpeechRecognitionTheme(primaryColor = ...)`。

面板和已展示的弹窗都可调用 `setTheme()`，切换颜色不重启识别。

## 仅使用控制器或自定义过滤

```kotlin
val controller = SpeechRecognitionController(
    context = this,
    config = SpeechRecognitionConfig(
        appId = "YOUR_APP_ID",
        mode = SpeechMode.FREE_TEXT,
        resultFilter = { text -> text.removePrefix("地址：").takeIf { it.isNotBlank() } },
    ),
    listener = object : SpeechRecognitionListener {
        override fun onResult(result: SpeechRecognitionResult) { /* 使用 result.text */ }
        override fun onError(error: SpeechRecognitionError) { /* 处理识别错误 */ }
    },
)
controller.start()
// controller.stop()
// 宿主销毁时 controller.release()
```

自定义 `resultFilter` 返回 `null` 会继续识别，返回字符串则提交该字符串。仅使用控制器时，显式设置 `stopOnResult = false` 可启用 Android 扩展的连续模式。

`SpeechRecognitionDialog(context, controller, mode, theme = SpeechRecognitionTheme.RED)` 可直接使用弹窗；省略 listener 时复用并在关闭后恢复控制器监听器。默认自动开始录音，接受结果后自动关闭；“结束录入”、外部遮罩和系统返回仅停止关闭，不提交结果。声波是固定循环动画。

`autoStart = false` 时，展示弹窗后调用 `dialog.startListening()`；面板对应 `setAutoStart(false)` 和 `panel.startListening()`。

控制器创建、生命周期方法和 `updateListener()` 必须在主线程调用；SDK 结果与状态回调回到主线程。

默认参数与 Swift 对齐：中文普通话、16 kHz、无标点、网络超时 20 秒，录音及前后静音超时参数为 86400000 毫秒。Android 讯飞 SDK 的有效参数范围及云端识别效果需真机验收。

验证记录见 [VERIFICATION.md](VERIFICATION.md)。
