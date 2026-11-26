package uni.dcloud.io.uniplugin_webview;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 下载工具类：为 WebView 提供 H5 文件下载落盘能力
 *
 * 两种入口：
 * 1. 系统下载：通过 DownloadManager 下载 http/https 附件
 *    - 来源一：WebView DownloadListener（服务器返回 Content-Disposition: attachment 等）
 *    - 来源二：H5 通过 JsBridge 发送 {type:'download', data:{url, filename}}
 * 2. blob 落盘：H5 已生成的 blob 转 base64 后由原生解码写文件
 *    - 来源：H5 通过 JsBridge 发送 {type:'blobDownload', data:{filename, base64, mime}}
 *
 * 保存目录：
 * - DownloadManager 通道：/storage/emulated/0/Download/（系统公共下载目录）
 *   由系统下载服务代为写入，所有 Android 版本均无需存储权限，文件管理器可直接查看；
 *   重名文件自动追加 (1)、(2)... 序号（与浏览器行为一致）
 * - blob 通道：Android 10+ 写公共 Download 目录（MediaStore，无需权限）；
 *   Android 9 及以下写 App 私有 Download 目录（写公共目录需 WRITE_EXTERNAL_STORAGE）
 */
public final class DownloadHelper {

    private static final String TAG = "DownloadHelper";

    /** 下载结果回调 */
    public interface DownloadCallback {
        void onResult(boolean success, String savedPath, String message);
    }

    private DownloadHelper() {
    }

