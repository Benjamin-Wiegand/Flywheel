package io.benwiegand.projection.geargrinder.projection.ui;

import static io.benwiegand.projection.geargrinder.util.UiUtil.EASE_IN;
import static io.benwiegand.projection.geargrinder.util.UiUtil.EASE_OUT;

import android.app.Notification;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Icon;
import android.icu.text.NumberFormat;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.StateSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.benwiegand.projection.geargrinder.NotificationService;
import io.benwiegand.projection.geargrinder.PackageService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class NotificationDisplay implements NotificationService.NotificationListener {
    private static final String TAG = NotificationDisplay.class.getSimpleName();

    private static final long TTS_ANNOUNCEMENT_PAUSE = 1500;    // milliseconds to pause between queued TTS messages
    private static final long POPUP_NOTIFICATION_ANIMATION_DURATION = 200;

    private static final long NOTIFICATION_DRAWER_ANIMATION_DURATION = 200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SettingsManager settingsManager;
    private final ViewGroup popupNotificationOverlay;
    private final ViewGroup popupNotificationFrame;
    private final View notificationDrawer;
    private final View notificationIndicatorWidget;
    private final Context context;

    private final NotificationDrawerAdapter notificationDrawerAdapter;

    private final TextToSpeech tts;
    private final NumberFormat numberFormat;

    private boolean notificationDrawerOpen = false;
    private View popupNotificationView = null;

    private NotificationService.ServiceBinder notificationServiceBinder = null;
    private PackageService.ServiceBinder packageServiceBinder = null;

    private Optional<NotificationService.ServiceBinder> getNotificationServiceBinder() {
        return Optional.ofNullable(notificationServiceBinder);
    }

    private Optional<PackageService.ServiceBinder> getPackageServiceBinder() {
        return Optional.ofNullable(packageServiceBinder);
    }


    public NotificationDisplay(ViewGroup popupNotificationOverlay, View notificationDrawer, View notificationIndicatorWidget, SettingsManager settingsManager) {
        this.popupNotificationOverlay = popupNotificationOverlay;
        this.notificationDrawer = notificationDrawer;
        this.notificationIndicatorWidget = notificationIndicatorWidget;
        this.settingsManager = settingsManager;
        popupNotificationFrame = popupNotificationOverlay.findViewById(R.id.popup_notification_frame);
        context = popupNotificationOverlay.getContext();

        popupNotificationOverlay.setOnClickListener(v -> dismissPopupNotification());
        notificationIndicatorWidget.setOnClickListener(v -> toggleNotificationDrawer());
        notificationDrawer.setOnClickListener(v -> closeNotificationDrawer());

        notificationIndicatorWidget.setVisibility(settingsManager.isNotificationDrawerEnabled() ? View.VISIBLE : View.GONE);

        RecyclerView notificationRecycler = notificationDrawer.findViewById(R.id.notification_recycler);
        notificationDrawerAdapter = new NotificationDrawerAdapter();
        notificationRecycler.setAdapter(notificationDrawerAdapter);
        notificationRecycler.setLayoutManager(new LinearLayoutManager(context));

        AudioManager audioManager = context.getSystemService(AudioManager.class);
        int ttsFocusGainType = settingsManager.isPauseMediaDuringNotificationTTSEnabled() ?
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
        AudioFocusRequest ttsAudioFocusRequest = new AudioFocusRequest.Builder(ttsFocusGainType)
                .build();

        tts = new TextToSpeech(context, this::onTTSInit);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "tts finished for utterance: " + utteranceId);
                audioManager.abandonAudioFocusRequest(ttsAudioFocusRequest);
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "tts error for utterance: " + utteranceId);
                audioManager.abandonAudioFocusRequest(ttsAudioFocusRequest);
            }

            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "tts started for utterance: " + utteranceId);
                audioManager.requestAudioFocus(ttsAudioFocusRequest);
            }
        });

        numberFormat = NumberFormat.getNumberInstance();
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

    private void showPopupNotification(View notificationView) {
        if (popupNotificationView != null) hidePopupNotification(popupNotificationView);
        popupNotificationView = notificationView;
        popupNotificationOverlay.animate()
                .setStartDelay(0)
                .setDuration(POPUP_NOTIFICATION_ANIMATION_DURATION)
                .withStartAction(() -> popupNotificationOverlay.setVisibility(View.VISIBLE))
                .alpha(1)
                .start();

        long showDuration = settingsManager.getPopupNotificationDuration() * 1000L;

        popupNotificationFrame.addView(notificationView);
        notificationView.animate()
                .setStartDelay(0)
                .setDuration(POPUP_NOTIFICATION_ANIMATION_DURATION)
                .withStartAction(() -> notificationView.setTranslationY(-notificationView.getHeight()))
                .translationY(0)
                .withEndAction(() -> handler.postDelayed(() -> hidePopupNotification(notificationView), showDuration));
    }

    private void hidePopupNotification(View notificationView) {
        if (notificationView != popupNotificationView) return;
        popupNotificationView = null;
        popupNotificationOverlay.animate()
                .setStartDelay(0)
                .setDuration(POPUP_NOTIFICATION_ANIMATION_DURATION)
                .withEndAction(() -> popupNotificationOverlay.setVisibility(View.GONE))
                .alpha(0)
                .start();

        notificationView.animate()
                .setStartDelay(0)
                .setDuration(POPUP_NOTIFICATION_ANIMATION_DURATION)
                .translationY(-notificationView.getHeight())
                .withEndAction(() -> popupNotificationFrame.removeView(notificationView))
                .start();
    }

    public boolean dismissPopupNotification() {
        if (popupNotificationView == null) return false;
        hidePopupNotification(popupNotificationView);
        return true;
    }

    private CharSequence joinTopText(List<CharSequence> texts) {
        if (texts.isEmpty()) return "";

        CharSequence text = texts.get(0);
        for (int i = 1; i < texts.size(); i++)
            text = context.getString(R.string.notification_top_text_join_format, text, texts.get(i));
        return text;
    }

    private void inflateNotification(View notificationView, StatusBarNotification sbn) {
        Optional<AppRecord> appRecordOptional = getPackageServiceBinder()
                .map(b -> b.getApp(sbn.getPackageName()))
                .filter(Objects::nonNull);

        Bundle extras = sbn.getNotification().extras;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        CharSequence appName = appRecordOptional
                .map(app -> app.label(context.getPackageManager()))
                .orElse(null);

        Icon largeIcon = sbn.getNotification().getLargeIcon();
        Icon smallIcon = sbn.getNotification().getSmallIcon();

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
            smallIconView.setVisibility(View.GONE);
        } else if (smallIcon != null) {
            smallIconView.setImageIcon(smallIcon);
            smallIconView.setBackgroundTintList(new ColorStateList(
                    new int[][] { StateSet.WILD_CARD },
                    new int[] { sbn.getNotification().color }
            ));
            smallIconView.setVisibility(View.VISIBLE);
            largeIconView.setVisibility(View.GONE);
        }

        if (appName != null) {
            ArrayList<CharSequence> topText = new ArrayList<>();
            topText.add(appName);
            if (subText != null) topText.add(subText);
            topLineText.setText(joinTopText(topText));
            topLineText.setVisibility(View.VISIBLE);
        } else {
            topLineText.setVisibility(View.GONE);
        }

        titleView.setText(title);
        textView.setText(text);

        touchTarget.setOnClickListener(v -> speakNotification(sbn, true));
        speakButton.setOnClickListener(v -> speakNotification(sbn, true));
        clearButton.setOnClickListener(v -> {
            notificationDrawerAdapter.removeNotification(sbn);
            hidePopupNotification(notificationView);
        });
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        int flags = sbn.getNotification().flags;

        if (sbn.getGroupKey() != null && (flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
        if ((flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;

        Log.d(TAG, "displaying notification: " + sbn);

        View notificationView = LayoutInflater.from(context).inflate(R.layout.layout_notification, popupNotificationFrame, false);
        inflateNotification(notificationView, sbn);

        notificationDrawerAdapter.addNotification(sbn);
        if (!notificationDrawerOpen && settingsManager.isPopupNotificationsEnabled())
            showPopupNotification(notificationView);

    }

    public void openNotificationDrawer() {
        if (notificationDrawerOpen) return;
        notificationDrawerOpen = true;

        notificationDrawer.setVisibility(View.VISIBLE);
        notificationDrawer.setAlpha(0);
        notificationDrawer.animate()
                .setStartDelay(0)
                .setDuration(NOTIFICATION_DRAWER_ANIMATION_DURATION)
                .setInterpolator(EASE_OUT)
                .alpha(1)
                .start();

        RecyclerView notificationRecycler = notificationDrawer.findViewById(R.id.notification_recycler);
        notificationRecycler.setTranslationX(notificationRecycler.getWidth());
        notificationRecycler.setZ(notificationRecycler.getWidth());
        notificationRecycler.animate()
                .setStartDelay(0)
                .setDuration(NOTIFICATION_DRAWER_ANIMATION_DURATION)
                .setInterpolator(EASE_OUT)
                .translationX(0)
                .z(0)
                .start();

        notificationRecycler.scrollToPosition(0);
    }

    public boolean closeNotificationDrawer() {
        if (!notificationDrawerOpen) return false;
        notificationDrawerOpen = false;

        notificationDrawer.animate()
                .setStartDelay(0)
                .setDuration(NOTIFICATION_DRAWER_ANIMATION_DURATION)
                .setInterpolator(EASE_IN)
                .alpha(0)
                .withEndAction(() -> notificationDrawer.setVisibility(View.GONE))
                .start();

        RecyclerView notificationRecycler = notificationDrawer.findViewById(R.id.notification_recycler);
        notificationRecycler.animate()
                .setStartDelay(0)
                .setDuration(NOTIFICATION_DRAWER_ANIMATION_DURATION)
                .setInterpolator(EASE_IN)
                .translationX(notificationRecycler.getWidth())
                .start();

        return true;
    }

    public void toggleNotificationDrawer() {
        if (notificationDrawerOpen) {
            closeNotificationDrawer();
        } else {
            openNotificationDrawer();
        }
    }

    private class NotificationDrawerAdapter extends RecyclerView.Adapter<NotificationViewHolder> {

        private final List<StatusBarNotification> notifications = new ArrayList<>();

        private void updateIndicators() {
            View noNotificationsIndicator = notificationDrawer.findViewById(R.id.no_notifications_indicator);
            View bellIndicator = notificationIndicatorWidget.findViewById(R.id.notification_bell_indicator);
            View counterIndicator = notificationIndicatorWidget.findViewById(R.id.notification_counter_indicator);
            TextView counterText = notificationIndicatorWidget.findViewById(R.id.notification_counter_text);

            noNotificationsIndicator.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
            bellIndicator.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
            counterIndicator.setVisibility(notifications.isEmpty() ? View.GONE : View.VISIBLE);
            counterText.setText(numberFormat.format(notifications.size()));
        }

        public void addNotification(StatusBarNotification sbn) {
            RecyclerView notificationRecycler = notificationDrawer.findViewById(R.id.notification_recycler);
            boolean scrolled = notificationRecycler.canScrollVertically(-1);

            notifications.add(0, sbn);
            notifyItemInserted(0);
            updateIndicators();

            if (scrolled) return;
            notificationRecycler.scrollToPosition(0);
        }

        public void removeNotification(StatusBarNotification sbn) {
            int index = notifications.indexOf(sbn);
            if (index == -1) return;
            notifications.remove(index);
            notifyItemRemoved(index);
            updateIndicators();
        }

        @NonNull
        @Override
        public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_notification, parent, false);
            return new NotificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
            StatusBarNotification sbn = notifications.get(position);
            inflateNotification(holder.itemView, sbn);
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }
    }

    private static class NotificationViewHolder extends RecyclerView.ViewHolder {
        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

}
