package io.benwiegand.projection.geargrinder.settings.ui.summary;

import static io.benwiegand.projection.geargrinder.util.CryptoUtil.decodeX509CertChain;
import static io.benwiegand.projection.geargrinder.util.LocaleUtil.getLocalizedDateString;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;

import java.security.cert.X509Certificate;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class X509CertificateChainSummaryProvider implements Preference.SummaryProvider<Preference> {

    public record CheckResult(
            boolean passed,
            Function<Context, String> summaryGetter,
            Function<Context, String> detailsGetter
    ) {

        public CheckResult(boolean passed, Function<Context, String> summaryGetter) {
            this(passed, summaryGetter, null);
        }

        public CheckResult(boolean passed, @StringRes int summary, @StringRes int details) {
            this(passed, c -> c.getString(summary), c -> c.getString(details));
        }

        public CheckResult(boolean passed, @StringRes int summary) {
            this(passed, c -> c.getString(summary), null);
        }

        public String summary(Context c) {
            return summaryGetter.apply(c);
        }

        public String details(Context c) {
            return detailsGetter.apply(c);
        }
    }

    public static Function<X509Certificate, CheckResult> CAR_SERVICE_CHECK = cert -> cert.getSubjectDN().getName().contains("O=CarService") ?
            new X509CertificateChainSummaryProvider.CheckResult(
                    true,
                    R.string.certificate_is_from_car_service
            ) : new X509CertificateChainSummaryProvider.CheckResult(
            false,
            R.string.certificate_is_not_from_car_service,
            R.string.certificate_is_not_from_car_service_details
    );

    public static Function<X509Certificate, CheckResult> EXPIRED_CHECK = cert -> new Date().before(cert.getNotAfter()) ?
            new X509CertificateChainSummaryProvider.CheckResult(
                    true,
                    c -> c.getString(R.string.certificate_is_not_expired_format, getLocalizedDateString(cert.getNotAfter(), FormatStyle.MEDIUM)),
                    c -> c.getString(R.string.certificate_is_expired_detail_format, getLocalizedDateString(cert.getNotBefore(), FormatStyle.SHORT), getLocalizedDateString(cert.getNotAfter(), FormatStyle.SHORT))
            ) : new X509CertificateChainSummaryProvider.CheckResult(
            false,
            c -> c.getString(R.string.certificate_is_expired_format, getLocalizedDateString(cert.getNotAfter(), FormatStyle.MEDIUM))
    );

    public static Function<X509Certificate, CheckResult> VALID_DATE_CHECK = cert -> new Date().after(cert.getNotBefore()) ?
            new X509CertificateChainSummaryProvider.CheckResult(
                    true,
                    R.string.certificate_is_within_valid_period
            ) : new X509CertificateChainSummaryProvider.CheckResult(
            false,
            c -> c.getString(R.string.certificate_is_not_within_valid_period_format, getLocalizedDateString(cert.getNotBefore(), FormatStyle.MEDIUM)),
            c -> c.getString(R.string.certificate_is_not_within_valid_period_detail)
    );

    public static List<Function<X509Certificate, CheckResult>> BASIC_CHECKS = List.of(
            EXPIRED_CHECK,
            VALID_DATE_CHECK
    );


    private final List<Function<X509Certificate, CheckResult>> chainChecks;
    private final List<Function<X509Certificate, CheckResult>> primaryCertChecks;

    public X509CertificateChainSummaryProvider(List<Function<X509Certificate, CheckResult>> chainChecks, List<Function<X509Certificate, CheckResult>> primaryCertChecks) {
        if (chainChecks == null) chainChecks = List.of();
        if (primaryCertChecks == null) primaryCertChecks = List.of();
        this.chainChecks = List.copyOf(chainChecks);
        this.primaryCertChecks = List.copyOf(primaryCertChecks);
    }

    @Nullable
    @Override
    public CharSequence provideSummary(@NonNull Preference preference) {
        Context context = preference.getContext();
        SettingsManager settingsManager = new SettingsManager(context);

        X509Certificate[] certChain;
        try {
            certChain = decodeX509CertChain(settingsManager.getX509CertificateChain(preference.getKey()));
            if (certChain == null) return context.getString(R.string.no_item_pref_summary);
        } catch (Throwable t) {
            return context.getString(R.string.corrupted_item_pref_summary_format, t.toString());
        }

        int colorError;
        try (TypedArray colorAttributes = context.obtainStyledAttributes(new int[] {androidx.constraintlayout.widget.R.attr.colorError})) {
            colorError = colorAttributes.getColor(0, Color.RED);
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (int i = 0; i < certChain.length; i++) {
            X509Certificate cert = certChain[i];
            if (i > 0) ssb.append("\n\n");

            ssb.append(context.getString(
                    R.string.certificate_line_pref_summary_format,
                    i, cert.getSubjectX500Principal().getName()
            ));

            Stream<Function<X509Certificate, CheckResult>> checks = chainChecks.stream();
            if (i == 0) checks = Stream.concat(primaryCertChecks.stream(), checks);
            checks.forEach(check -> {
                CheckResult result = check.apply(cert);
                if (result.passed()) return;
                String line = context.getString(R.string.certificate_check_failure_line_pref_summary_format, result.summary(context));
                ssb.append("\n").append(line, new ForegroundColorSpan(colorError), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            });

        }

        return ssb;
    }
}
