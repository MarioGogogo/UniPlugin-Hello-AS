# mxop-KeyStore 加密插件

基于 Android Keystore API 的硬件级安全加密模块，提供密钥的安全存储和数据加解密功能。

## 功能特性

- **硬件级安全**：密钥存储在 Android Keystore 硬件安全区域
- **AES-GCM 256位加密**：军用级加密强度
- **密钥不可提取**：防止密钥被恶意提取
- **完整生命周期管理**：支持密钥生成、加密、解密、删除

## 系统要求

- **uni-app**：HBuilderX 3.0+
- **Android**：API 23 (Android 6.0) 及以上
- **硬件支持**：推荐使用支持硬件密钥存储的设备

## 插件安装

### 1. 目录结构

在 uni-app 项目根目录创建 `nativeplugins` 目录，结构如下：

```
uni-app-project/
├── nativeplugins/
│   └── mxop-KeyStore/
│       ├── android/
│       │   └── uniplugin_keystore-release.aar
│       └── package.json
└── ...
```

### 2. package.json

```json
{
  "name": "mxop-KeyStore",
  "id": "mxop-KeyStore",
  "version": "1.0.0",
  "description": "基于 Android Keystore 的硬件级加密插件",
  "_platforms": ["Android"],
  "plugins": [
    {
      "type": "module",
      "name": "mxop-KeyStore",
      "class": "io.dcloud.uniplugin.KeystoreModule"
    }
  ]
}
```

### 3. manifest.json 配置

在 `manifest.json` 中添加权限配置：

```json
{
  "app-plus": {
    "distribute": {
      "android": {
        "permissions": [
          "<uses-permission android:name=\"android.permission.USE_FINGERPRINT\"/>"
        ]
      }
    }
  }
}
```

## 前端调用示例

### 引入插件

```javascript
const keyStore = uni.requireNativePlugin("mxop-KeyStore");
```

### 1. 生成密钥

```javascript
keyStore.generateKey("my_secret_key", (result) => {
    if (result.success) {
        console.log("密钥生成成功:", result.message);
    } else {
        console.error("生成失败:", result.message);
    }
});
```

### 2. 加密数据

```javascript
const sensitiveData = "这是需要加密的敏感数据";

keyStore.encryptAndStore("my_secret_key", sensitiveData, (result) => {
    if (result.success) {
        console.log("加密成功!");
        // 保存加密结果到本地存储
        uni.setStorageSync("encrypted_data", result.encryptedData);
        uni.setStorageSync("iv", result.iv);
    } else {
        console.error("加密失败:", result.message);
    }
});
```

### 3. 解密数据

```javascript
const encryptedData = uni.getStorageSync("encrypted_data");
const iv = uni.getStorageSync("iv");

keyStore.retrieveAndDecrypt("my_secret_key", encryptedData, iv, (result) => {
    if (result.success) {
        console.log("解密成功, 原始数据:", result.data);
    } else {
        console.error("解密失败:", result.message);
    }
});
```

### 4. 检查密钥是否存在

```javascript
keyStore.hasKey("my_secret_key", (result) => {
    if (result.exists) {
        console.log("密钥已存在");
    } else {
        console.log("密钥不存在");
    }
});
```

### 5. 获取密钥信息

```javascript
keyStore.getKeyInfo("my_secret_key", (result) => {
    if (result.success) {
        console.log("密钥名称:", result.keyName);
        console.log("算法:", result.algorithm);
        console.log("密钥长度:", result.keySize);
        console.log("硬件安全:", result.insideSecureHardware);
    }
});
```

### 6. 列出所有密钥

```javascript
keyStore.listKeys((result) => {
    if (result.success) {
        console.log("密钥数量:", result.count);
        console.log("密钥列表:", result.keys);
    }
});
```

### 7. 删除密钥

```javascript
keyStore.deleteKey("my_secret_key", (result) => {
    if (result.success) {
        console.log("密钥已删除");
    }
});
```

### 8. 清空所有密钥（谨慎使用）

```javascript
keyStore.clearAllKeys((result) => {
    if (result.success) {
        console.log("已清空所有密钥, 删除数量:", result.deletedCount);
    }
});
```

## 完整使用示例：Token 安全存储

