package uni.dcloud.io.uniplugin_webview;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.WebChromeClient.FileChooserParams;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * 无 UI 的代理 Fragment，专门用于处理 WebView 文件选择的 startActivityForResult 回调。
 * 以解耦对宿主 Activity 的直接依赖。
 *
 * 方案要点：
 * 1. 普通 <input type="file">：通过系统 SAF（Intent.ACTION_GET_CONTENT）拉起文件选择器，
 *    content:// Uri 由系统授予临时 URI 权限。
 * 2. 带 capture 的 <input type="file" capture>：先申请相机权限，再用系统相机 App 拍照/录像，
 *    结果写入缓存目录并通过 FileProvider 以 content:// Uri 回传；相机不可用时回退到文件选择器。
 *
 * 虽然 SAF 技术上不需要存储权限，但为了用户体验透明，
 * 会在打开文件选择器前先检查并请求存储权限（如有需要）。
 */
public class FileChooserFragment extends Fragment {

    public interface FileChooserResult {
        void onResult(Uri[] uris);
    }

    private static final int REQUEST_CODE_FILE_CHOOSER = 10001;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 10002;
    private static final int REQUEST_CODE_CAMERA = 10003;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 10004;
    private static final String TAG = "FileChooserFragment";

    private static final String CAMERA_DIR_NAME = "camera";
    private static final String FILE_PROVIDER_AUTHORITY_SUFFIX = ".uniwebview.fileprovider";

    private FileChooserResult mCallback;

    // 待启动的文件选择器 Intent；拍照权限被拒或相机不可用时回退使用
    private Intent mPendingIntent;

    private boolean mCaptureVideo;
    private File mCameraOutputFile;
    private Uri mCameraOutputUri;

