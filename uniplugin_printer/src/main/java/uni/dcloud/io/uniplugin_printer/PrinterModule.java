package uni.dcloud.io.uniplugin_printer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

/**
 * 外部打印机插件（基于 Android Print Framework / PrintManager）
 *
 * 支持：
 *   - 打印图片（本地路径 / file:// / content:// / 网络 URL / base64 / assets）
 *   - 打印 PDF（本地路径 / file:// / content:// / 网络 URL / base64）
 *   - 打印网页（URL 或 HTML 字符串）
 *   - 查询打印服务、查看/取消打印任务
 *
 * 打印时系统会弹出打印对话框，用户可选择本机已安装的任何打印机
 * （WiFi 网络打印机需安装对应打印服务，如 Mopria、HP Print Service 等）。
 */
public class PrinterModule extends UniModule {

    private static final String TAG = "UniPrinterModule";

    /** 网页加载超时默认值（毫秒） */
    private static final int DEFAULT_LOAD_TIMEOUT_MS = 30 * 1000;
    /** 页面加载完成后等待布局的延迟（毫秒） */
    private static final int DEFAULT_LOAD_DELAY_MS = 500;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, PrintJobMonitor> monitors = new HashMap<>();
    private final List<WebView> activeWebViews = new ArrayList<>();

    // ==================== 基础能力 ====================

    /**
     * 检查设备打印支持情况
     * 回调：{ code: 0, supported: Boolean, printServiceCount: Number, message: String }
     */
    @UniJSMethod(uiThread = true)
    public void checkSupport(UniJSCallback callback) {
        JSONObject res = new JSONObject();
        res.put("code", 0);
        res.put("supported", false);
        try {
            PrintManager pm = getPrintManager();
            if (pm == null) {
                res.put("message", "设备不支持打印服务");
            } else {
                int count = findPrintServices().size();
                res.put("supported", true);
                res.put("printServiceCount", count);
                res.put("message", count > 0
                        ? "检测到 " + count + " 个打印服务"
                        : "未检测到打印服务，请先安装打印机驱动（如 Mopria 打印服务）");
            }
        } catch (Exception e) {
            res.put("code", -1);
            res.put("message", "检查失败: " + e.getMessage());
        }
        if (callback != null) {
            callback.invoke(res);
        }
    }

    /**
     * 列出设备上已安装的打印服务（Mopria、HP、佳能、爱普生等）
     * 回调：{ code: 0, services: [{ id, label, enabled }], count: Number, message: String }
     */
    @UniJSMethod(uiThread = true)
    public void getPrintServices(UniJSCallback callback) {
        JSONObject res = new JSONObject();
        res.put("code", 0);
        try {
            Activity activity = getActivity();
            JSONArray arr = new JSONArray();
            if (activity != null) {
                PackageManager packageManager = activity.getPackageManager();
                for (ResolveInfo info : findPrintServices()) {
                    JSONObject o = new JSONObject();
                    if (info.serviceInfo != null) {
                        o.put("id", info.serviceInfo.packageName + "/" + info.serviceInfo.name);
                        o.put("label", info.loadLabel(packageManager).toString());
                        int state = packageManager.getComponentEnabledSetting(
                                new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name));
                        o.put("enabled", state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
                    } else {
                        o.put("id", info.toString());
                        o.put("label", info.toString());
                        o.put("enabled", true);
                    }
                    arr.add(o);
                }
            }
            res.put("services", arr);
            res.put("count", arr.size());
            res.put("message", "查询成功");
        } catch (Exception e) {
            res.put("code", -1);
            res.put("message", "查询失败: " + e.getMessage());
        }
        if (callback != null) {
            callback.invoke(res);
        }
    }

    /**
     * 查看当前所有打印任务
     * 回调：{ code: 0, jobs: [{ id, label, state }], count: Number }
     */
    @UniJSMethod(uiThread = true)
    public void getPrintJobs(UniJSCallback callback) {
        JSONObject res = new JSONObject();
        res.put("code", 0);
        try {
            PrintManager pm = getPrintManager();
            JSONArray arr = new JSONArray();
            if (pm != null) {
                for (PrintJob job : pm.getPrintJobs()) {
                    arr.add(jobToJson(job));
                }
            }
            res.put("jobs", arr);
            res.put("count", arr.size());
        } catch (Exception e) {
            res.put("code", -1);
            res.put("message", "查询失败: " + e.getMessage());
        }
        if (callback != null) {
            callback.invoke(res);
        }
    }

