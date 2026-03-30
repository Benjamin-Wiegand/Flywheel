package io.benwiegand.projection.geargrinder.projection.ui;

import android.app.Notification;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.StateSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.benwiegand.projection.geargrinder.NotificationService;
import io.benwiegand.projection.geargrinder.PackageService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;

public class NotificationDisplay implements NotificationService.NotificationListener {
    private static final String TAG = NotificationDisplay.class.getSimpleName();

    private static final long TTS_ANNOUNCEMENT_PAUSE = 1500;    // milliseconds to pause between queued TTS messages
    private static final long POPUP_NOTIFICATION_ANIMATION_DURATION = 200;
    private static final long POPUP_NOTIFICATION_SHOW_DURATION = 10000;

    private final Set<View> popupNotificationViews = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ViewGroup popupNotificationOverlay;
    private final ViewGroup popupNotificationFrame;
    private final Context context;

    private final TextToSpeech tts;

    private NotificationService.ServiceBinder notificationServiceBinder = null;
    private PackageService.ServiceBinder packageServiceBinder = null;

    private Optional<NotificationService.ServiceBinder> getNotificationServiceBinder() {
        return Optional.ofNullable(notificationServiceBinder);
    }

    private Optional<PackageService.ServiceBinder> getPackageServiceBinder() {
        return Optional.ofNullable(packageServiceBinder);
    }


    public NotificationDisplay(ViewGroup popupNotificationOverlay) {
        this.popupNotificationOverlay = popupNotificationOverlay;
        popupNotificationFrame = popupNotificationOverlay.findViewById(R.id.popup_notification_frame);
        context = popupNotificationOverlay.getContext();

        tts = new TextToSpeech(context, this::onTTSInit);
    }

    public void destroy() {
        getNotificationServiceBinder().ifPresent(b -> b.unregisterCallback(this));
        tts.shutdown();
    }

    public void setNotificationServiceBinder(NotificationService.ServiceBinder binder) {
        notificationServiceBinder = binder;
        binder.registerListener(this);
    }

    public void setPackageServiceBinder(PackageService.ServiceBinder binder) {
        packageServiceBinder = binder;
    }

    private void onTTSInit(int status) {
        Log.i(TAG, "TTS init status: " + status);
    }

    private String generateUtteranceId() {
        return UUID.randomUUID().toString();
    }

    public void speakText(CharSequence text, boolean interrupt) {
        int maxUtteranceLength = TextToSpeech.getMaxSpeechInputLength();
        int utterances = Math.ceilDiv(text.length(), maxUtteranceLength);
        if (utterances <= 0) return;

        if (utterances > 1)
            Log.d(TAG, "splitting " + text.length() + " character text into " + utterances + " utterances");

        // TODO: this method of splitting will split words
        CharSequence[] utteranceTexts = new CharSequence[utterances];
        for (int i = 0; i < utteranceTexts.length; i++) {
            int start = i * maxUtteranceLength;
            int end = start + Math.min(text.length() - start, maxUtteranceLength);
            utteranceTexts[i] = text.subSequence(start, end);
        }

        int queueMode = interrupt ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
        Bundle params = new Bundle();

        tts.speak(utteranceTexts[0], queueMode, params, generateUtteranceId());
        for (int i = 1; i < utteranceTexts.length; i++)
            tts.speak(utteranceTexts[i], TextToSpeech.QUEUE_ADD, params, generateUtteranceId());
        tts.playSilentUtterance(TTS_ANNOUNCEMENT_PAUSE, TextToSpeech.QUEUE_ADD, generateUtteranceId());
    }

    public void speakNotification(StatusBarNotification sbn, boolean interrupt) {
        Bundle extras = sbn.getNotification().extras;

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence tickerText = sbn.getNotification().tickerText;

        CharSequence announcementText;
        if (tickerText != null) {
            announcementText = tickerText;
        } else if (title != null && text != null) {
            announcementText = context.getString(R.string.notification_tts_announcement_format, title, text);
        } else if (title != null) {
            announcementText = title;
        } else if (text != null) {
            announcementText = text;
        } else {
            announcementText = context.getText(R.string.notification_tts_announcement_no_content);
        }

        speakText(announcementText, interrupt);
    }

