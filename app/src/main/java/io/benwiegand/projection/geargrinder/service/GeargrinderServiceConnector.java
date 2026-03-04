package io.benwiegand.projection.geargrinder.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.benwiegand.projection.geargrinder.AccessibilityInputService;
import io.benwiegand.projection.geargrinder.ConnectionService;
import io.benwiegand.projection.geargrinder.IShizukuUserService;
import io.benwiegand.projection.geargrinder.PackageService;
import io.benwiegand.projection.geargrinder.PrivdService;
import io.benwiegand.projection.geargrinder.ProjectionActivity;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftServiceConnection;
import io.benwiegand.projection.geargrinder.privileged.ShizukuUserService;
import rikka.shizuku.Shizuku;

public class GeargrinderServiceConnector extends MakeshiftServiceConnection {
    private static final String PACKAGE_NAME = "io.benwiegand.projection.geargrinder";

    private static final ComponentName ACCESSIBILITY_SERVICE_COMPONENT = new ComponentName(PACKAGE_NAME, AccessibilityInputService.class.getName());
    private static final ComponentName PROJECTION_ACTIVITY_COMPONENT = new ComponentName(PACKAGE_NAME, ProjectionActivity.class.getName());
    private static final ComponentName PRIVD_SERVICE_COMPONENT = new ComponentName(PACKAGE_NAME, PrivdService.class.getName());
    private static final ComponentName PACKAGE_SERVICE_COMPONENT = new ComponentName(PACKAGE_NAME, PackageService.class.getName());
    private static final ComponentName CONNECTION_SERVICE_COMPONENT = new ComponentName(PACKAGE_NAME, ConnectionService.class.getName());
    private static final ComponentName SHIZUKU_USER_SERVICE_COMPONENT = new ComponentName(PACKAGE_NAME, ShizukuUserService.class.getName());

    private static final Shizuku.UserServiceArgs SHIZUKU_ARGS = new Shizuku.UserServiceArgs(SHIZUKU_USER_SERVICE_COMPONENT)
            .tag("geargrinder-shizuku-service")
            .processNameSuffix("shizuku-service")
            .daemon(false);

    private final Map<ComponentName, IBinder> binderMap = new HashMap<>();
    private final String tag;
    private final Context context;
    private final ConnectionListener listener;

    private boolean shizukuBound = false;
    private boolean contextBound = false;

    public interface ConnectionListener {
        default void onAccessibilityServiceConnected(AccessibilityInputService.ServiceBinder binder) {}
        default void onProjectionActivityConnected(ProjectionActivity.ActivityBinder binder) {}
        default void onPrivdServiceConnected(PrivdService.ServiceBinder binder) {}
        default void onPackageServiceConnected(PackageService.ServiceBinder binder) {}
        default void onConnectionServiceConnected(ConnectionService.ServiceBinder binder) {}
        default void onShizukuUserServiceConnected(IShizukuUserService service) {}
    }

    public GeargrinderServiceConnector(String tag, Context context, ConnectionListener listener) {
        this.tag = tag + "-connector";
        this.context = context;
        this.listener = listener;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (contextBound) context.unbindService(this);
        if (shizukuBound && Shizuku.getBinder() != null)
            Shizuku.unbindUserService(SHIZUKU_ARGS, this, true);
    }

    private void makeshiftBind(ComponentName component) {
        bindService(context, component, this);
    }

    private void realBind(ComponentName component, int flags) {
        context.bindService(new Intent().setComponent(component), this, flags);
        contextBound = true;
    }

    private Optional<IBinder> getBinder(ComponentName componentName) {
        return Optional.ofNullable(binderMap.get(componentName));
    }


    public void bindAccessibilityService() {
        makeshiftBind(ACCESSIBILITY_SERVICE_COMPONENT);
    }

    public Optional<AccessibilityInputService.ServiceBinder> getAccessibilityBinder() {
        return getBinder(ACCESSIBILITY_SERVICE_COMPONENT)
                .map(b -> (AccessibilityInputService.ServiceBinder) b);
    }

    public void bindProjectionActivity() {
        makeshiftBind(PROJECTION_ACTIVITY_COMPONENT);
    }

    public Optional<ProjectionActivity.ActivityBinder> getProjectionBinder() {
        return getBinder(PROJECTION_ACTIVITY_COMPONENT)
                .map(b -> (ProjectionActivity.ActivityBinder) b);
    }

    public void bindPrivdService(int flags) {
        realBind(PRIVD_SERVICE_COMPONENT, flags);
    }

    public Optional<PrivdService.ServiceBinder> getPrivdBinder() {
        return getBinder(PRIVD_SERVICE_COMPONENT)
                .map(b -> (PrivdService.ServiceBinder) b);
    }

    public void bindPackageService(int flags) {
        realBind(PACKAGE_SERVICE_COMPONENT, flags);
    }

    public Optional<PackageService.ServiceBinder> getPackageBinder() {
        return getBinder(PACKAGE_SERVICE_COMPONENT)
                .map(b -> (PackageService.ServiceBinder) b);
    }

    public void bindConnectionService(int flags) {
        realBind(CONNECTION_SERVICE_COMPONENT, flags);
    }

    public Optional<ConnectionService.ServiceBinder> getConnectionBinder() {
        return getBinder(CONNECTION_SERVICE_COMPONENT)
                .map(b -> (ConnectionService.ServiceBinder) b);
    }

    public void bindShizukuUserService() {
        Shizuku.bindUserService(SHIZUKU_ARGS, this);
        shizukuBound = true;
    }

    public Optional<IShizukuUserService> getShizukuUserService() {
        return getBinder(SHIZUKU_USER_SERVICE_COMPONENT)
                .map(IShizukuUserService.Stub::asInterface);
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i(tag, "service connected: " + name.getShortClassName());
        binderMap.put(name, service);
        if (name.equals(ACCESSIBILITY_SERVICE_COMPONENT)) {
            listener.onAccessibilityServiceConnected((AccessibilityInputService.ServiceBinder) service);
        } else if (name.equals(PROJECTION_ACTIVITY_COMPONENT)) {
            listener.onProjectionActivityConnected((ProjectionActivity.ActivityBinder) service);
        } else if (name.equals(PRIVD_SERVICE_COMPONENT)) {
            listener.onPrivdServiceConnected((PrivdService.ServiceBinder) service);
        } else if (name.equals(PACKAGE_SERVICE_COMPONENT)) {
            listener.onPackageServiceConnected((PackageService.ServiceBinder) service);
        } else if (name.equals(CONNECTION_SERVICE_COMPONENT)) {
            listener.onConnectionServiceConnected((ConnectionService.ServiceBinder) service);
        } else if (name.equals(SHIZUKU_USER_SERVICE_COMPONENT)) {
            listener.onShizukuUserServiceConnected(IShizukuUserService.Stub.asInterface(service));
        } else {
            Log.wtf(tag, "unhandled component: " + name);
            assert false;
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.i(tag, "service disconnected: " + name.getShortClassName());
        binderMap.remove(name);
    }

}
