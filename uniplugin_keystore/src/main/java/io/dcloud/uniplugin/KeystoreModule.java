package io.dcloud.uniplugin;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

/**
 * mxop-KeyStore 加密插件
 * 基于 Android Keystore API 的硬件级安全加密模块
 *
 * 功能特性：
 * - 密钥存储在硬件安全区域（Keystore）
 * - AES-GCM 256位加密
 * - 防止密钥提取
 * - 支持密钥生成、加密存储、解密读取、删除
 */
public class KeystoreModule extends UniModule {

    private static final String TAG = "mxop-KeyStore";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ENCRYPTION_ALGO = "AES/GCM/NoPadding";
    private static final String KEY_ALGO = "AES";
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * 生成并存储密钥到 Keystore
     * @param keyName 密钥名称
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void generateKey(String keyName, UniJSCallback callback) {
        try {
            if (keyName == null || keyName.isEmpty()) {
                invokeError(callback, "密钥名称不能为空");
                return;
            }

            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (keyStore.containsAlias(keyName)) {
                invokeSuccess(callback, "密钥已存在", false);
                return;
            }

            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

            KeyGenParameterSpec keyGenSpec = new KeyGenParameterSpec.Builder(
                    keyName,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .setUserAuthenticationRequired(false)
                    .build();

            keyGenerator.init(keyGenSpec);
            keyGenerator.generateKey();

            invokeSuccess(callback, "密钥生成成功", true);

        } catch (Exception e) {
            Log.e(TAG, "生成密钥失败", e);
            invokeError(callback, "生成密钥失败: " + e.getMessage());
        }
    }

    /**
     * 加密数据并存储（不直接存储明文，存储加密后的数据）
     * @param keyName 密钥名称
     * @param data 要加密的明文数据
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void encryptAndStore(String keyName, String data, UniJSCallback callback) {
        try {
            if (keyName == null || keyName.isEmpty()) {
                invokeError(callback, "密钥名称不能为空");
                return;
            }

            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(keyName)) {
                generateKey(keyName, null);
                keyStore.load(null);
            }

            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(keyName, null);
            SecretKey secretKey = secretKeyEntry.getSecretKey();

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            String encryptedDataBase64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP);
            String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("message", "加密成功");
            result.put("encryptedData", encryptedDataBase64);
            result.put("iv", ivBase64);
            result.put("keyName", keyName);

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "加密失败", e);
            invokeError(callback, "加密失败: " + e.getMessage());
        }
    }

    /**
     * 从加密数据中解密并读取
     * @param keyName 密钥名称
     * @param encryptedDataBase64 Base64编码的加密数据
     * @param ivBase64 Base64编码的IV
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void retrieveAndDecrypt(String keyName, String encryptedDataBase64, String ivBase64, UniJSCallback callback) {
        try {
            if (keyName == null || keyName.isEmpty()) {
                invokeError(callback, "密钥名称不能为空");
                return;
            }

            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(keyName)) {
                invokeError(callback, "密钥不存在");
                return;
            }

            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(keyName, null);
            SecretKey secretKey = secretKeyEntry.getSecretKey();

            byte[] encryptedData = Base64.decode(encryptedDataBase64, Base64.NO_WRAP);
            byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGO);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);

            byte[] decryptedData = cipher.doFinal(encryptedData);
            String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("message", "解密成功");
            result.put("data", decryptedText);

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "解密失败", e);
            invokeError(callback, "解密失败: " + e.getMessage());
        }
    }

    /**
     * 兼容旧版接口（只传 keyName 和 callback）
     * 用于读取之前存储的数据
     */
    @UniJSMethod(uiThread = false)
    public void retrieveAndDecrypt(String keyName, UniJSCallback callback) {
        invokeError(callback, "请使用完整接口：retrieveAndDecrypt(keyName, encryptedData, iv, callback)");
    }

    /**
     * 检查密钥是否存在
     * @param keyName 密钥名称
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void hasKey(String keyName, UniJSCallback callback) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            boolean exists = keyStore.containsAlias(keyName);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("exists", exists);
            result.put("keyName", keyName);

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "检查密钥失败", e);
            invokeError(callback, "检查密钥失败: " + e.getMessage());
        }
    }

    /**
     * 删除密钥
     * @param keyName 密钥名称
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void deleteKey(String keyName, UniJSCallback callback) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(keyName)) {
                invokeError(callback, "密钥不存在");
                return;
            }

            keyStore.deleteEntry(keyName);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("message", "密钥已删除");
            result.put("keyName", keyName);

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "删除密钥失败", e);
            invokeError(callback, "删除密钥失败: " + e.getMessage());
        }
    }

    /**
     * 列出所有密钥
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void listKeys(UniJSCallback callback) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            java.util.Enumeration<String> aliases = keyStore.aliases();
            java.util.List<String> keyList = new java.util.ArrayList<>();

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    keyList.add(alias);
                }
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("keys", keyList);
            result.put("count", keyList.size());

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "列出密钥失败", e);
            invokeError(callback, "列出密钥失败: " + e.getMessage());
        }
    }

    /**
     * 获取密钥信息
     * @param keyName 密钥名称
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void getKeyInfo(String keyName, UniJSCallback callback) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(keyName)) {
                invokeError(callback, "密钥不存在");
                return;
            }

            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(keyName, null);
            SecretKey secretKey = secretKeyEntry.getSecretKey();

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("keyName", keyName);
            result.put("algorithm", secretKey.getAlgorithm());
            result.put("format", secretKey.getFormat());
            result.put("keySize", KEY_SIZE);
            result.put("insideSecureHardware", isInsideSecureHardware(keyName));

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "获取密钥信息失败", e);
            invokeError(callback, "获取密钥信息失败: " + e.getMessage());
        }
    }

    /**
     * 检查密钥是否在硬件安全区域
     */
    private boolean isInsideSecureHardware(String keyName) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(keyName, null);
                SecretKey secretKey = secretKeyEntry.getSecretKey();

                // 使用反射调用 isInsideSecureHardware() 方法
                try {
                    java.lang.reflect.Method method = secretKey.getClass().getMethod("isInsideSecureHardware");
                    return (Boolean) method.invoke(secretKey);
                } catch (Exception reflectException) {
                    Log.d(TAG, "无法调用 isInsideSecureHardware，设备可能不支持硬件密钥存储");
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "检查硬件安全失败", e);
            return false;
        }
    }

    /**
     * 使用 mxopDecrypt 进行加密（兼容外部 aar）
     * @param data 明文数据
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void mxopEncrypt(String data, UniJSCallback callback) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] keyBytes = new byte[32];
            random.nextBytes(keyBytes);

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, KEY_ALGO);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("encryptedData", Base64.encodeToString(encryptedData, Base64.NO_WRAP));
            result.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
            result.put("key", Base64.encodeToString(keyBytes, Base64.NO_WRAP));

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "mxop加密失败", e);
            invokeError(callback, "mxop加密失败: " + e.getMessage());
        }
    }

    /**
     * 使用 mxopDecrypt 进行解密（兼容外部 aar）
     * @param encryptedDataBase64 加密数据
     * @param ivBase64 IV向量
     * @param keyBase64 密钥
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void mxopDecrypt(String encryptedDataBase64, String ivBase64, String keyBase64, UniJSCallback callback) {
        try {
            byte[] encryptedData = Base64.decode(encryptedDataBase64, Base64.NO_WRAP);
            byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);
            byte[] keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP);

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, KEY_ALGO);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGO);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec);

            byte[] decryptedData = cipher.doFinal(encryptedData);
            String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("data", decryptedText);

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "mxop解密失败", e);
            invokeError(callback, "mxop解密失败: " + e.getMessage());
        }
    }

    /**
     * 清空所有密钥（谨慎使用）
     * @param callback 回调
     */
    @UniJSMethod(uiThread = false)
    public void clearAllKeys(UniJSCallback callback) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            java.util.Enumeration<String> aliases = keyStore.aliases();
            int deletedCount = 0;

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    keyStore.deleteEntry(alias);
                    deletedCount++;
                }
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("message", "已清空 " + deletedCount + " 个密钥");
            result.put("deletedCount", deletedCount);

            if (callback != null) {
                callback.invoke(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "清空密钥失败", e);
            invokeError(callback, "清空密钥失败: " + e.getMessage());
        }
    }

    private void invokeSuccess(UniJSCallback callback, String message, boolean data) {
        if (callback == null) return;
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("message", message);
        result.put("data", data);
        callback.invoke(result);
    }

    private void invokeError(UniJSCallback callback, String message) {
        if (callback == null) return;
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("message", message);
        callback.invoke(result);
    }
}