    /**
     * 取消打印任务
     * 参数：{ jobId: String }
     * 回调：{ code: 0, jobId, message: String }
     */
    @UniJSMethod(uiThread = true)
    public void cancelPrintJob(JSONObject params, UniJSCallback callback) {
        String jobId = params != null ? params.getString("jobId") : null;
        if (jobId == null || jobId.isEmpty()) {
            invokeError(callback, -1, "请传入 jobId");
            return;
        }
        try {
            PrintManager pm = getPrintManager();
            boolean found = false;
            if (pm != null) {
                for (PrintJob job : pm.getPrintJobs()) {
                    if (jobId.equals(job.getId().toString())) {
                        job.cancel();
                        found = true;
                        break;
                    }
                }
            }
            JSONObject res = new JSONObject();
            res.put("code", found ? 0 : -1);
            res.put("jobId", jobId);
            res.put("message", found ? "已发送取消请求" : "未找到该打印任务（可能已完成）");
            if (callback != null) {
                callback.invoke(res);
            }
        } catch (Exception e) {
            invokeError(callback, -1, "取消失败: " + e.getMessage());
        }
    }

    // ==================== 打印图片 ====================

    /**
     * 打印图片
     * 参数：
     *   imagePath   String  图片来源：本地绝对路径 / file:// / content:// / http(s):// / data:base64 / assets://
     *   imageBase64 String  纯 base64 图片数据（与 imagePath 二选一）
     *   jobName     String  打印任务名称
     *   pageSize    String  纸张：A3/A4/A5/B5/LETTER/LEGAL/TABLOID/EXECUTIVE/CUSTOM，默认 A4
     *   orientation String  PORTRAIT / LANDSCAPE，默认 PORTRAIT
     *   dpi         Number  分辨率，默认 300
     *   colorMode   String  COLOR / MONOCHROME，默认 COLOR
     *   marginMm    Number  页边距(mm)
     *   fitMode     String  FIT / FILL / STRETCH / CENTER，默认 FIT
     *   split       Boolean 长图自动分页（true 时按页宽缩放后拆成多页），默认 false
     *
     * 回调（多次）：{ code: 0, jobId, jobName, state, isFinal, message }
     *   state: queued → started → blocked → completed / failed / cancelled
     */
    @UniJSMethod(uiThread = true)
    public void printImage(JSONObject params, UniJSCallback callback) {
        final Activity activity = getActivity();
        if (activity == null) {
            invokeError(callback, -1, "无法获取 Activity 上下文");
            return;
        }
        final PrintManager pm = getPrintManager();
        if (pm == null) {
            invokeError(callback, -1, "设备不支持打印服务");
            return;
        }

        final String source = extractSource(params, "imagePath", "imageBase64");
        if (source == null) {
            invokeError(callback, -1, "请传入 imagePath 或 imageBase64");
            return;
        }

        // 图片加载可能涉及网络/解码，放后台线程执行
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bitmap = SourceResolver.decodeImage(source, activity);
                    if (bitmap == null) {
                        invokeErrorOnMain(callback, -1, "图片解码失败，请检查图片格式");
                        return;
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            doPrintImage(pm, params, bitmap, callback);
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "printImage 加载失败", e);
                    invokeErrorOnMain(callback, -1, "图片加载失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void doPrintImage(PrintManager pm, JSONObject params, Bitmap bitmap, UniJSCallback callback) {
        try {
            PrintAttributes attrs = PrintAttributesHelper.parse(params);
            String jobName = PrintAttributesHelper.jobName(params, "图片打印");
            String fitMode = params != null ? params.getString("fitMode") : null;
            boolean split = params != null && params.getBooleanValue("split");

            PrintDocumentAdapter adapter = new ImagePrintAdapter(jobName, bitmap, fitMode, split);
            PrintJob job = pm.print(jobName, adapter, attrs);
            handleSubmittedJob(pm, job, jobName, callback, null);
        } catch (Exception e) {
            Log.e(TAG, "printImage 提交失败", e);
            invokeError(callback, -1, "提交打印失败: " + e.getMessage());
        }
    }

    // ==================== 打印 PDF ====================

    /**
     * 打印 PDF 文件
     * 参数：
     *   filePath    String  PDF 来源：本地绝对路径 / file:// / content:// / http(s):// / data:base64 / assets://
     *   fileBase64  String  纯 base64 PDF 数据（与 filePath 二选一）
     *   jobName / pageSize / orientation / dpi / colorMode / marginMm  同上
     *   renderScale Number  PDF 渲染分辨率倍数（1~4，默认 3，约 216dpi）
     *
     * 回调（多次）：同 printImage
     */
    @UniJSMethod(uiThread = true)
    public void printPdf(JSONObject params, UniJSCallback callback) {
        final Activity activity = getActivity();
        if (activity == null) {
            invokeError(callback, -1, "无法获取 Activity 上下文");
            return;
        }
        final PrintManager pm = getPrintManager();
        if (pm == null) {
            invokeError(callback, -1, "设备不支持打印服务");
            return;
        }

        final String source = extractSource(params, "filePath", "fileBase64");
        if (source == null) {
            invokeError(callback, -1, "请传入 filePath 或 fileBase64");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File pdfFile = SourceResolver.resolveToFile(source, activity);
                    final int pageCount = SourceResolver.countPdfPages(pdfFile);
                    if (pageCount <= 0) {
                        invokeErrorOnMain(callback, -1, "PDF 文件为空或无法解析");
                        return;
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            doPrintPdf(pm, params, pdfFile, pageCount, callback);
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "printPdf 加载失败", e);
                    invokeErrorOnMain(callback, -1, "PDF 加载失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void doPrintPdf(PrintManager pm, JSONObject params, File pdfFile, int pageCount, UniJSCallback callback) {
        try {
            PrintAttributes attrs = PrintAttributesHelper.parse(params);
            String jobName = PrintAttributesHelper.jobName(params, "PDF打印");
            float renderScale = params != null ? params.getFloatValue("renderScale") : 0f;

            PrintDocumentAdapter adapter = new PdfPrintAdapter(jobName, pdfFile, pageCount, renderScale);
            PrintJob job = pm.print(jobName, adapter, attrs);
            handleSubmittedJob(pm, job, jobName, callback, null);
        } catch (Exception e) {
            Log.e(TAG, "printPdf 提交失败", e);
            invokeError(callback, -1, "提交打印失败: " + e.getMessage());
        }
    }

    // ==================== 打印网页 ====================

    /**
     * 打印网页 / HTML 内容
     * 参数：
     *   url           String  网页地址（与 html 二选一）
     *   html          String  HTML 字符串（与 url 二选一）
     *   baseUrl       String  html 模式下相对路径基准地址，默认 file:///android_asset/
     *   jobName / pageSize / orientation / dpi / colorMode / marginMm  同上
     *   loadTimeoutMs Number  页面加载超时（毫秒），默认 30000
     *   loadDelayMs   Number  加载完成后等待布局的延迟（毫秒），默认 500
     *
     * 回调（多次）：同 printImage
     */
    @UniJSMethod(uiThread = true)
    public void printWebPage(JSONObject params, UniJSCallback callback) {
        final Activity activity = getActivity();
        if (activity == null) {
            invokeError(callback, -1, "无法获取 Activity 上下文");
            return;
        }
        final PrintManager pm = getPrintManager();
        if (pm == null) {
            invokeError(callback, -1, "设备不支持打印服务");
            return;
        }

        String url = params != null ? params.getString("url") : null;
        String html = params != null ? params.getString("html") : null;
        if ((url == null || url.isEmpty()) && (html == null || html.isEmpty())) {
            invokeError(callback, -1, "请传入 url 或 html 内容");
            return;
        }

        final String jobName = PrintAttributesHelper.jobName(params,
                url != null && !url.isEmpty() ? "网页打印" : "HTML打印");
        final int timeoutMs = params != null && params.getIntValue("loadTimeoutMs") > 0
                ? params.getIntValue("loadTimeoutMs") : DEFAULT_LOAD_TIMEOUT_MS;
        final int delayMs = params != null && params.getIntValue("loadDelayMs") > 0
                ? params.getIntValue("loadDelayMs") : DEFAULT_LOAD_DELAY_MS;
        final String expectedUrl = url != null && !url.isEmpty()
                ? url
                : (params != null && params.getString("baseUrl") != null ? params.getString("baseUrl") : "file:///android_asset/");

        // 离屏 WebView 渲染页面（不可见，不干扰界面）
        final WebView webView = new WebView(activity);
        activeWebViews.add(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                cleanupWebView(webView);
            }
        };

        final Runnable timeoutTask = new Runnable() {
            @Override
            public void run() {
                if (finished.get()) {
                    return;
                }
                finished.set(true);
                Log.w(TAG, "网页加载超时: " + (url != null ? url : "html"));
                cleanup.run();
                invokeError(callback, -1, "页面加载超时（" + timeoutMs + "ms），请检查网络或网址");
            }
        };

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mainHandler.removeCallbacks(timeoutTask);
                if (finished.get()) {
                    return;
                }
                // 等待页面布局完成后再打印，避免图片/样式未加载完整
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (finished.get()) {
                            return;
                        }
                        doPrintWebPage(pm, webView, jobName, params, callback, finished, cleanup);
                    }
                }, delayMs);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame() && !finished.get()) {
                    finished.set(true);
                    mainHandler.removeCallbacks(timeoutTask);
                    cleanup.run();
                    invokeError(callback, -1, "页面加载失败: "
                            + (error != null ? error.getDescription() : "未知错误"));
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (!finished.get() && failingUrl != null && failingUrl.equals(expectedUrl)) {
                    finished.set(true);
                    mainHandler.removeCallbacks(timeoutTask);
                    cleanup.run();
                    invokeError(callback, -1, "页面加载失败: " + description + " (" + errorCode + ")");
                }
            }
        });

        attachWebView(webView, activity);
        mainHandler.postDelayed(timeoutTask, timeoutMs);

        if (html != null && !html.isEmpty()) {
            String baseUrl = params != null ? params.getString("baseUrl") : null;
            webView.loadDataWithBaseURL(baseUrl != null ? baseUrl : "file:///android_asset/",
                    html, "text/html", "UTF-8", null);
        } else {
            webView.loadUrl(url);
        }
    }

    private void doPrintWebPage(PrintManager pm, WebView webView, String jobName, JSONObject params,
                                UniJSCallback callback, AtomicBoolean finished, Runnable cleanup) {
        try {
            PrintAttributes attrs = PrintAttributesHelper.parse(params);
            PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(jobName);
            PrintJob job = pm.print(jobName, adapter, attrs);
            if (job == null) {
                finished.set(true);
                cleanup.run();
                invokeError(callback, -1, "打印任务创建失败，请检查是否安装了打印服务");
                return;
            }
            handleSubmittedJob(pm, job, jobName, callback, new Runnable() {
                @Override
                public void run() {
                    finished.set(true);
                    cleanup.run();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "printWebPage 提交失败", e);
            finished.set(true);
            cleanup.run();
            invokeError(callback, -1, "提交打印失败: " + e.getMessage());
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 任务提交成功后的统一处理：先回调一次提交结果（keepAlive），
     * 再启动状态监视器持续回调，直到最终状态
     */
    private void handleSubmittedJob(PrintManager pm, PrintJob job, String jobName,
                                    UniJSCallback callback, Runnable onTerminal) {
        if (job == null) {
            invokeError(callback, -1, "打印任务创建失败，请检查是否安装了打印服务");
            return;
        }
        JSONObject res = new JSONObject();
        res.put("code", 0);
        res.put("jobId", job.getId().toString());
        res.put("jobName", jobName);
        res.put("state", "queued");
        res.put("isFinal", false);
        res.put("message", "打印任务已提交，请在系统打印对话框中选择打印机");
        if (callback != null) {
            callback.invokeAndKeepAlive(res);
        }

        PrintJobMonitor monitor = new PrintJobMonitor(pm, job.getId().toString(), jobName, callback, onTerminal);
        monitors.put(job.getId().toString(), monitor);
        monitor.start();
    }

    private String extractSource(JSONObject params, String pathKey, String base64Key) {
        if (params == null) {
            return null;
        }
        String path = params.getString(pathKey);
        if (path != null && !path.isEmpty()) {
            return path;
        }
        String base64 = params.getString(base64Key);
        if (base64 != null && !base64.isEmpty()) {
            // 纯 base64 数据加上前缀，交给 SourceResolver 统一识别
            return base64.startsWith("data:") ? base64 : "data:application/octet-stream;base64," + base64;
        }
        return null;
    }

    private JSONObject jobToJson(PrintJob job) {
        JSONObject o = new JSONObject();
        o.put("id", job.getId().toString());
        PrintJobInfo info = job.getInfo();
        if (info != null) {
            o.put("label", info.getLabel());
            o.put("state", stateOf(info.getState()));
        }
        return o;
    }

    private String stateOf(int state) {
        switch (state) {
            case PrintJobInfo.STATE_STARTED:
                return "started";
            case PrintJobInfo.STATE_BLOCKED:
                return "blocked";
            case PrintJobInfo.STATE_COMPLETED:
                return "completed";
            case PrintJobInfo.STATE_FAILED:
                return "failed";
            case PrintJobInfo.STATE_CANCELED:
                return "cancelled";
            case PrintJobInfo.STATE_QUEUED:
            default:
                return "queued";
        }
    }

    /**
     * 通过 PackageManager 查询已安装的打印服务
     * （DCloud SDK 编译环境中 PrintManager.getPrintServices 不可用，改用 Intent 查询）
     */
    private List<ResolveInfo> findPrintServices() {
        Context ctx = mUniSDKInstance != null ? mUniSDKInstance.getContext() : null;
        if (ctx == null) {
            return new ArrayList<>();
        }
        try {
            Intent intent = new Intent("android.printservice.PrintService");
            return ctx.getPackageManager().queryIntentServices(intent, 0);
        } catch (Exception e) {
            Log.e(TAG, "查询打印服务失败", e);
            return new ArrayList<>();
        }
    }

    private void attachWebView(WebView webView, Activity activity) {
        try {
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dm.widthPixels, dm.heightPixels);
            webView.setVisibility(View.INVISIBLE);
            decor.addView(webView, lp);
        } catch (Exception e) {
            Log.w(TAG, "WebView 附加窗口失败，尝试离屏打印", e);
        }
    }

    private void cleanupWebView(WebView webView) {
        try {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.stopLoading();
            webView.destroy();
        } catch (Exception e) {
            Log.e(TAG, "cleanupWebView 异常", e);
        } finally {
            activeWebViews.remove(webView);
        }
    }

    private Activity getActivity() {
        if (mUniSDKInstance != null && mUniSDKInstance.getContext() instanceof Activity) {
            return (Activity) mUniSDKInstance.getContext();
        }
        return null;
    }

    private PrintManager getPrintManager() {
        Context ctx = mUniSDKInstance != null ? mUniSDKInstance.getContext() : null;
        if (ctx == null) {
            return null;
        }
        return (PrintManager) ctx.getSystemService(Context.PRINT_SERVICE);
    }

    private void invokeError(UniJSCallback callback, int code, String message) {
        if (callback == null) {
            return;
        }
        JSONObject res = new JSONObject();
        res.put("code", code);
        res.put("message", message);
        callback.invoke(res);
    }

    private void invokeErrorOnMain(final UniJSCallback callback, final int code, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                invokeError(callback, code, message);
            }
        });
    }

    /**
     * 模块销毁时清理：停止任务监视器、销毁离屏 WebView
     * （与 BetechNfcModule 一致，为普通方法，供上层生命周期调用）
     */
    public void destroy() {
        try {
            for (PrintJobMonitor monitor : monitors.values()) {
                monitor.stop();
            }
            monitors.clear();
        } catch (Exception e) {
            Log.e(TAG, "销毁监视器异常", e);
        }
        for (WebView webView : new ArrayList<>(activeWebViews)) {
            cleanupWebView(webView);
        }
    }
}
