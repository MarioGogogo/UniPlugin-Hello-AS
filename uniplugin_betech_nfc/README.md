# BetechNfcModule API 使用文档

必达门锁 NFC 插件 - 提供开门卡的读、写、注销功能。

## 引入插件

```javascript
const betechNfc = uni.requireNativePlugin('BetechNfcModule')
```

---

## 注册码说明

使用插件前需要先向必达获取注册码：
1. 联系必达官方，提供应用信息
2. 必达提供注册码
3. 调用 `initNFC()` 验证注册码

---

## API 方法

### 1. initNFC - 初始化并验证注册码

**必须首先调用**，验证注册码后自动启用 NFC 前台调度。

#### 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| registerCode | String | 是 | 必达提供的注册码 |

#### 回调结果

```javascript
{
  code: Number,    // 0-成功，20-注册失败
  message: String  // 结果描述
}
```

#### 示例

```javascript
betechNfc.initNFC({
  registerCode: "ABCD-1234-EFGH-5678"
}, (res) => {
  if (res.code === 0) {
    console.log("初始化成功")
  } else {
    console.error("初始化失败:", res.message)
  }
})
```

---

### 2. writeGuestCard - 写开门卡

将门卡信息写入 NFC 卡片。

#### 参数

| 参数 | 类型 | 必填 | 说明 | 取值范围/格式 |
|------|------|------|------|---------------|
| sectorNo | Number | 是 | 扇区号 | 1-15 |
| hotelType | Number | 是 | 酒店类型 | 1-非加密，2-加密 |
| hotelPwd | String | 是 | 酒店密码 | 任意字符串 |
| cardNo | Number | 是 | 逻辑卡号 | 1 ~ 4294967295 |
| guestSn | Number | 是 | 宾客流水号 | 1 ~ 4294967295 |
| guestNo | Number | 是 | 宾客序号 | 1 ~ 255 |
| doorId | String | 是 | 门锁代号 | **必须是6位字符串** |
| beginTime | String | 是 | 有效开始时间 | **格式: yyyy-MM-dd HH:mm** |
| endTime | String | 是 | 有效截止时间 | **格式: yyyy-MM-dd HH:mm** |
| liftData | String | 否 | 电梯控制码 | 16进制字符串，无则传空字符串 |
| usePowerSwitch | Boolean | 否 | 取电类型 | true 或 false |
| suitDoor | String | 否 | 套房号 | 无则传空字符串 |
| pubDoor | String | 否 | 公共门 | 无则传空字符串 |
| systemId | String | 否 | 取电标识 | 无则传空字符串 |

#### ⚠️ 时间格式要求

**必须严格遵循** `yyyy-MM-dd HH:mm` 格式：

- ✅ 正确：`2026-06-22 14:00`
- ❌ 错误：`2026-06-22 14:00:00`（不要秒）
- ❌ 错误：`2026/06/22 14:00`（不要斜杠）
- ❌ 错误：`2026-06-22T14:00`（不要 T 分隔）

#### 时间格式化示例

```javascript
function formatNfcTime(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

// 使用
const beginTime = formatNfcTime(new Date())           // 当前时间，如 "2026-06-22 14:00"
const endTime = formatNfcTime(new Date(Date.now() + 86400000))  // 明天此时
```

#### 回调结果

```javascript
{
  code: Number,          // 0-成功，其他-失败
  cardId: String,        // 物理卡号（成功时返回）
  message: String        // 结果描述
}
```

#### 示例

```javascript
betechNfc.writeGuestCard({
  sectorNo: 1,
  hotelType: 2,
  hotelPwd: "your-hotel-pwd",
  cardNo: 123456789,
  guestSn: 987654321,
  guestNo: 1,
  doorId: "010101",     // 6位门锁代号
  beginTime: "2026-06-22 14:00",
  endTime: "2026-06-23 12:00",
  liftData: "",
  usePowerSwitch: true
}, (res) => {
  if (res.code === 0) {
    console.log("写卡成功，物理卡号:", res.cardId)
  } else {
    console.error("写卡失败:", res.message)
  }
})
```