    private void animatePopupNotificationOverlay() {
        boolean show = !popupNotificationViews.isEmpty();
        popupNotificationOverlay.animate()
                .setStartDelay(0)
                .setDuration(POPUP_NOTIFICATION_ANIMATION_DURATION)
                .withStartAction(() -> { if (show) popupNotificationOverlay.setVisibility(View.VISIBLE); })
                .withEndAction(() -> { if (!show) popupNotificationOverlay.setVisibility(View.GONE); })
                .alpha(show ? 1 : 0)
                .start();
    }

    private void hidePopupNotification(View notificationView) {
        if (!popupNotificationViews.remove(notificationView)) return;
        animatePopupNotificationOverlay();

        notificationView.animate()
                .setStartDelay(0)
                .setDuration(POPUP_NOTIFICATION_ANIMATION_DURATION)
                .translationY(-notificationView.getHeight())
                .withEndAction(() -> popupNotificationFrame.removeView(notificationView))
                .start();
    }

    private CharSequence joinTopText(List<CharSequence> texts) {
        if (texts.isEmpty()) return "";

        CharSequence text = texts.get(0);
        for (int i = 1; i < texts.size(); i++)
            text = context.getString(R.string.notification_top_text_join_format, text, texts.get(i));
        return text;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        int flags = sbn.getNotification().flags;

        if (sbn.getGroupKey() != null && (flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
        if ((flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;

        Log.d(TAG, "displaying notification: " + sbn);

        Optional<AppRecord> appRecordOptional = getPackageServiceBinder()
                .map(b -> b.getApp(sbn.getPackageName()))
                .filter(Objects::nonNull);

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        CharSequence appName = appRecordOptional
                .map(app -> app.label(context.getPackageManager()))
                .orElse(null);

        Icon largeIcon = sbn.getNotification().getLargeIcon();
        Icon smallIcon = sbn.getNotification().getSmallIcon();

        View notificationView = LayoutInflater.from(context).inflate(R.layout.layout_notification_popup, popupNotificationFrame, false);
        ImageView smallIconView = notificationView.findViewById(R.id.notification_small_icon);
        ImageView largeIconView = notificationView.findViewById(R.id.notification_large_icon);
        TextView topLineText = notificationView.findViewById(R.id.notification_top_line_text);
        TextView titleView = notificationView.findViewById(R.id.notification_title);
        TextView textView = notificationView.findViewById(R.id.notification_text);
        View touchTarget = notificationView.findViewById(R.id.touch_target);
        Button speakButton = notificationView.findViewById(R.id.speak_button);
        Button clearButton = notificationView.findViewById(R.id.clear_button);

        if (largeIcon != null) {
            largeIconView.setImageIcon(largeIcon);
            largeIconView.setVisibility(View.VISIBLE);
        } else if (smallIcon != null) {
            smallIconView.setImageIcon(smallIcon);
            smallIconView.setVisibility(View.VISIBLE);
            smallIconView.setBackgroundTintList(new ColorStateList(
                    new int[][] { StateSet.WILD_CARD },
                    new int[] { sbn.getNotification().color }
            ));
        }

        if (appName != null) {
            ArrayList<CharSequence> topText = new ArrayList<>();
            topText.add(appName);
            if (subText != null) topText.add(subText);
            topLineText.setText(joinTopText(topText));
        } else {
            topLineText.setVisibility(View.GONE);
        }

        titleView.setText(title);
        textView.setText(text);

        touchTarget.setOnClickListener(v -> speakNotification(sbn, true));
        speakButton.setOnClickListener(v -> speakNotification(sbn, true));
        clearButton.setOnClickListener(v -> hidePopupNotification(notificationView));
        popupNotificationOverlay.setOnClickListener(v -> hidePopupNotification(notificationView));

        popupNotificationFrame.addView(notificationView);
        popupNotificationViews.add(notificationView);
        animatePopupNotificationOverlay();

        handler.post(() -> {
            notificationView.animate()
                    .setStartDelay(0)
                    .setDuration(POPUP_NOTIFICATION_ANIMATION_DURATION)
                    .withStartAction(() -> notificationView.setTranslationY(-notificationView.getHeight()))
                    .translationY(0)
                    .withEndAction(() -> handler.postDelayed(() -> hidePopupNotification(notificationView), POPUP_NOTIFICATION_SHOW_DURATION));
        });


    }
}