```vue
<template>
  <view class="container">
    <button @click="saveToken">保存 Token</button>
    <button @click="getToken">读取 Token</button>
    <button @click="deleteToken">删除 Token</button>
  </view>
</template>

<script>
const keyStore = uni.requireNativePlugin("mxop-KeyStore");
const KEY_NAME = "app_user_token";

export default {
  methods: {
    // 保存 Token
    saveToken() {
      const token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."; // 你的 token

      keyStore.encryptAndStore(KEY_NAME, token, (result) => {
        if (result.success) {
          uni.setStorageSync("token_encrypted", result.encryptedData);
          uni.setStorageSync("token_iv", result.iv);
          uni.showToast({ title: "Token 已加密保存", icon: "success" });
        } else {
          uni.showToast({ title: "保存失败: " + result.message, icon: "none" });
        }
      });
    },

    // 读取 Token
    getToken() {
      const encryptedData = uni.getStorageSync("token_encrypted");
      const iv = uni.getStorageSync("token_iv");

      if (!encryptedData || !iv) {
        uni.showToast({ title: "未找到保存的 Token", icon: "none" });
        return;
      }

      keyStore.retrieveAndDecrypt(KEY_NAME, encryptedData, iv, (result) => {
        if (result.success) {
          console.log("Token:", result.data);
          uni.showToast({ title: "Token 读取成功", icon: "success" });
          // 使用 result.data 进行 API 调用
        } else {
          uni.showToast({ title: "读取失败: " + result.message, icon: "none" });
        }
      });
    },

    // 删除 Token
    deleteToken() {
      keyStore.deleteKey(KEY_NAME, (result) => {
        if (result.success) {
          uni.removeStorageSync("token_encrypted");
          uni.removeStorageSync("token_iv");
          uni.showToast({ title: "Token 已删除", icon: "success" });
        }
      });
    }
  }
};
</script>
```

## API 说明

| 方法 | 参数 | 返回值 |
|------|------|--------|
| `generateKey(keyName, callback)` | keyName: 密钥名称<br>callback: 回调函数 | `{success: boolean, message: string}` |
| `encryptAndStore(keyName, data, callback)` | keyName: 密钥名称<br>data: 明文数据<br>callback: 回调函数 | `{success: boolean, encryptedData: string, iv: string}` |
| `retrieveAndDecrypt(keyName, encryptedData, iv, callback)` | keyName: 密钥名称<br>encryptedData: Base64 密文<br>iv: Base64 IV<br>callback: 回调函数 | `{success: boolean, data: string}` |
| `hasKey(keyName, callback)` | keyName: 密钥名称<br>callback: 回调函数 | `{success: boolean, exists: boolean}` |
| `getKeyInfo(keyName, callback)` | keyName: 密钥名称<br>callback: 回调函数 | `{success: boolean, keyName, algorithm, keySize, insideSecureHardware}` |
| `listKeys(callback)` | callback: 回调函数 | `{success: boolean, keys: string[], count: number}` |
| `deleteKey(keyName, callback)` | keyName: 密钥名称<br>callback: 回调函数 | `{success: boolean, message: string}` |
| `clearAllKeys(callback)` | callback: 回调函数 | `{success: boolean, deletedCount: number}` |

## 返回数据格式

### 成功响应

```json
{
  "success": true,
  "message": "操作成功"
}
```

### 加密响应

```json
{
  "success": true,
  "message": "加密成功",
  "encryptedData": "Base64编码的密文",
  "iv": "Base64编码的IV向量",
  "keyName": "密钥名称"
}
```

### 解密响应

```json
{
  "success": true,
  "message": "解密成功",
  "data": "解密后的明文数据"
}
```

### 错误响应

```json
{
  "success": false,
  "message": "错误信息描述"
}
```

## 安全建议

1. **加密数据存储**：加密后的密文可以安全存储在 `uni.setStorageSync` 中，但 IV 必须一并保存
2. **密钥命名规范**：建议使用应用包名前缀，如 `com.example.app.user_token`
3. **定期清理**：不需要的密钥应及时删除
4. **备份机制**：Keystore 密钥无法备份，卸载应用会清除所有密钥
5. **生物识别**：如需更高安全性，可结合指纹/面部识别使用

## 云打包配置

在 HBuilderX 中：

1. **发行** → **原生 App-云打包**
2. 选择 Android 平台
3. 确认 `nativeplugins` 目录中包含 `mxop-KeyStore` 插件
4. 点击打包

## 常见问题

**Q: 加密后的数据可以跨设备使用吗？**
A: 不可以。Keystore 密钥绑定设备硬件，卸载重装或更换设备后无法解密。

**Q: 最低支持什么 Android 版本？**
A: Android 6.0 (API 23) 及以上。

**Q: 如何确认设备支持硬件密钥存储？**
A: 调用 `getKeyInfo()` 方法，检查 `insideSecureHardware` 字段。

**Q: 卸载应用后密钥还在吗？**
A: 不在。Keystore 密钥随应用卸载而清除。

## License

MIT