    // 无参构造函数必须保留
    public FileChooserFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true); // 保留实例，防止屏幕旋转等销毁回调
    }

    /**
     * 启动文件选择流程
     * H5 带 capture 属性时走相机，否则走文件选择器；两者都会先做权限检查
     * @param fileChooserParams WebView 的文件选择参数，用于判断 capture 和 accept 类型，可为 null
     */
    public void start(Intent intent, FileChooserParams fileChooserParams, FileChooserResult callback) {
        mCallback = callback;
        mPendingIntent = intent;

        boolean useCamera = fileChooserParams != null && fileChooserParams.isCaptureEnabled();
        mCaptureVideo = useCamera
            && !acceptsType(fileChooserParams, "image/")
            && acceptsType(fileChooserParams, "video/");

        if (useCamera) {
            if (checkAndRequestCameraPermission()) {
                openCamera();
            }
        } else if (checkAndRequestStoragePermission()) {
            // 已有权限或无需权限，直接打开文件选择器
            openFileChooser();
        }
        // 否则等待权限回调结果
    }

    private static boolean acceptsType(FileChooserParams params, String prefix) {
        for (String type : params.getAcceptTypes()) {
            if (type != null && type.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查并请求相机权限
     * @return true 表示已有权限（或无需权限），可以继续；false 表示正在请求权限
     */
    private boolean checkAndRequestCameraPermission() {
        Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Activity is null, cannot check camera permission");
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        // 宿主 App 在 Manifest 中声明了 CAMERA，系统要求必须已授权才能调起相机 Intent
        Log.d(TAG, "Requesting camera permission");
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        return false;
    }

    /**
     * 检查并请求存储权限
     * @return true 表示已有权限（或无需权限），可以继续；false 表示正在请求权限
     */
    private boolean checkAndRequestStoragePermission() {
        Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Activity is null, cannot check permissions");
            return true;
        }

        // Android 13+ (API 33+) 使用 READ_MEDIA_IMAGES，旧版本使用 READ_EXTERNAL_STORAGE
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 不再需要 READ_EXTERNAL_STORAGE 即可使用 SAF
            // 但为兼容性和用户感知，可以请求相册权限
            permission = android.Manifest.permission.READ_MEDIA_IMAGES;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permission = android.Manifest.permission.READ_EXTERNAL_STORAGE;
        } else {
            // Android 6.0 以下无需运行时权限
            return true;
        }

        // 检查权限
        if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Storage permission already granted");
            return true;
        }

        // 检查是否需要显示权限说明（用户之前拒绝过）
        boolean shouldShowRationale = shouldShowRequestPermissionRationale(permission);

        if (shouldShowRationale) {
            // 用户之前拒绝过，显示说明
            Toast.makeText(activity, "需要存储权限才能选择文件", Toast.LENGTH_LONG).show();
        }

        Log.d(TAG, "Requesting storage permission: " + permission);
        requestPermissions(new String[]{permission}, REQUEST_CODE_STORAGE_PERMISSION);
        return false;
    }

    /**
     * 拉起系统相机拍照/录像，输出到缓存目录
     */
    private void openCamera() {
        Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Activity is null, cannot open camera");
            fallbackToFileChooser(null);
            return;
        }

        File dir = new File(activity.getCacheDir(), CAMERA_DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to create camera output dir: " + dir);
            fallbackToFileChooser("无法创建拍照文件，已切换为文件选择");
            return;
        }
        File outputFile = new File(dir, "capture_" + System.currentTimeMillis() + (mCaptureVideo ? ".mp4" : ".jpg"));
        Uri outputUri = FileProvider.getUriForFile(activity, activity.getPackageName() + FILE_PROVIDER_AUTHORITY_SUFFIX, outputFile);

        Intent intent = new Intent(mCaptureVideo ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CODE_CAMERA);
            mCameraOutputFile = outputFile;
            mCameraOutputUri = outputUri;
        } catch (Exception e) {
            // 设备没有相机 App（ActivityNotFoundException）等
            Log.e(TAG, "Failed to open camera", e);
            fallbackToFileChooser("无法打开相机，已切换为文件选择");
        }
    }

    /**
     * 相机不可用时回退到文件选择器
     */
    private void fallbackToFileChooser(String toastText) {
        if (toastText != null && getActivity() != null) {
            Toast.makeText(getActivity(), toastText, Toast.LENGTH_LONG).show();
        }
        openFileChooser();
    }

    /**
     * 直接打开文件选择器
     */
    private void openFileChooser() {
        if (mPendingIntent == null) {
            Log.e(TAG, "Pending intent is null");
            notifyResult(null);
            return;
        }

        try {
            startActivityForResult(mPendingIntent, REQUEST_CODE_FILE_CHOOSER);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start file chooser", e);
            notifyResult(null);
        }
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Storage permission granted");
                Toast.makeText(getActivity(), "权限已授予，正在打开文件选择器", Toast.LENGTH_SHORT).show();
                openFileChooser();
            } else {
                Log.d(TAG, "Storage permission denied");
                Toast.makeText(getActivity(), "未授予存储权限，文件选择功能可能受限", Toast.LENGTH_LONG).show();
                // 即使权限被拒绝，SAF 仍然可以工作，直接打开文件选择器
                openFileChooser();
            }
        } else if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Camera permission " + (granted ? "granted" : "denied"));
            if (granted) {
                openCamera();
            } else {
                fallbackToFileChooser("未授予相机权限，已切换为文件选择");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FILE_CHOOSER) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            notifyResult(results);
        } else if (requestCode == REQUEST_CODE_CAMERA) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                // 个别相机 App 会在 data 里直接返回 Uri；否则读取 EXTRA_OUTPUT 指向的文件
                Uri uri = data != null ? data.getData() : null;
                if (uri == null && mCameraOutputFile != null && mCameraOutputFile.length() > 0) {
                    uri = mCameraOutputUri;
                }
                if (uri != null) {
                    results = new Uri[]{uri};
                }
            }
            if (results == null && mCameraOutputFile != null) {
                // 取消或拍摄失败，清理空的输出文件
                mCameraOutputFile.delete();
            }
            mCameraOutputFile = null;
            mCameraOutputUri = null;
            notifyResult(results);
        }
    }

    /**
     * 通知结果并清理
     */
    private void notifyResult(Uri[] results) {
        if (mCallback != null) {
            mCallback.onResult(results);
            mCallback = null;
        }
        removeSelf();
    }

    private void removeSelf() {
        FragmentManager fm = getFragmentManager();
        if (fm != null) {
            fm.beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
}
