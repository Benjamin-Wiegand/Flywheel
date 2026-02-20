package io.benwiegand.projection.geargrinder.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.HashMap;

import io.benwiegand.projection.geargrinder.R;

public class AppDock {
    private static final String TAG = AppDock.class.getSimpleName();

    private final Context context;
    private final PackageManager pm;
    private final View rootView;
    private final LinearLayout itemsView;

    private final AppDockListener listener;

    private final HashMap<ComponentName, View> dockItems = new HashMap<>();

    public interface AppDockListener {
        void onAppSelected(ComponentName componentName);
        void onAppDrawerSelected();
    }


    public AppDock(View rootView, AppDockListener listener) {
        this.rootView = rootView;
        this.listener = listener;
        context = rootView.getContext();
        pm = context.getPackageManager();

        itemsView = rootView.findViewById(R.id.dock_items);

        rootView.findViewById(R.id.app_drawer_button)
                .setOnClickListener(v -> listener.onAppDrawerSelected());
    }

    public View getRootView() {
        return rootView;
    }

    public void addApp(ComponentName componentName) {
        if (dockItems.containsKey(componentName)) return;

        Drawable icon;
        try {
            icon = pm.getActivityIcon(componentName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "failed to look up component: " + componentName, e);
            icon = pm.getDefaultActivityIcon();
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        View dockItemView = inflater.inflate(R.layout.layout_dock_item, itemsView, false);

        ImageView iconView = dockItemView.findViewById(R.id.app_icon);
        iconView.setImageDrawable(icon);

        dockItemView.findViewById(R.id.touch_target)
                .setOnClickListener(v -> listener.onAppSelected(componentName));

        itemsView.addView(dockItemView);
        dockItems.put(componentName, dockItemView);
    }

}