---

### 3. readGuestCard - 读开门卡

读取 NFC 卡片中的门卡信息。

#### 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sectorNo | Number | 是 | 扇区号（写卡时使用的扇区号） |
| hotelType | Number | 是 | 酒店类型（1非加密/2加密） |
| hotelPwd | String | 是 | 酒店密码（写卡时使用的密码） |

#### 回调结果

```javascript
{
  code: Number,          // 0-成功，其他-失败
  message: String,       // 结果描述
  data: {                // 卡片信息（成功时返回）
    cardNo: Number,      // 逻辑卡号
    guestSn: Number,     // 宾客流水号
    guestNo: Number,     // 宾客序号
    doorId: String,      // 门锁代号
    beginTime: String,   // 有效开始时间
    endTime: String,     // 有效截止时间
    liftData: String,    // 电梯控制码
    usePowerSwitch: Boolean,
    suitDoor: String,    // 套房号
    pubDoor: String,     // 公共门
    systemId: String,    // 取电标识
    isDisabled: Boolean  // 是否已注销
  }
}
```

#### 示例

```javascript
betechNfc.readGuestCard({
  sectorNo: 1,
  hotelType: 2,
  hotelPwd: "your-hotel-pwd"
}, (res) => {
  if (res.code === 0) {
    console.log("卡号:", res.data.cardNo)
    console.log("有效期:", res.data.beginTime, "至", res.data.endTime)
    console.log("房间:", res.data.doorId)
  } else {
    console.error("读卡失败:", res.message)
  }
})
```

---

### 4. enableNFCDispatch - 启用 NFC 监听

手动启用 NFC 前台调度。**建议在进入写卡/读卡页面或打开写卡弹窗时调用**，避免 NFC 监听一直开启影响其他应用使用 NFC。

#### 参数

无

#### 回调结果

```javascript
{
  code: Number,     // 0-成功，其他-失败
  message: String   // 结果描述
}
```

#### 示例

```javascript
// 进入写卡页面时启用
onShow() {
  betechNfc.enableNFCDispatch((res) => {
    console.log("NFC 监听已启用")
  })
}
```

---

### 5. disableNFCDispatch - 禁用 NFC 监听

手动禁用 NFC 前台调度。**建议在退出写卡/读卡页面或关闭弹窗时调用**，释放 NFC 资源。

#### 参数

无

#### 回调结果

```javascript
{
  code: Number,     // 0-成功，其他-失败
  message: String   // 结果描述
}
```

#### 示例

```javascript
// 退出写卡页面时禁用
onHide() {
  betechNfc.disableNFCDispatch((res) => {
    console.log("NFC 监听已禁用")
  })
}

// 或关闭弹窗时禁用
closeDialog() {
  betechNfc.disableNFCDispatch()
  this.dialogVisible = false
}
```

---

### 6. disableGuestCard - 注销开门卡

清除卡片中的宾客信息，使卡片失效。

#### 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sectorNo | Number | 是 | 扇区号（写卡时使用的扇区号） |
| hotelType | Number | 是 | 酒店类型（1非加密/2加密） |
| hotelPwd | String | 是 | 酒店密码（写卡时使用的密码） |

#### 回调结果

```javascript
{
  code: Number,     // 0-成功，其他-失败
  message: String   // 结果描述
}
```

#### 示例

```javascript
betechNfc.disableGuestCard({
  sectorNo: 1,
  hotelType: 2,
  hotelPwd: "your-hotel-pwd"
}, (res) => {
  if (res.code === 0) {
    console.log("注销成功")
    uni.showToast({ title: "注销成功", icon: "success" })
  } else {
    console.error("注销失败:", res.message)
  }
})
```

---

## 错误码说明

