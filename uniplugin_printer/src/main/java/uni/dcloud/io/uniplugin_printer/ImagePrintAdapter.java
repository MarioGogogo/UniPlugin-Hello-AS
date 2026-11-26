package uni.dcloud.io.uniplugin_printer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.graphics.pdf.PdfDocument;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * 图片打印适配器
 * 把一张位图转换为 PDF 页面输出到打印机：
 *   - 单页模式：整图按 fitMode 绘制到一页
 *   - 分页模式（split=true）：长图按页宽等比缩放后自动拆分为多页（适合长截图、小票）
 */
public class ImagePrintAdapter extends PrintDocumentAdapter {

    /** 适应页面（等比缩放居中，整图可见） */
    public static final String FIT = "FIT";
    /** 填满页面（等比缩放裁剪超出部分） */
    public static final String FILL = "FILL";
    /** 拉伸铺满（不等比，可能变形） */
    public static final String STRETCH = "STRETCH";
    /** 原始尺寸居中（超出部分裁剪） */
    public static final String CENTER = "CENTER";

    private final String jobName;
    private final Bitmap bitmap;
    private final String fitMode;
    private final boolean split;

    private PrintAttributes attributes;
    private int totalPages = 1;

    public ImagePrintAdapter(String jobName, Bitmap bitmap, String fitMode, boolean split) {
        this.jobName = jobName;
        this.bitmap = bitmap;
        this.fitMode = fitMode == null || fitMode.isEmpty() ? FIT : fitMode.toUpperCase(Locale.US);
        this.split = split;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        try {
            this.attributes = newAttributes;

            if (split) {
                int pageWidth = pageWidth();
                int pageHeight = pageHeight();
                if (pageWidth > 0 && bitmap.getWidth() > 0) {
                    float scale = pageWidth * 1f / bitmap.getWidth();
                    float scaledHeight = bitmap.getHeight() * scale;
                    totalPages = Math.max(1, (int) Math.ceil(scaledHeight / pageHeight));
                } else {
                    totalPages = 1;
                }
            } else {
                totalPages = 1;
            }

            PrintDocumentInfo info = new PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(totalPages)
                    .build();
            callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
        } catch (Exception e) {
            callback.onLayoutFailed(null);
        }
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                        CancellationSignal cancellationSignal, WriteResultCallback callback) {
        OutputStream out = null;
        PdfDocument document = null;
        try {
            int pageWidth = pageWidth();
            int pageHeight = pageHeight();

            document = new PdfDocument();
            if (split) {
                drawSplit(document, pageWidth, pageHeight);
            } else {
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 0).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                drawSingle(canvas, pageWidth, pageHeight);
                document.finishPage(page);
            }

            out = new FileOutputStream(destination.getFileDescriptor());
            document.writeTo(out);
            out.flush();

            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
        } catch (Exception e) {
            callback.onWriteFailed(null);
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
            if (document != null) {
                try {
                    document.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void onFinish() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    // ---------------- 绘制逻辑 ----------------

    /**
     * 分页绘制：长图按页宽等比缩放，纵向拆成多页
     */
    private void drawSplit(PdfDocument document, int pageWidth, int pageHeight) {
        float scale = pageWidth * 1f / bitmap.getWidth();
        int totalRows = (int) Math.ceil(bitmap.getHeight() * scale / pageHeight);

        int index = 0;
        for (int row = 0; row < totalRows; row++) {
            int srcTop = Math.round(row * pageHeight / scale);
            int srcBottom = Math.min(bitmap.getHeight(), Math.round((row + 1) * pageHeight / scale));
            if (srcTop >= srcBottom) {
                break;
            }
            Rect src = new Rect(0, srcTop, bitmap.getWidth(), srcBottom);
            float drawHeight = (srcBottom - srcTop) * scale;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index++).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            RectF dst = new RectF(0, 0, pageWidth, drawHeight);
            canvas.drawBitmap(bitmap, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
            document.finishPage(page);
        }
    }

    /**
     * 单页绘制，按 fitMode 控制图片在页面上的显示方式
     */
    private void drawSingle(Canvas canvas, int pageWidth, int pageHeight) {
        switch (fitMode) {
            case FILL:
                drawScaled(canvas, pageWidth, pageHeight,
                        Math.max(pageWidth * 1f / bitmap.getWidth(), pageHeight * 1f / bitmap.getHeight()));
                break;
            case STRETCH:
                canvas.drawBitmap(bitmap, null, new RectF(0, 0, pageWidth, pageHeight),
                        new Paint(Paint.FILTER_BITMAP_FLAG));
                break;
            case CENTER:
                canvas.save();
                canvas.clipRect(0, 0, pageWidth, pageHeight);
                canvas.drawBitmap(bitmap,
                        (pageWidth - bitmap.getWidth()) / 2f,
                        (pageHeight - bitmap.getHeight()) / 2f,
                        new Paint(Paint.FILTER_BITMAP_FLAG));
                canvas.restore();
                break;
            case FIT:
            default:
                drawScaled(canvas, pageWidth, pageHeight,
                        Math.min(pageWidth * 1f / bitmap.getWidth(), pageHeight * 1f / bitmap.getHeight()));
                break;
        }
    }

    private void drawScaled(Canvas canvas, int pageWidth, int pageHeight, float scale) {
        int width = Math.round(bitmap.getWidth() * scale);
        int height = Math.round(bitmap.getHeight() * scale);
        int left = (pageWidth - width) / 2;
        int top = (pageHeight - height) / 2;
        RectF dst = new RectF(left, top, left + width, top + height);
        canvas.drawBitmap(bitmap, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
    }

    private int pageWidth() {
        if (attributes != null && attributes.getMediaSize() != null) {
            return milsToPx(attributes.getMediaSize().getWidthMils(), dpi());
        }
        return 2480; // A4 @300dpi 兜底
    }

    private int pageHeight() {
        if (attributes != null && attributes.getMediaSize() != null) {
            return milsToPx(attributes.getMediaSize().getHeightMils(), dpi());
        }
        return 3508; // A4 @300dpi 兜底
    }

    private int dpi() {
        if (attributes != null && attributes.getResolution() != null) {
            return attributes.getResolution().getHorizontalDpi();
        }
        return 300;
    }

    private int milsToPx(int mils, int dpi) {
        return (int) Math.round(mils / 1000.0 * dpi);
    }
}
