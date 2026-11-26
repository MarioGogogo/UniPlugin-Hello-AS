# UniPrinterModule API 使用文档

安卓原生**外部打印机插件**（基于系统 Android Print Framework / PrintManager），
支持打印**图片、PDF、网页/HTML**，无需引入第三方 SDK，打印时系统弹出打印对话框，
可选择本机已安装的任何打印机（WiFi / USB / 蓝牙打印机）。

> 网络打印机（WiFi）需在手机上安装对应的打印服务，如 **Mopria 打印服务**（Google Play 免费）、
> HP Print Service、Epson Print Enabler、佳能打印插件等；安装后本插件即可自动发现并打印。

## 引入插件

```javascript
const printer = uni.requireNativePlugin('UniPrinterModule')
```

## 打印流程

```
printImage / printPdf / printWebPage
        │  ① 提交后立即回调 { state: "queued" }
        ▼
系统打印对话框（用户选择打印机、份数、双面等）
        │  ② 状态变化持续回调（keepAlive）
        ▼
queued → started → blocked → completed / failed / cancelled（最后一次回调 isFinal=true）
```

三个打印方法的回调格式完全一致（可多次收到回调）：

```javascript
{
  code: Number,        // 0-正常（含各状态回调），其他-错误
  jobId: String,       // 打印任务 ID（用于取消/查询）
  jobName: String,     // 任务名称
  state: String,       // queued | started | blocked | completed | failed | cancelled
  isFinal: Boolean,    // true 表示任务已结束（最后一次回调）
  message: String      // 状态描述 / 错误信息
}
```

---

## 通用打印参数

以下参数对 `printImage` / `printPdf` / `printWebPage` 均有效：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| jobName | String | 否 | 打印任务名称，默认"图片打印"/"PDF打印"/"网页打印" |
| pageSize | String | 否 | 纸张：A3/A4/A5/B5/LETTER/LEGAL/TABLOID/EXECUTIVE/CUSTOM，默认 A4 |
| customWidthMm | Number | 否 | 自定义纸张宽度(mm)，pageSize=CUSTOM 时生效 |
| customHeightMm | Number | 否 | 自定义纸张高度(mm)，pageSize=CUSTOM 时生效 |
| orientation | String | 否 | PORTRAIT（竖版）/ LANDSCAPE（横版），默认 PORTRAIT |
| dpi | Number | 否 | 打印分辨率，默认 300 |
| colorMode | String | 否 | COLOR / MONOCHROME，默认 COLOR |
| marginMm | Number | 否 | 页边距(mm)，默认 0 |

---

## 1. checkSupport - 检测打印支持

```javascript
printer.checkSupport((res) => {
  // { code: 0, supported: true, printServiceCount: 1, message: "检测到 1 个打印服务" }
  if (!res.supported || res.printServiceCount === 0) {
    console.log('请先安装 Mopria 等打印服务')
  }
})
```

## 2. getPrintServices - 打印服务列表

```javascript
printer.getPrintServices((res) => {
  // { code: 0, count: 1, services: [{ id: "...", label: "Mopria Print Service", enabled: true }] }
})
```

## 3. getPrintJobs / cancelPrintJob - 任务管理

```javascript
printer.getPrintJobs((res) => {
  // { code: 0, count: 1, jobs: [{ id: "xxx", label: "图片打印", state: "started" }] }
})

printer.cancelPrintJob({ jobId: 'xxx' }, (res) => {
  console.log(res.message)
})
```

---

## 4. printImage - 打印图片

#### 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| imagePath | String | 二选一 | 图片来源（见下方"支持的资源来源"） |
| imageBase64 | String | 二选一 | 纯 base64 图片数据 |
| fitMode | String | 否 | FIT（整图居中）/ FILL（裁边填满）/ STRETCH（拉伸）/ CENTER（原尺寸居中），默认 FIT |
| split | Boolean | 否 | true 时长图按页宽等比缩放自动分多页（适合长截图），默认 false |

#### 支持的资源来源（imagePath）

- 本地绝对路径：`/storage/emulated/0/DCIM/xxx.jpg`
- `file://` 协议：`file:///storage/emulated/0/xxx.jpg`
- `content://` 协议：`content://media/external/images/media/xxx`（相册/文件选择器返回）
- 网络地址：`https://example.com/a.png`（插件自动下载）
- base64：`data:image/png;base64,xxxx`
- assets 资源：`assets://static/xxx.png` 或 `file:///android_asset/static/xxx.png`

#### 示例