| code | 说明 | 处理建议 |
|------|------|----------|
| 0 | 操作成功 | - |
| 2 | 未检测到卡片 | 请将卡片贴近手机背面 |
| 4 | 读卡失败 | 重新尝试 |
| 5 | 酒店密码错误 | 检查 hotelPwd 参数 |
| 6 | 写卡失败 | 重新尝试或更换卡片 |
| 9 | 参数错误 | 检查参数格式，特别是时间格式 |
| 20 | SDK 未注册或注册码无效 | 检查 initNFC 是否成功 |
| -1 | 系统异常 | 查看日志排查 |

---

## 完整使用流程示例

```vue
<template>
  <view class="page">
    <button @click="handleInit">1. 初始化</button>
    <button @click="handleWrite">2. 写卡</button>
    <button @click="handleRead">3. 读卡</button>
    <button @click="handleDisable">4. 注销</button>
  </view>
</template>

<script>
// 时间格式化工具
function formatNfcTime(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

export default {
  data() {
    return {
      betechNfc: null,
      hotelPwd: "your-hotel-pwd",    // 替换为实际酒店密码
      registerCode: "your-reg-code"   // 替换为实际注册码
    }
  },

  onReady() {
    this.betechNfc = uni.requireNativePlugin('BetechNfcModule')
  },

  methods: {
    // 1. 初始化
    handleInit() {
      this.betechNfc.initNFC({
        registerCode: this.registerCode
      }, (res) => {
        uni.showToast({
          title: res.code === 0 ? "初始化成功" : res.message,
          icon: res.code === 0 ? "success" : "none"
        })
      })
    },

    // 2. 写卡
    handleWrite() {
      const now = new Date()
      const tomorrow = new Date(Date.now() + 86400000)

      this.betechNfc.writeGuestCard({
        sectorNo: 1,
        hotelType: 2,
        hotelPwd: this.hotelPwd,
        cardNo: Date.now(),           // 使用时间戳作为卡号
        guestSn: Date.now(),           // 使用时间戳作为流水号
        guestNo: 1,
        doorId: "010101",             // 6位门锁代号
        beginTime: formatNfcTime(now),      // "2026-06-22 14:00"
        endTime: formatNfcTime(tomorrow),  // "2026-06-23 14:00"
        liftData: "",
        usePowerSwitch: true
      }, (res) => {
        if (res.code === 0) {
          uni.showToast({
            title: "写卡成功：" + res.cardId,
            icon: "success"
          })
        } else {
          uni.showToast({
            title: "写卡失败：" + res.message,
            icon: "none"
          })
        }
      })
    },

    // 3. 读卡
    handleRead() {
      this.betechNfc.readGuestCard({
        sectorNo: 1,
        hotelType: 2,
        hotelPwd: this.hotelPwd
      }, (res) => {
        if (res.code === 0) {
          console.log("卡信息:", res.data)
          uni.showModal({
            title: "读卡成功",
            content: `卡号: ${res.data.cardNo}\n有效期: ${res.data.beginTime} - ${res.data.endTime}`
          })
        } else {
          uni.showToast({
            title: "读卡失败：" + res.message,
            icon: "none"
          })
        }
      })
    },

    // 4. 注销
    handleDisable() {
      this.betechNfc.disableGuestCard({
        sectorNo: 1,
        hotelType: 2,
        hotelPwd: this.hotelPwd
      }, (res) => {
        uni.showToast({
          title: res.code === 0 ? "注销成功" : "注销失败：" + res.message,
          icon: res.code === 0 ? "success" : "none"
        })
      })
    }
  }
}
</script>
```

---

## 注意事项

1. **必须先调用 initNFC**，成功后才能进行其他操作
2. **时间格式严格**：必须是 `yyyy-MM-dd HH:mm`，不要秒、不要斜杠
3. **doorId 必须是 6 位**：不足 6 位请在前面补 0
4. **大整数参数**：cardNo 和 guestSn 最大值 4294967295，在 JS 安全整数范围内
5. **NFC 操作需要手机支持**：确保设备有 NFC 功能且已开启
6. **写卡时请将卡片贴近手机背面**：操作过程中不要移开卡片
7. **同一扇区号和密码**：读卡、注销时必须使用写卡时相同的 sectorNo 和 hotelPwd
