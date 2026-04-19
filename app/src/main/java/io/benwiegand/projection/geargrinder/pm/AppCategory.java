package io.benwiegand.projection.geargrinder.pm;

import static io.benwiegand.projection.geargrinder.pm.CarManifestConstants.CAR_HARDWARE_FEATURE_NAME;
import static io.benwiegand.projection.geargrinder.pm.CarManifestConstants.INTENT_ACTION_CAR_SERVICE;
import static io.benwiegand.projection.geargrinder.pm.CarManifestConstants.INTENT_CATEGORY_CAR_LAUNCHER;
import static io.benwiegand.projection.geargrinder.pm.CarManifestConstants.INTENT_CATEGORY_CAR_MEDIA;
import static io.benwiegand.projection.geargrinder.pm.CarManifestConstants.INTENT_CATEGORY_CAR_NAVIGATION;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.benwiegand.projection.geargrinder.R;

public enum AppCategory {
    /**
     * apps from navigation and music, plus apps which advertise car features beyond just notifications.
     */
    FOCUSED,

    /**
     * includes apps which advertise navigation capability and apps which play media audio of some sort
     */
    NAVIGATION_AND_AUDIO,

    /**
     * apps which advertise AA/Automotive support
     */
    CAR_APP;
    
    @StringRes
    public int getLabel() {
        return switch (this) {
            case FOCUSED -> R.string.category_focused_apps;
            case NAVIGATION_AND_AUDIO -> R.string.category_navigation_and_audio;
            case CAR_APP -> R.string.category_car_apps;
        };
    }

    public static Set<AppCategory> getCategoriesForPackage(PackageManager pm, PackageInfo pkg, Set<CarFeature> carFeatures) {
        Set<AppCategory> categories = new HashSet<>();
        String packageName = pkg.packageName;

        Supplier<Intent> baseIntent = () -> new Intent().setPackage(packageName);

        Stream<Pair<AppCategory, Intent>> activityIntentLookups = Stream.of(
                Pair.create(NAVIGATION_AND_AUDIO, baseIntent.get()
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_APP_MUSIC)),
                Pair.create(NAVIGATION_AND_AUDIO, baseIntent.get()
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_APP_MAPS)),
                Pair.create(NAVIGATION_AND_AUDIO, baseIntent.get()
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("google.navigation:"))),
                Pair.create(CAR_APP, baseIntent.get()
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(INTENT_CATEGORY_CAR_LAUNCHER))
        );

        Stream<Pair<AppCategory, Intent>> serviceIntentLookups = Stream.of(
                Pair.create(NAVIGATION_AND_AUDIO, baseIntent.get()
                        .setAction(INTENT_ACTION_CAR_SERVICE)
                        .addCategory(INTENT_CATEGORY_CAR_MEDIA)),
                Pair.create(NAVIGATION_AND_AUDIO, baseIntent.get()
                        .setAction(INTENT_ACTION_CAR_SERVICE)
                        .addCategory(INTENT_CATEGORY_CAR_NAVIGATION)),
                Pair.create(CAR_APP, baseIntent.get()
                        .setAction(INTENT_ACTION_CAR_SERVICE))
        );

        Stream.concat(
                activityIntentLookups.filter(lookup -> !pm.queryIntentActivities(lookup.second, PackageManager.MATCH_ALL).isEmpty()),
                serviceIntentLookups.filter(lookup -> !pm.queryIntentServices(lookup.second, PackageManager.MATCH_ALL).isEmpty()))
                .map(lookup -> lookup.first)
                .forEach(categories::add);


        // additional car app detection
        if (!categories.contains(CAR_APP)) {
            boolean carFeature = pkg.reqFeatures != null
                    && Arrays.stream(pkg.reqFeatures)
                    .anyMatch(feat -> CAR_HARDWARE_FEATURE_NAME.equals(feat.name));
            boolean carMeta = !carFeatures.isEmpty();

            if (carFeature || carMeta) categories.add(CAR_APP);
        }

        // only the media category has a matching car feature
        if (carFeatures.contains(CarFeature.MEDIA)) categories.add(NAVIGATION_AND_AUDIO);

        // "focused"
        if (categories.contains(NAVIGATION_AND_AUDIO)
                || (!carFeatures.isEmpty() && (!carFeatures.contains(CarFeature.NOTIFICATION) || carFeatures.size() > 1))) {

            categories.add(FOCUSED);
        }

        return Set.copyOf(categories);
    }

}
