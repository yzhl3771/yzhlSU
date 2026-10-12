package me.yzhl.su.platform;

import static me.yzhl.su.platform.WorkerPreload.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import me.weishu.kernelsu.ui.util.KsuCliKt;

public class StartupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        var action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"me.yzhl.su.magica.LAUNCH".equals(action)) {
            return;
        }
        if (KsuCliKt.rootAvailable()) return;
        try {
            context.startService(new Intent(context, WorkerService.class));
            Log.i(TAG, "WorkerService started from boot action: " + action);
        } catch (Throwable e) {

            Log.e(TAG, "Failed to start WorkerService from boot action: " + action, e);
        }
    }
}
