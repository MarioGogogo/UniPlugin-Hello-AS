package uni.dcloud.io.uniplugin_printer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * PDF 打印适配器
 * 使用系统 PdfRenderer 把 PDF 的每一页渲染成位图，再重组为打印用 PdfDocument 输出。
 * 注意：PdfRenderer 渲染时按页进行，一次只保留一页位图，内存安全。
 */
public class PdfPrintAdapter extends PrintDocumentAdapter {

    private static final float DEFAULT_RENDER_SCALE = 3.0f;
    private static final float MAX_RENDER_SCALE = 4.0f;

    private final String jobName;
    private final File pdfFile;
    private final int totalPages;
    private final float renderScale;

    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;

    public PdfPrintAdapter(String jobName, File pdfFile, int totalPages, float renderScale) {
        this.jobName = jobName;
        this.pdfFile = pdfFile;
        this.totalPages = totalPages;
        this.renderScale = clamp(renderScale > 0 ? renderScale : DEFAULT_RENDER_SCALE);
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        if (totalPages <= 0) {
            callback.onLayoutFailed(null);
            return;
        }
        PrintDocumentInfo info = new PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(totalPages)
                .build();
        callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                        CancellationSignal cancellationSignal, WriteResultCallback callback) {
        OutputStream out = null;
        PdfDocument document = null;
        try {
            openRenderer();
            if (renderer == null) {
                callback.onWriteFailed(null);
                return;
            }

            int start = pages[0].getStart();
            int end = pages[pages.length - 1].getEnd();
            end = Math.min(end, totalPages - 1);

            document = new PdfDocument();
            for (int i = start; i <= end; i++) {
                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    return;
                }
                Bitmap bitmap = renderPage(i);
                if (bitmap == null) {
                    callback.onWriteFailed(null);
                    return;
                }
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        bitmap.getWidth(), bitmap.getHeight(), i).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawBitmap(bitmap, 0, 0, null);
                document.finishPage(page);
                bitmap.recycle();
            }

            out = new FileOutputStream(destination.getFileDescriptor());
            document.writeTo(out);
            out.flush();

            callback.onWriteFinished(new PageRange[]{new PageRange(start, end)});
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
        closeRenderer();
    }

    private Bitmap renderPage(int index) {
        try {
            PdfRenderer.Page page = renderer.openPage(index);
            int width = Math.max(1, Math.round(page.getWidth() * renderScale));
            int height = Math.max(1, Math.round(page.getHeight() * renderScale));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            page.close();
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private void openRenderer() {
        if (renderer != null) {
            return;
        }
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
        } catch (Exception e) {
            renderer = null;
        }
    }

    private void closeRenderer() {
        try {
            if (renderer != null) {
                renderer.close();
            }
        } catch (Exception ignored) {
        }
        renderer = null;
        try {
            if (pfd != null) {
                pfd.close();
            }
        } catch (Exception ignored) {
        }
        pfd = null;
    }

    private float clamp(float scale) {
        return Math.min(Math.max(scale, 0.5f), MAX_RENDER_SCALE);
    }
}
