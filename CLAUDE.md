# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是 uni-app 原生插件 Android 离线打包项目，包含多个原生插件模块和一个示例应用。

### 项目结构

```
UniPlugin-Hello-AS/
├── app/                           # 主应用模块（自定义基座）
│   ├── src/main/
│   │   ├── assets/               # dcloud_uniplugins.json 插件注册配置
│   │   ├── java/                 # 空目录（主要逻辑在插件模块）
│   │   └── AndroidManifest.xml  # 应用清单
│   └── libs/                     # uniapp-v8-release.aar 核心库
├── uniplugin_module/             # 模块插件示例（TestModule）
├── uniplugin_component/          # 组件插件示例（TestText/RichAlert）
├── uniplugin_richalert/          # 富文本 Alert 插件
├── uniplugin_webview/            # WebView 插件（主要插件，含详细文档）
├── uniplugin_betech_nfc/         # 必达门锁 NFC 插件（读卡/写卡/注销）
├── uniplugin_keystore/           # 硬件级安全加密插件（AES-GCM 256位）
├── uniplugin_printer/            # 外部打印机插件（图片/PDF/网页打印，PrintManager）
├── uts-toast/                    # UTS 原生插件（Kotlin）
└── uniapp示例工程源码/            # uni-app 前端示例工程
```

## 常用命令

### 构建项目

```bash
# 同步 Gradle 依赖
./gradlew build

# 清理构建
./gradlew clean

# 构建调试版 APK
./gradlew assembleDebug

# 构建发布版 APK（使用测试密钥）
./gradlew assembleRelease
```

### 运行测试

项目无单元测试配置，需通过真机/模拟器测试。

### 签名配置

测试密钥：`app/testuniapp.keystore`
- 别名：`testuniappalias`
- 密码：`123456`

## 插件架构

### 插件注册机制

插件在 `app/src/main/assets/dcloud_uniplugins.json` 中注册：

```json
{
  "nativePlugins": [
    {
      "type": "module",     // 模块类型：全局方法
      "name": "ModuleName",
      "class": "全限定类名"
    },
    {
      "type": "component",  // 组件类型：nvue 组件
      "name": "ComponentName",
      "class": "全限定类名"
    }
  ]
}
```

### 模块依赖关系

所有插件模块都使用 `compileOnly` 引用 `uniapp-v8-release.aar`，避免重复打包：

```gradle
compileOnly fileTree(dir: '../app/libs', include: ['uniapp-v8-release.aar'])
```

### 插件类型

1. **Module 插件**：继承 `UniModule`，通过 `uni.requireNativePlugin('ModuleName')` 调用
2. **Component 插件**：继承 `UniComponent`，仅在 `.nvue` 页面中可用

## 核心插件：uniplugin_webview

WebView 插件是项目的主要功能模块，提供完整的文档和实现。

### 调试环境开关

在 `WebViewComponent.java` 顶部有环境开关 `IS_ANDROID_STUDIO`：

- `true` = Android Studio 真机调试，本地路径拼接为 assets 完整路径
- `false` = uni-app 自定义基座，动态扫描外部存储路径

### 安全配置

WebView 已配置以下安全措施：
- 禁止文件访问（`setAllowFileAccess(false)`）
- 拦截 `file://` 协议
- Intent 注入防护（禁止携带 component/package）

### 回调使用规范

- `invoke()`：一次性回调（如 `evaluateJavascript`）
- `invokeAndKeepAlive()`：多次回调（生命周期事件、进度、JsBridge 消息）

## 依赖配置

### 编译 SDK 版本

- `compileSdkVersion`：29（大部分模块）/ 35（app 模块）/ 34（uts-toast）
- `minSdkVersion`：16/21/22（模块间不同）
- `targetSdkVersion`：28（大部分模块）/ 32（app 模块）

### 核心依赖

- `com.alibaba:fastjson:1.2.83` - JSON 处理
- `androidx.recyclerview:recyclerview:1.0.0` - RecyclerView 组件
- `androidx.appcompat:appcompat:1.0.0` - AppCompat 兼容库

## 前端集成

前端项目位于 `uniapp示例工程源码/unipluginDemo/`。

### nvue 页面注意事项

组件插件（如 `UniWebView`）**只能用于 `.nvue` 页面**，事件名必须全小写（如 `@onpagestart`）。

## 自定义基座打包

### Android Studio 离线打包

1. 检查 `settings.gradle` 包含所有插件模块
2. 确认 `app/build.gradle` 包含插件依赖
3. 验证 `dcloud_uniplugins.json` 插件注册
4. 构建 APK 并签名

### HBuilderX 云打包

需准备 `package.json` 和 `plugin.xml`，详见 `uniplugin_webview/自定义基座打包指南.md`。

## 关键文件位置

| 文件 | 位置 |
|------|------|
| 插件注册 | `app/src/main/assets/dcloud_uniplugins.json` |
| 应用清单 | `app/src/main/AndroidManifest.xml` |
| Gradle 配置 | `build.gradle`（根）、`app/build.gradle` |
| WebView 组件 | `uniplugin_webview/src/main/java/.../WebViewComponent.java` |
| WebView 模块 | `uniplugin_webview/src/main/java/.../WebViewModule.java` |
| WebView 文档 | `uniplugin_webview/README.md`、`uniplugin_webview/调用指南.md`、`uniplugin_webview/H5下载对接指南.md` |
| BetechNFC 文档 | `uniplugin_betech_nfc/README.md` |
| KeyStore 文档 | `uniplugin_keystore/README.md`、`uniplugin_keystore/UNIAPP_README.md` |
| 示例前端 | `uniapp示例工程源码/unipluginDemo/` |

## 开发提示

- 新增插件模块后需同步 `settings.gradle`、`app/build.gradle` 和 `dcloud_uniplugins.json`
- Component 插件只能在 nvue 页面使用，事件名会被编译器转为全小写
- 切换调试环境只修改 `IS_ANDROID_STUDIO` 常量即可，前端代码无需改动