```javascript
printer.printImage({
  imagePath: '/storage/emulated/0/DCIM/test.jpg',  // 或网络 URL / base64
  jobName: '我的图片',
  pageSize: 'A4',
  orientation: 'PORTRAIT',
  fitMode: 'FIT'
}, (res) => {
  if (res.code !== 0) {
    console.error('打印失败:', res.message)
    return
  }
  console.log('状态:', res.state, res.message)
  if (res.isFinal) {
    // completed / failed / cancelled
  }
})
```

---

## 5. printPdf - 打印 PDF

#### 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| filePath | String | 二选一 | PDF 来源（与 printImage 相同的资源来源规则） |
| fileBase64 | String | 二选一 | 纯 base64 PDF 数据 |
| renderScale | Number | 否 | PDF 渲染分辨率倍数 1~4，默认 3（约 216dpi），越大越清晰越耗内存 |

#### 示例

```javascript
// 方式一：直接传本地文件/网络地址
printer.printPdf({
  filePath: 'https://example.com/order.pdf',
  jobName: '订单PDF'
}, callback)

// 方式二：先用 uni.downloadFile 下载再打印（可拿到进度）
uni.downloadFile({
  url: 'https://example.com/order.pdf',
  success: (res) => {
    // App 端把 plus 路径转换为 file:/// 绝对路径
    const path = plus.io.convertLocalFileSystemURL(res.tempFilePath)
    printer.printPdf({ filePath: path, jobName: '订单PDF' }, callback)
  }
})
```

---

## 6. printWebPage - 打印网页 / HTML

#### 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | String | 二选一 | 网页地址 |
| html | String | 二选一 | HTML 字符串 |
| baseUrl | String | 否 | html 模式下相对路径基准，默认 file:///android_asset/ |
| loadTimeoutMs | Number | 否 | 加载超时(毫秒)，默认 30000 |
| loadDelayMs | Number | 否 | 加载完成后等待布局延迟(毫秒)，默认 500 |

#### 示例

```javascript
// 打印网页
printer.printWebPage({
  url: 'https://www.baidu.com',
  jobName: '百度'
}, callback)

// 打印 HTML 字符串
printer.printWebPage({
  html: '<h1>你好</h1><p>这是测试内容</p>',
  jobName: 'HTML打印'
}, callback)
```

---

## 完整示例（图片打印）

```vue
<template>
  <view>
    <button @click="chooseAndPrint">选择图片并打印</button>
  </view>
</template>

<script>
export default {
  data() {
    return { printer: null }
  },
  onReady() {
    this.printer = uni.requireNativePlugin('UniPrinterModule')
  },
  methods: {
    chooseAndPrint() {
      uni.chooseImage({
        count: 1,
        success: (res) => {
          let path = res.tempFilePaths[0]
          // App 端转换为 file:/// 绝对路径
          if (typeof plus !== 'undefined') {
            path = plus.io.convertLocalFileSystemURL(path)
          }
          this.printer.printImage({
            imagePath: path,
            jobName: '图片打印',
            pageSize: 'A4',
            fitMode: 'FIT'
          }, (res) => {
            if (res.code === 0) {
              uni.showToast({ title: res.message, icon: 'none' })
            } else {
              uni.showToast({ title: res.message, icon: 'none' })
            }
          })
        }
      })
    }
  }
}
</script>
```

---

## 注意事项

1. **需要安装打印服务**：WiFi 网络打印机需要先在手机安装对应的打印服务应用
   （Mopria / HP / Epson / 佳能等），打印对话框才会列出打印机。
2. **设备需连接打印机所在网络**：手机与打印机需在同一局域网，或打印机支持蓝牙/USB 直连。
3. **打印对话框由系统弹出**：提交任务后用户需要在系统对话框中选择打印机并确认，这是 Android 系统标准流程，无法跳过。
4. **回调可多次触发**：提交后回调会持续收到状态更新，请用 `res.isFinal` 判断任务结束。
5. **网络图片/PDF 会自动下载**：加载过程在后台线程执行，不会卡 UI。
6. **大图/大 PDF 已做内存保护**：图片自动采样到单边 3000px，PDF 按页渲染且只保留一页位图。
7. **网页打印为离屏渲染**：不会显示 WebView 界面，打印完成或超时后自动销毁，不影响页面。
8. **cancelPrintJob 仅取消排队/进行中的任务**：已完成的无法取消。
9. **Android 6.0+ 需动态申请存储权限**：选择相册图片打印前，请先用 `uni.authorize` 或权限插件申请
   `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` 权限。
