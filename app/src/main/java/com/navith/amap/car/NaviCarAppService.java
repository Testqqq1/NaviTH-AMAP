package com.navith.amap.car;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.HostValidator;
import androidx.car.app.Screen;
import androidx.car.app.Session;

/** Android Auto projected (NOT Android Automotive OS standalone). */
public final class NaviCarAppService extends CarAppService {
    @NonNull @Override public HostValidator createHostValidator() {
        if (getApplicationInfo().flags != 0 && (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR; // Debug head-unit testing only.
        }
        return new HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build();
    }
    @NonNull @Override public Session onCreateSession() { return new NaviSession(); }
    private static final class NaviSession extends Session {
        @NonNull @Override public Screen onCreateScreen(@NonNull Intent intent) {
            return new NaviCarScreen(getCarContext());
        }
    }
}
