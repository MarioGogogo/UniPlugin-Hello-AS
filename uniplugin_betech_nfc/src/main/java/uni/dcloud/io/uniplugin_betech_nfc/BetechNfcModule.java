package uni.dcloud.io.uniplugin_betech_nfc;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.betech.btlock.cards.GuestCard;
import com.betech.btlock.nfc.BTLockNfcHelper;
import com.betech.btlock.results.DataResult;
import com.betech.btlock.results.Result;
import com.betech.wehotel.register.OnRegisterCallback;

import io.dcloud.feature.uniapp.common.UniModule;
import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;

/**
 * 必达 NFC 门锁插件模块
 * 提供 NFC 门卡的读、写、注销功能
 */
public class BetechNfcModule extends UniModule {

    private static final String TAG = "BetechNfcModule";
    private boolean isInitialized = false;
    private boolean isDispatchEnabled = false;

    /**
     * 获取当前 Activity
     */
    private Activity getActivity() {
        if (mUniSDKInstance != null && mUniSDKInstance.getContext() instanceof Activity) {
            return (Activity) mUniSDKInstance.getContext();
        }
        return null;
    }

    /**
     * 初始化 NFC 并验证注册码
     * @param params 参数：{ registerCode: "注册码" }
     * @param callback 回调：{ code: 0, message: "注册成功" }
     */
    @UniJSMethod(uiThread = true)
    public void initNFC(JSONObject params, UniJSCallback callback) {
        try {
            Activity activity = getActivity();
            if (activity == null) {
                invokeError(callback, -1, "无法获取 Activity 上下文");
                return;
            }

            String registerCode = params.getString("registerCode");

            // 初始化 BTLockNfcHelper
            BTLockNfcHelper.init(activity);

            // 验证注册码
            BTLockNfcHelper.getInstance().registerCodeVerify2(registerCode, new OnRegisterCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "注册码验证成功");
                    isInitialized = true;

                    // 启用前台调度
                    try {
                        BTLockNfcHelper.getInstance().enableForegroundDispatch(activity);
                        isDispatchEnabled = true;
                        Log.d(TAG, "NFC 前台调度已启用");
                    } catch (Exception e) {
                        Log.e(TAG, "启用前台调度失败", e);
                    }

                    if (callback != null) {
                        JSONObject result = new JSONObject();
                        result.put("code", 0);
                        result.put("message", "注册成功");
                        callback.invoke(result);
                    }
                }

                @Override
                public void onFail(String message) {
                    Log.e(TAG, "注册码验证失败: " + message);
                    isInitialized = false;

                    if (callback != null) {
                        JSONObject result = new JSONObject();
                        result.put("code", 20);
                        result.put("message", message);
                        callback.invoke(result);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "initNFC 异常", e);
            if (callback != null) {
                JSONObject result = new JSONObject();
                result.put("code", -1);
                result.put("message", "初始化异常: " + e.getMessage());
                callback.invoke(result);
            }
        }
    }

    /**
     * 写开门卡
     * @param params 参数：{ sectorNo, hotelType, hotelPwd, cardNo, guestSn, guestNo, doorId, beginTime, endTime, liftData, usePowerSwitch }
     * @param callback 回调：{ code: 0, cardId: "物理卡号", message: "写卡成功" }
     */
    @UniJSMethod(uiThread = true)
    public void writeGuestCard(JSONObject params, UniJSCallback callback) {
        if (!isInitialized) {
            invokeError(callback, 20, "请先调用 initNFC 初始化");
            return;
        }

        try {
            int sectorNo = params.getIntValue("sectorNo");
            int hotelType = params.getIntValue("hotelType");
            String hotelPwd = params.getString("hotelPwd");

            // 构建 GuestCard 对象
            GuestCard card = new GuestCard();
            card.setCardNo(params.getLong("cardNo"));
            card.setGuestSn(params.getLong("guestSn"));
            card.setGuestNo(params.getIntValue("guestNo"));
            card.setDoorId(params.getString("doorId"));
            card.setBeginTime(params.getString("beginTime"));
            card.setEndTime(params.getString("endTime"));
            card.setLiftData(params.getString("liftData"));
            card.setUsePowerSwitch(params.getBooleanValue("usePowerSwitch"));

            // 可选参数
            card.setSuitDoor(params.getString("suitDoor"));
            card.setPubDoor(params.getString("pubDoor"));
            card.setSystemId(params.getString("systemId"));

            // 调用写卡
            Result result = BTLockNfcHelper.getInstance().writeGuestCard(sectorNo, hotelType, hotelPwd, card);

            if (callback != null) {
                JSONObject res = new JSONObject();
                res.put("code", result.getCode());
                res.put("message", result.getMessage());

                // Result 类有 getCardId() 方法获取物理卡号
                if (result.getCode() == 0 && result.getCardId() != null) {
                    res.put("cardId", result.getCardId());
                }

                callback.invoke(res);
            }

        } catch (Exception e) {
            Log.e(TAG, "writeGuestCard 异常", e);
            invokeError(callback, -1, "写卡异常: " + e.getMessage());
        }
    }