    /**
     * 用系统 DownloadManager 下载 http/https 文件
     *
     * @param context            任意 Context（内部转 ApplicationContext 注册广播）
     * @param url                完整的 http/https 下载地址
     * @param filename           保存的文件名
     * @param mimetype           MIME 类型（可为空）
     * @param userAgent          请求 User-Agent（可为空，用于保持与 WebView 一致的会话）
     * @param cb                 完成回调（在主线程广播回调中触发）
     */
    public static void downloadWithManager(Context context, String url, String filename,
                                           String mimetype, String userAgent, DownloadCallback cb) {
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;

            // 系统公共下载目录 /storage/emulated/0/Download/（文件管理器可见）
            // DownloadManager 由系统服务代为写入，任何版本均无需存储权限
            File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!publicDir.exists()) {
                publicDir.mkdirs();
            }
            final String uniqueName = dedupeFileName(publicDir, filename);
            final File expected = new File(publicDir, uniqueName);

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(uniqueName);
            req.setDescription("UniWebView 下载");
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uniqueName);
            if (!TextUtils.isEmpty(mimetype)) {
                req.setMimeType(mimetype);
            }
            if (!TextUtils.isEmpty(userAgent)) {
                req.addRequestHeader("User-Agent", userAgent);
            }
            // API 33+ 无通知权限时系统会静默丢弃通知，不会崩溃；主要反馈通道是 ondownloadcomplete 事件
            try {
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
            } catch (Exception ignore) {
            }

            DownloadManager dm = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
            final long id = dm.enqueue(req);
            final String urlFinal = url;
            Log.d(TAG, "已加入系统下载队列: " + url + " → " + expected.getAbsolutePath());

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long doneId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (doneId != id) {
                        return; // 其他下载任务的完成广播
                    }
                    try {
                        appContext.unregisterReceiver(this);
                    } catch (Exception ignore) {
                    }
                    queryAndCallback(dm, id, expected, urlFinal, cb);
                }
            };
            // ACTION_DOWNLOAD_COMPLETE 是受保护的系统广播，无需 RECEIVER_EXPORTED 标记
            appContext.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } catch (Exception e) {
            Log.e(TAG, "发起下载失败: " + e.getMessage(), e);
            if (cb != null) {
                cb.onResult(false, null, "发起下载失败: " + e.getMessage());
            }
        }
    }

    /**
     * 将 base64（或 dataURL）数据解码后保存
     * Android 10+：通过 MediaStore 写入系统公共 Download 目录（无需权限，文件管理器可见）
     * Android 9- ：写 App 私有 Download 目录（公共目录需 WRITE_EXTERNAL_STORAGE 权限，插件不申请）
     *
     * @param base64   纯 base64 字符串，或 data:xxx;base64,xxxx 格式
     * @param filename 保存的文件名
     */
    public static void saveBase64(Context context, String base64, String filename, DownloadCallback cb) {
        try {
            String data = base64;
            int comma = data != null ? data.indexOf("base64,") : -1;
            if (data != null && data.startsWith("data:") && comma >= 0) {
                data = data.substring(comma + "base64,".length());
            }
            if (TextUtils.isEmpty(data)) {
                if (cb != null) cb.onResult(false, null, "base64 数据为空");
                return;
            }
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);

            String safeName = sanitizeFileName(filename);
            if (TextUtils.isEmpty(safeName)) {
                safeName = "download_" + System.currentTimeMillis();
            }

            if (Build.VERSION.SDK_INT >= 29) {
                saveToPublicDownloadsViaMediaStore(context, bytes, safeName, cb);
                return;
            }

            // Android 9 及以下：回落到 App 私有 Download 目录
            File dir = getDownloadDir(context);
            File file = new File(dir, dedupeFileName(dir, safeName));
            OutputStream os = null;
            try {
                os = new FileOutputStream(file);
                os.write(bytes);
                os.flush();
                Log.d(TAG, "blob 已保存(私有目录): " + file.getAbsolutePath() + " (" + bytes.length + " bytes)");
                if (cb != null) cb.onResult(true, file.getAbsolutePath(), "保存成功");
            } finally {
                try {
                    if (os != null) os.close();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "blob 保存失败: " + e.getMessage(), e);
            if (cb != null) cb.onResult(false, null, "保存失败: " + e.getMessage());
        }
    }

    /**
     * Android 10+：通过 MediaStore.Downloads 把数据写入系统公共 Download 目录
     * 应用无需任何存储权限即可向 Downloads 集合贡献自己的文件
     */
    private static void saveToPublicDownloadsViaMediaStore(Context context, byte[] bytes,
                                                           String displayName, DownloadCallback cb) {
        OutputStream os = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
            values.put(MediaStore.Downloads.MIME_TYPE, guessMimeType(displayName));
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("MediaStore insert 返回 null");
            }
            os = resolver.openOutputStream(uri);
            os.write(bytes);
            os.flush();
            os.close();
            os = null;

            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, done, null, null);

            String displayPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    + File.separator + displayName;
            Log.d(TAG, "blob 已保存(公共下载): " + displayPath + " (" + bytes.length + " bytes)");
            if (cb != null) cb.onResult(true, displayPath, "保存成功");
        } catch (Exception e) {
            Log.e(TAG, "MediaStore 保存失败: " + e.getMessage(), e);
            if (cb != null) cb.onResult(false, null, "保存失败: " + e.getMessage());
        } finally {
            try {
                if (os != null) os.close();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * 重名文件自动追加序号：账单.html → 账单(1).html → 账单(2).html（与浏览器下载行为一致）
     */
    public static String dedupeFileName(File dir, String filename) {
        String safe = sanitizeFileName(filename);
        if (TextUtils.isEmpty(safe)) {
            safe = "download_" + System.currentTimeMillis();
        }
        File f = new File(dir, safe);
        if (!f.exists()) {
            return safe;
        }
        String base = safe;
        String ext = "";
        int dot = safe.lastIndexOf('.');
        if (dot > 0) {
            base = safe.substring(0, dot);
            ext = safe.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            String candidate = base + "(" + i + ")" + ext;
            if (!new File(dir, candidate).exists()) {
                return candidate;
            }
        }
        return base + "_" + System.currentTimeMillis() + ext;
    }

    /** 按扩展名推断 MIME 类型（MediaStore 索引用） */
    private static String guessMimeType(String filename) {
        int dot = filename != null ? filename.lastIndexOf('.') : -1;
        if (dot >= 0 && dot < filename.length() - 1) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    filename.substring(dot + 1).toLowerCase());
            if (!TextUtils.isEmpty(mime)) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    /**
     * 从 Content-Disposition / URL / MIME 推导保存文件名
     */
    public static String deriveFilename(String url, String contentDisposition, String mimetype) {
        String name = null;
        if (!TextUtils.isEmpty(contentDisposition)) {
            try {
                Matcher m = Pattern.compile(
                        "filename\\s*=\\s*(\"([^\"]+)\"|([^;\\r\\n]+))", Pattern.CASE_INSENSITIVE)
                        .matcher(contentDisposition);
                if (m.find()) {
                    name = m.group(2) != null ? m.group(2) : m.group(3);
                    if (name != null) name = name.trim();
                }
            } catch (Exception ignore) {
            }
        }
        if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(url)) {
            try {
                String clean = url.split("[?#]")[0];
                int slash = clean.lastIndexOf('/');
                name = slash >= 0 ? clean.substring(slash + 1) : clean;
                name = URLDecoder.decode(name, "UTF-8");
            } catch (Exception ignore) {
            }
        }
        name = sanitizeFileName(name);
        if (TextUtils.isEmpty(name)) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);
            name = "download_" + System.currentTimeMillis() + (TextUtils.isEmpty(ext) ? "" : "." + ext);
        } else if (name.indexOf('.') < 0 && !TextUtils.isEmpty(mimetype)) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);
            if (!TextUtils.isEmpty(ext)) {
                name = name + "." + ext;
            }
        }
        return name;
    }

    /**
     * 文件名安全过滤：去路径分隔符与非法字符，防隐藏文件，限制长度（保留尾部扩展名）
     */
    public static String sanitizeFileName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        if (cleaned.startsWith(".")) {
            cleaned = "_" + cleaned;
        }
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(cleaned.length() - 100);
        }
        return cleaned;
    }

    /** 获取下载保存目录（外部私有 Download 目录，无需权限） */
    public static File getDownloadDir(Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = new File(context.getFilesDir(), Environment.DIRECTORY_DOWNLOADS);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** 查询下载任务最终状态并回调 */
    private static void queryAndCallback(DownloadManager dm, long id, File expected,
                                         String url, DownloadCallback cb) {
        Cursor c = null;
        try {
            DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
            c = dm.query(q);
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    String path = expected != null && expected.exists()
                            ? expected.getAbsolutePath() : (url != null ? url : "");
                    Log.d(TAG, "下载完成: " + path);
                    if (cb != null) cb.onResult(true, path, "下载完成");
                } else {
                    int reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    Log.w(TAG, "下载失败: status=" + status + ", reason=" + reason);
                    if (cb != null) cb.onResult(false, null, "下载失败: status=" + status + ", reason=" + reason);
                }
                return;
            }
            if (cb != null) cb.onResult(false, null, "下载任务查询不到（可能已被系统清除）");
        } catch (Exception e) {
            Log.e(TAG, "查询下载状态失败: " + e.getMessage(), e);
            if (cb != null) cb.onResult(false, null, "查询下载状态失败: " + e.getMessage());
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
