package io.benwiegand.projection.geargrinder.util;

import android.content.Context;
import android.content.res.Configuration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;

public class LocaleUtil {

    /**
     * Gets an english context that can be used to resolve english string resources regardless of device language.
     * This should only be used for log messages, where they will be read by a developer (probably me) and not the user.
     */
    public static Context getDeveloperContext(Context appContext) {
        Configuration enConfig = new Configuration(appContext.getResources().getConfiguration());
        enConfig.setLocale(Locale.ENGLISH);
        return appContext
                .createConfigurationContext(enConfig);
    }

    public static ZonedDateTime getLocalizedTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public static ZonedDateTime getLocalizedTime(Date date) {
        return getLocalizedTime(date.toInstant());
    }

    public static String getLocalizedDateString(ZonedDateTime zdt, FormatStyle style) {
        DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDate(style);
        return zdt.format(dtf);
    }

    public static String getLocalizedDateString(Instant instant, FormatStyle style) {
        return getLocalizedDateString(getLocalizedTime(instant), style);
    }

    public static String getLocalizedDateString(Date date, FormatStyle style) {
        return getLocalizedDateString(getLocalizedTime(date), style);
    }

}