    /**
     * 读开门卡
     * @param params 参数：{ sectorNo, hotelType, hotelPwd }
     * @param callback 回调：{ code: 0, data: { ... }, message: "读卡成功" }
     */
    @UniJSMethod(uiThread = true)
    public void readGuestCard(JSONObject params, UniJSCallback callback) {
        if (!isInitialized) {
            invokeError(callback, 20, "请先调用 initNFC 初始化");
            return;
        }

        try {
            int sectorNo = params.getIntValue("sectorNo");
            int hotelType = params.getIntValue("hotelType");
            String hotelPwd = params.getString("hotelPwd");

            // 调用读卡
            DataResult<GuestCard> result = BTLockNfcHelper.getInstance().readGuestCard(sectorNo, hotelType, hotelPwd);

            if (callback != null) {
                JSONObject res = new JSONObject();
                res.put("code", result.getCode());
                res.put("message", result.getMessage());

                if (result.getCode() == 0 && result.getData() != null) {
                    GuestCard card = result.getData();
                    JSONObject data = guestCardToJson(card);
                    res.put("data", data);
                }

                callback.invoke(res);
            }

        } catch (Exception e) {
            Log.e(TAG, "readGuestCard 异常", e);
            invokeError(callback, -1, "读卡异常: " + e.getMessage());
        }
    }

    /**
     * 注销开门卡
     * @param params 参数：{ sectorNo, hotelType, hotelPwd }
     * @param callback 回调：{ code: 0, message: "注销成功" }
     */
    @UniJSMethod(uiThread = true)
    public void disableGuestCard(JSONObject params, UniJSCallback callback) {
        if (!isInitialized) {
            invokeError(callback, 20, "请先调用 initNFC 初始化");
            return;
        }

        try {
            int sectorNo = params.getIntValue("sectorNo");
            int hotelType = params.getIntValue("hotelType");
            String hotelPwd = params.getString("hotelPwd");

            // 调用注销
            Result result = BTLockNfcHelper.getInstance().disableGuestCard(sectorNo, hotelType, hotelPwd);

            if (callback != null) {
                JSONObject res = new JSONObject();
                res.put("code", result.getCode());
                res.put("message", result.getMessage());
                callback.invoke(res);
            }

        } catch (Exception e) {
            Log.e(TAG, "disableGuestCard 异常", e);
            invokeError(callback, -1, "注销异常: " + e.getMessage());
        }
    }

    /**
     * 启用 NFC 监听
     * 手动启用 NFC 前台调度，通常在进入写卡/读卡页面时调用
     * @param callback 回调：{ code: 0, message: "NFC 监听已启用" }
     */
    @UniJSMethod(uiThread = true)
    public void enableNFCDispatch(UniJSCallback callback) {
        if (!isInitialized) {
            invokeError(callback, 20, "请先调用 initNFC 初始化");
            return;
        }

        try {
            Activity activity = getActivity();
            if (activity == null) {
                invokeError(callback, -1, "无法获取 Activity 上下文");
                return;
            }

            BTLockNfcHelper.getInstance().enableForegroundDispatch(activity);
            isDispatchEnabled = true;
            Log.d(TAG, "NFC 前台调度已启用");

            if (callback != null) {
                JSONObject res = new JSONObject();
                res.put("code", 0);
                res.put("message", "NFC 监听已启用");
                callback.invoke(res);
            }

        } catch (Exception e) {
            Log.e(TAG, "启用 NFC 监听异常", e);
            invokeError(callback, -1, "启用失败: " + e.getMessage());
        }
    }

    /**
     * 禁用 NFC 监听
     * 手动禁用 NFC 前台调度，通常在退出写卡/读卡页面或关闭弹窗时调用
     * @param callback 回调：{ code: 0, message: "NFC 监听已禁用" }
     */
    @UniJSMethod(uiThread = true)
    public void disableNFCDispatch(UniJSCallback callback) {
        try {
            Activity activity = getActivity();
            if (activity != null) {
                BTLockNfcHelper.getInstance().disableForegroundDispatch(activity);
                isDispatchEnabled = false;
                Log.d(TAG, "NFC 前台调度已禁用");
            }

            if (callback != null) {
                JSONObject res = new JSONObject();
                res.put("code", 0);
                res.put("message", "NFC 监听已禁用");
                callback.invoke(res);
            }

        } catch (Exception e) {
            Log.e(TAG, "禁用 NFC 监听异常", e);
            invokeError(callback, -1, "禁用失败: " + e.getMessage());
        }
    }

    /**
     * 处理新的 Intent（NFC 刷卡事件）
     */
    public void onNewIntent(Intent intent) {
        try {
            BTLockNfcHelper.getInstance().onIntent(intent);
        } catch (Exception e) {
            Log.e(TAG, "onNewIntent 异常", e);
        }
    }

    /**
     * 模块销毁时禁用前台调度
     */
    public void destroy() {
        try {
            if (isDispatchEnabled) {
                Activity activity = getActivity();
                if (activity != null) {
                    BTLockNfcHelper.getInstance().disableForegroundDispatch(activity);
                    isDispatchEnabled = false;
                    Log.d(TAG, "NFC 前台调度已禁用");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "禁用前台调度异常", e);
        }
    }

    /**
     * 将 GuestCard 对象转换为 JSONObject
     */
    private JSONObject guestCardToJson(GuestCard card) {
        JSONObject json = new JSONObject();
        json.put("cardNo", card.getCardNo());
        json.put("guestSn", card.getGuestSn());
        json.put("guestNo", card.getGuestNo());
        json.put("doorId", card.getDoorId());
        json.put("beginTime", card.getBeginTime());
        json.put("endTime", card.getEndTime());
        json.put("liftData", card.getLiftData());
        json.put("usePowerSwitch", card.isUsePowerSwitch());
        json.put("suitDoor", card.getSuitDoor());
        json.put("pubDoor", card.getPubDoor());
        json.put("systemId", card.getSystemId());
        json.put("isDisabled", card.isDisabled());
        return json;
    }

    /**
     * 返回错误回调
     */
    private void invokeError(UniJSCallback callback, int code, String message) {
        if (callback != null) {
            JSONObject result = new JSONObject();
            result.put("code", code);
            result.put("message", message);
            callback.invoke(result);
        }
    }
}
