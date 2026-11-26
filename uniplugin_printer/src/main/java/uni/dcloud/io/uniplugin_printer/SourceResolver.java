package uni.dcloud.io.uniplugin_printer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * 打印数据源解析工具
 * 统一支持以下来源：
 *   1. 本地绝对路径       /storage/emulated/0/xxx.jpg
 *   2. file:// 协议       file:///storage/emulated/0/xxx.jpg
 *   3. content:// 协议    content://media/external/images/media/xxx
 *   4. http(s):// 网络    自动下载到缓存目录
 *   5. base64 数据        data:image/png;base64,xxxx 或纯 base64
 *   6. assets 资源        assets://xxx 或 file:///android_asset/xxx
 */
public final class SourceResolver {

    private static final String TAG = "SourceResolver";

    /** 图片解码后单边最大像素，防止大图 OOM */
    private static final int MAX_IMAGE_DIMENSION = 3000;

    /** 网络下载超时 */
    private static final int NETWORK_TIMEOUT_MS = 30 * 1000;

    private SourceResolver() {
    }

    /**
     * 解码图片（自动处理 EXIF 旋转、大图采样）
     */
    public static Bitmap decodeImage(String source, Context context) throws Exception {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("图片源为空");
        }
        File file = resolveToFile(source.trim(), context);

        // 先读取尺寸边界，避免大图 OOM
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("无法解析图片文件");
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIMENSION);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (bitmap == null) {
            throw new IllegalArgumentException("图片解码失败，请检查图片格式");
        }

        // EXIF 旋转校正（拍照的照片常见）
        try {
            int rotation = exifRotation(file);
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                    bitmap = rotated;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "EXIF 旋转处理失败，忽略", e);
        }
        return bitmap;
    }

    /**
     * 把各种来源统一解析成本地 File（content:// 会复制到缓存目录，网络会自动下载）
     */
    public static File resolveToFile(String source, Context context) throws Exception {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源为空");
        }
        String src = source.trim();
        String scheme = schemeOf(src);
        switch (scheme) {
            case "path": {
                File f = new File(src);
                if (!f.exists()) {
                    throw new IllegalArgumentException("文件不存在: " + src);
                }
                return f;
            }
            case "file": {
                String path = Uri.parse(src).getPath();
                File f = new File(path);
                if (!f.exists()) {
                    throw new IllegalArgumentException("文件不存在: " + src);
                }
                return f;
            }
            case "content": {
                File tmp = File.createTempFile("print_content_", ".tmp", context.getCacheDir());
                try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(src));
                     OutputStream out = new FileOutputStream(tmp)) {
                    if (in == null) {
                        throw new IllegalArgumentException("无法打开 content:// 资源");
                    }
                    copy(in, out);
                }
                return tmp;
            }
            case "http": {
                File tmp = File.createTempFile("print_download_", ".tmp", context.getCacheDir());
                download(src, tmp);
                return tmp;
            }
            case "base64": {
                String base64 = src;
                int comma = src.indexOf(',');
                if (comma >= 0 && src.startsWith("data:")) {
                    base64 = src.substring(comma + 1);
                }
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                File tmp = File.createTempFile("print_base64_", ".tmp", context.getCacheDir());
                try (OutputStream out = new FileOutputStream(tmp)) {
                    out.write(data);
                }
                return tmp;
            }
            case "asset": {
                String path = src;
                if (src.startsWith("file:///android_asset/")) {
                    path = src.substring("file:///android_asset/".length());
                } else if (src.startsWith("assets://")) {
                    path = src.substring("assets://".length());
                }
                File tmp = File.createTempFile("print_asset_", ".tmp", context.getCacheDir());
                try (InputStream in = context.getAssets().open(path);
                     OutputStream out = new FileOutputStream(tmp)) {
                    copy(in, out);
                }
                return tmp;
            }
            default:
                throw new IllegalArgumentException("不支持的资源类型: " + src);
        }
    }

    /**
     * 统计 PDF 页数（同时校验文件可读）
     */
    public static int countPdfPages(File pdfFile) {
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)) {
            PdfRenderer renderer = new PdfRenderer(pfd);
            int count = renderer.getPageCount();
            renderer.close();
            return count;
        } catch (Exception e) {
            Log.e(TAG, "统计 PDF 页数失败", e);
            return -1;
        }
    }

    private static String schemeOf(String source) {
        String s = source.toLowerCase(Locale.US);
        if (s.startsWith("data:")) {
            return "base64";
        }
        if (s.startsWith("content://")) {
            return "content";
        }
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return "http";
        }
        if (s.startsWith("file:///android_asset/") || s.startsWith("assets://")) {
            return "asset";
        }
        if (s.startsWith("file://")) {
            return "file";
        }
        return "path";
    }

    private static void download(String url, File target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("下载失败，HTTP " + code);
            }
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(target)) {
                copy(in, out);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static int calculateSampleSize(int width, int height, int maxDimension) {
        int sample = 1;
        while (width / sample > maxDimension || height / sample > maxDimension) {
            sample *= 2;
        }
        return sample;
    }

    private static int exifRotation(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.flush();
    }
}
