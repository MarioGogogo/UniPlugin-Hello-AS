# mxop-KeyStore 加密插件

基于 Android Keystore API 的硬件级安全加密模块，提供密钥的安全存储和数据加解密功能。

## 功能特性

- **硬件级安全**：密钥存储在 Android Keystore 硬件安全区域
- **AES-GCM 256位加密**：军用级加密强度
- **密钥不可提取**：防止密钥被恶意提取
- **完整生命周期管理**：支持密钥生成、加密、解密、删除

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

## API 说明

| 方法 | 参数 | 说明 |
|------|------|------|
| `generateKey(keyName, callback)` | keyName: 密钥名称<br>callback: 回调函数 | 生成并存储密钥到 Keystore |
| `encryptAndStore(keyName, data, callback)` | keyName: 密钥名称<br>data: 明文数据<br>callback: 回调函数 | 加密数据，返回 Base64 编码的密文和 IV |
| `retrieveAndDecrypt(keyName, encryptedData, iv, callback)` | keyName: 密钥名称<br>encryptedData: Base64 密文<br>iv: Base64 IV<br>callback: 回调函数 | 解密数据，返回明文 |
| `hasKey(keyName, callback)` | keyName: 密钥名称<br>callback: 回调函数 | 检查密钥是否存在 |
| `getKeyInfo(keyName, callback)` | keyName: 密钥名称<br>callback: 回调函数 | 获取密钥详细信息 |
| `listKeys(callback)` | callback: 回调函数 | 列出所有密钥名称 |
| `deleteKey(keyName, callback)` | keyName: 密钥名称<br>callback: 回调函数 | 删除指定密钥 |
| `clearAllKeys(callback)` | callback: 回调函数 | 清空所有密钥 |

## 安全建议

1. **加密数据存储**：加密后的密文可以存储在本地，但 IV 必须一并保存
2. **密钥命名规范**：建议使用应用包名前缀，如 `com.example.app.user_token`
3. **定期清理**：不需要的密钥应及时删除
4. **备份机制**：Keystore 密钥无法备份，卸载应用会清除所有密钥

## 系统要求

- **Android 版本**：API 23 (Android 6.0) 及以上
- **硬件支持**：推荐使用支持硬件密钥存储的设备

## 返回数据格式

### 成功响应

```json
{
  "success": true,
  "message": "操作成功",
  "data": "..."
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

### 错误响应

```json
{
  "success": false,
  "message": "错误信息"
}
```
