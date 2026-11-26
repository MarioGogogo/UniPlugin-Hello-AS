# UniPlugin-Hello-AS

uni-app 原生插件 Android 离线打包工程，包含多个原生插件模块（Module / Component）与一个自定义基座示例应用。

## 项目结构

```
UniPlugin-Hello-AS/
├── app/                           # 主应用模块（自定义基座）
│   ├── libs/                      # uniapp-v8-release.aar 核心库
│   └── src/main/assets/           # dcloud_uniplugins.json 插件注册配置
├── uniplugin_module/              # 模块插件示例（TestModule）
├── uniplugin_component/           # 组件插件示例（TestText / RichAlert）
├── uniplugin_richalert/           # 富文本 Alert 插件
├── uniplugin_webview/             # WebView 插件（核心模块，含完整文档）
├── uniplugin_betech_nfc/          # 必达门锁 NFC 插件（读卡/写卡/注销）
├── uniplugin_keystore/            # 硬件级安全加密插件（AES-GCM 256 位）
├── uniplugin_printer/             # 外部打印机插件（图片/PDF/网页打印）
├── uts-toast/                     # UTS 原生插件（Kotlin）
└── uniapp示例工程源码/             # uni-app 前端示例工程
```

## 插件模块一览

| 模块 | 类型 | 说明 |
|------|------|------|
| `uniplugin_webview` | Component | 原生 WebView 组件：池化预热、SAF 文件选择（无权限方案）、H5 下载（blob 拦截 / DownloadManager）、H5 拍照录像、JS Bridge 双向通信。详见 [模块文档](uniplugin_webview/README.md) |
| `uniplugin_betech_nfc` | Module | 必达门锁 NFC 读卡/写卡/注销，详见 [README](uniplugin_betech_nfc/README.md) |
| `uniplugin_keystore` | Module | 硬件级安全加密（AndroidKeyStore AES-GCM 256），详见 [README](uniplugin_keystore/README.md) |
| `uniplugin_printer` | Module | 系统打印服务：图片 / PDF / 网页打印（PrintManager） |
| `uniplugin_richalert` | Module | 富文本 Alert 弹窗 |
| `uniplugin_module` / `uniplugin_component` | 示例 | DCloud 官方插件模板 |
| `uts-toast` | UTS | UTS 语言原生插件（Kotlin）示例 |

## 快速开始

### 环境要求

- Android Studio（含 Gradle 同步环境）
- JDK 8+
- compileSdk 29~35（各模块不同，以模块 `build.gradle` 为准）

### 构建命令

```bash
# 同步 Gradle 依赖
./gradlew build

# 构建调试版 APK
./gradlew assembleDebug

# 构建发布版 APK（使用测试密钥）
./gradlew assembleRelease

# 清理构建
./gradlew clean
```

### 测试

项目无单元测试配置，需通过真机/模拟器运行验证。

## 插件架构

### 插件注册机制

所有插件在 `app/src/main/assets/dcloud_uniplugins.json` 中注册：

```json
{
  "nativePlugins": [
    { "type": "module",   "name": "模块名", "class": "全限定类名" },
    { "type": "component", "name": "组件名", "class": "全限定类名" }
  ]
}
```

### 插件类型

1. **Module 插件**：继承 `UniModule`，前端通过 `uni.requireNativePlugin('ModuleName')` 调用
2. **Component 插件**：继承 `UniComponent`，仅在 `.nvue` 页面中可用

### 依赖约定

所有插件模块统一使用 `compileOnly` 引用核心库，避免重复打包：

```gradle
compileOnly fileTree(dir: '../app/libs', include: ['uniapp-v8-release.aar'])
```

## 核心插件：uniplugin_webview

`UniWebView` 组件（仅限 `.nvue` 页面）能力概览：

- **加载**：支持网络 URL 与本地路径（`IS_ANDROID_STUDIO` 开关切换调试环境，前端零改动）
- **事件**：`@onpagestart` / `@onpagefinish` / `@onpageerror` / `@onjsmessage` / `@onprogress` / `@ondownloadcomplete`（事件名全小写）
- **组件方法**：`reload` / `goBack` / `canGoBack` / `evaluateJavascript` / `clearCache` / `clearSessionAndReload` 等
- **文件选择**：H5 `<input type="file">` 走系统 SAF，无需存储权限；`capture` 拍照/录像经 FileProvider 回传
- **文件下载**：blob 拦截、JsBridge 直通、附件 DownloadListener 三通道，保存至系统下载文件夹，无需存储权限
- **安全**：禁文件访问、拦截 `file://`、Intent 注入防护

> 完整 API 与集成说明见 [`uniplugin_webview/README.md`](uniplugin_webview/README.md)、[`uniplugin_webview/调用指南.md`](uniplugin_webview/调用指南.md)、[`uniplugin_webview/H5下载对接指南.md`](uniplugin_webview/H5下载对接指南.md)。

## 前端集成

前端示例位于 [`uniapp示例工程源码/unipluginDemo/`](uniapp示例工程源码/unipluginDemo/)。

> ⚠️ 组件插件只能在 `.nvue` 页面使用，事件名会被编译器转为全小写（如 `@onpagestart`）。

## 自定义基座打包

### Android Studio 离线打包

1. 检查 `settings.gradle` 包含所有插件模块
2. 确认 `app/build.gradle` 包含插件依赖
3. 验证 `dcloud_uniplugins.json` 插件注册
4. 构建 APK 并签名

### HBuilderX 云打包

准备 `package.json` 和 `plugin.xml`，参考 [`uniplugin_webview/自定义基座打包指南.md`](uniplugin_webview/自定义基座打包指南.md)。

## 签名配置

测试密钥：`app/testuniapp.keystore`

- 别名：`testuniappalias`
- 密码：`123456`

> ⚠️ 仅用于开发调试，正式发布请更换正式签名。

## 新增插件模块

新增插件模块后需同步三处：

1. `settings.gradle` — 添加 `include ':模块名'`
2. `app/build.gradle` — 添加模块依赖
3. `app/src/main/assets/dcloud_uniplugins.json` — 注册插件类
