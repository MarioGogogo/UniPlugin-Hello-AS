package uni.dcloud.io.uniplugin_printer;

import android.print.PrintAttributes;

import com.alibaba.fastjson.JSONObject;

import java.util.Locale;

/**
 * 打印参数解析工具
 * 把 JS 传入的 JSON 参数转换为系统 PrintAttributes（纸张、方向、分辨率、颜色、边距）
 */
public final class PrintAttributesHelper {

    private static final int DEFAULT_DPI = 300;

    private PrintAttributesHelper() {
    }

    /**
     * 解析打印属性
     * 支持的参数：
     *   pageSize      String  纸张：A3/A4/A5/B5/LETTER/LEGAL/TABLOID/EXECUTIVE/CUSTOM，默认 A4
     *   customWidthMm Number  自定义纸张宽度(mm)，pageSize=CUSTOM 时生效
     *   customHeightMm Number 自定义纸张高度(mm)，pageSize=CUSTOM 时生效
     *   orientation   String  PORTRAIT / LANDSCAPE，默认 PORTRAIT
     *   dpi           Number  打印分辨率，默认 300
     *   colorMode     String  COLOR / MONOCHROME，默认 COLOR
     *   marginMm      Number  页边距(mm)，默认 0
     */
    public static PrintAttributes parse(JSONObject params) {
        PrintAttributes.Builder builder = new PrintAttributes.Builder();

        // 纸张尺寸
        String pageSize = params != null ? params.getString("pageSize") : null;
        PrintAttributes.MediaSize mediaSize = mediaSizeOf(pageSize, params);
        String orientation = params != null ? params.getString("orientation") : null;
        if ("LANDSCAPE".equalsIgnoreCase(orientation)) {
            mediaSize = mediaSize.asLandscape();
        }
        builder.setMediaSize(mediaSize);

        // 分辨率
        int dpi = params != null ? params.getIntValue("dpi") : 0;
        if (dpi <= 0) {
            dpi = DEFAULT_DPI;
        }
        builder.setResolution(new PrintAttributes.Resolution("print", "print", dpi, dpi));

        // 颜色模式
        String colorMode = params != null ? params.getString("colorMode") : null;
        if ("MONOCHROME".equalsIgnoreCase(colorMode)) {
            builder.setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME);
        } else {
            builder.setColorMode(PrintAttributes.COLOR_MODE_COLOR);
        }

        // 页边距
        int marginMm = params != null ? params.getIntValue("marginMm") : 0;
        if (marginMm > 0) {
            int marginMils = (int) (marginMm * 39.3701f);
            builder.setMinMargins(new PrintAttributes.Margins(marginMils, marginMils, marginMils, marginMils));
        } else {
            builder.setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0));
        }

        return builder.build();
    }

    /**
     * 打印任务名称，未传时使用默认名称
     */
    public static String jobName(JSONObject params, String defaultName) {
        String name = params != null ? params.getString("jobName") : null;
        return name == null || name.isEmpty() ? defaultName : name;
    }

    private static PrintAttributes.MediaSize mediaSizeOf(String pageSize, JSONObject params) {
        if (pageSize == null || pageSize.isEmpty()) {
            return PrintAttributes.MediaSize.ISO_A4;
        }
        String key = pageSize.trim().toUpperCase(Locale.US).replace("-", "").replace("_", "");
        switch (key) {
            case "A3":
                return PrintAttributes.MediaSize.ISO_A3;
            case "A5":
                return PrintAttributes.MediaSize.ISO_A5;
            case "B5":
                return PrintAttributes.MediaSize.ISO_B5;
            case "LETTER":
                return PrintAttributes.MediaSize.NA_LETTER;
            case "LEGAL":
                return PrintAttributes.MediaSize.NA_LEGAL;
            case "TABLOID":
                return PrintAttributes.MediaSize.NA_TABLOID;
            case "EXECUTIVE":
                return PrintAttributes.MediaSize.JIS_EXEC;
            case "CUSTOM": {
                int wMm = params != null ? params.getIntValue("customWidthMm") : 0;
                int hMm = params != null ? params.getIntValue("customHeightMm") : 0;
                if (wMm > 0 && hMm > 0) {
                    return new PrintAttributes.MediaSize(
                            "custom", "自定义",
                            (int) (wMm * 39.3701f), (int) (hMm * 39.3701f));
                }
                return PrintAttributes.MediaSize.ISO_A4;
            }
            case "A4":
            default:
                return PrintAttributes.MediaSize.ISO_A4;
        }
    }
}
