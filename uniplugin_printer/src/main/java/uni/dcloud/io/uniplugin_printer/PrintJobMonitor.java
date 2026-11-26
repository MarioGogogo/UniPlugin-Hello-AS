package uni.dcloud.io.uniplugin_printer;

import android.os.Handler;
import android.os.Looper;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import io.dcloud.feature.uniapp.bridge.UniJSCallback;

/**
 * 打印任务监视器
 * 系统 PrintJob 没有监听器接口，这里通过定时轮询状态，
 * 把 queued → started → blocked → completed/failed/cancelled 的变化实时回调给 JS。
 *
 * 回调约定：
 *   - 状态变化时调用 invokeAndKeepAlive，JS 回调会持续收到更新
 *   - 到达最终状态（completed/failed/cancelled）时调用 invoke，回调自动注销
 */
public class PrintJobMonitor {

    private static final String TAG = "PrintJobMonitor";
    private static final long POLL_INTERVAL_MS = 500L;

    private final PrintManager printManager;
    private final String jobId;
    private final UniJSCallback callback;
    private final Runnable onTerminal;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            poll();
        }
    };

    private String lastState;
    private boolean finished;

    public PrintJobMonitor(PrintManager printManager, String jobId, String initialJobName,
                           UniJSCallback callback, Runnable onTerminal) {
        this.printManager = printManager;
        this.jobId = jobId;
        this.callback = callback;
        this.onTerminal = onTerminal;
        this.lastState = "queued";
    }

    public void start() {
        handler.postDelayed(pollTask, POLL_INTERVAL_MS);
    }

    public void stop() {
        finished = true;
        handler.removeCallbacks(pollTask);
    }

    private void poll() {
        if (finished) {
            return;
        }
        PrintJob job = findJob();
        if (job == null) {
            // 任务尚未出现在系统列表中（用户还没确认打印对话框），继续等待
            handler.postDelayed(pollTask, POLL_INTERVAL_MS);
            return;
        }

        String state = stateOf(job);
        boolean terminal = PrintJobInfo.STATE_COMPLETED == job.getInfo().getState()
                || PrintJobInfo.STATE_FAILED == job.getInfo().getState()
                || PrintJobInfo.STATE_CANCELED == job.getInfo().getState();

        if (!state.equals(lastState)) {
            lastState = state;
            JSONObject res = new JSONObject();
            res.put("code", 0);
            res.put("jobId", jobId);
            res.put("jobName", job.getInfo() != null ? job.getInfo().getLabel() : "");
            res.put("state", state);
            res.put("isFinal", terminal);
            res.put("message", messageOf(state));

            if (callback != null) {
                if (terminal) {
                    // 最终状态：一次性回调，自动注销
                    callback.invoke(res);
                } else {
                    callback.invokeAndKeepAlive(res);
                }
            }
        }

        if (terminal) {
            finished = true;
            if (onTerminal != null) {
                try {
                    onTerminal.run();
                } catch (Exception e) {
                    Log.e(TAG, "onTerminal 回调异常", e);
                }
            }
            return;
        }
        handler.postDelayed(pollTask, POLL_INTERVAL_MS);
    }

    private PrintJob findJob() {
        try {
            for (PrintJob job : printManager.getPrintJobs()) {
                if (jobId.equals(job.getId())) {
                    return job;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "查询打印任务失败", e);
        }
        return null;
    }

    private String stateOf(PrintJob job) {
        if (job == null || job.getInfo() == null) {
            return "queued";
        }
        switch (job.getInfo().getState()) {
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

    private String messageOf(String state) {
        switch (state) {
            case "queued":
                return "任务已排队，等待系统处理";
            case "started":
                return "正在打印";
            case "blocked":
                return "任务被阻塞，请检查打印机状态（离线/缺纸等）";
            case "completed":
                return "打印完成";
            case "failed":
                return "打印失败，请检查打印机";
            case "cancelled":
                return "打印任务已取消";
            default:
                return state;
        }
    }
}
