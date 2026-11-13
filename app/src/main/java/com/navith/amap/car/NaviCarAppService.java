package com.navith.amap.car;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.ScreenManager;
import androidx.car.app.validation.HostValidator;
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
        @Override public void onNewIntent(@NonNull Intent intent) {
            if (!CarContext.ACTION_NAVIGATE.equals(intent.getAction())) return;
            String query = "";
            Uri data = intent.getData();
            if (data != null) {
                query = data.getQueryParameter("q");
                if (query == null || query.isEmpty()) query = data.getSchemeSpecificPart();
            }
            if (query == null) query = "";
            query = query.replace("geo:", "").trim();
            ScreenManager screens = getCarContext().getCarService(ScreenManager.class);
            if (!(screens.getTop() instanceof NaviCarScreen)) screens.popToRoot();
            Screen top = screens.getTop();
            if (top instanceof NaviCarScreen) {
                screens.push(new NaviSearchScreen(getCarContext(), (NaviCarScreen) top, query));
            }
        }
    }
}
