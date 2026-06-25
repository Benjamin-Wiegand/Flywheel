package io.benwiegand.projection.geargrinder.projection.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;

import java.io.InputStream;

import io.benwiegand.projection.geargrinder.R;

public class PhoneCallDisplay {
    private static final String TAG = PhoneCallDisplay.class.getSimpleName();

    private static final long INCOMING_CALL_OVERLAY_ANIMATION_DURATION = 200;

    private record CallerId(String phoneNumber, String displayName, Uri photoUri, Uri photoThumbnailUri) {
        public CallerId(String phoneNumber) {
            this(phoneNumber, null, null, null);
        }

        public CallerId() {
            this(null, null, null, null);
        }

        public String friendlyName(Context context) {
            if (displayName() != null) return displayName();
            if (phoneNumber() != null) return phoneNumber();
            return context.getString(R.string.unknown_caller);
        }

        public Drawable thumbnailDrawable(Context context) {
            try {
                Uri uri = photoThumbnailUri() != null ? photoThumbnailUri() : photoUri();
                if (uri != null) {
                    InputStream is = context.getContentResolver().openInputStream(uri);
                    return Drawable.createFromStream(is, null);
                }
            } catch (Throwable t) {
                Log.e(TAG, "failed to load contact photo", t);
            }

            return AppCompatResources.getDrawable(context, R.drawable.ic_launcher_foreground);  // TODO
        }
    }

    private static int callStateStringToInt(String callStateStr) {
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(callStateStr)) return TelephonyManager.CALL_STATE_IDLE;
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(callStateStr)) return TelephonyManager.CALL_STATE_RINGING;
        if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(callStateStr)) return TelephonyManager.CALL_STATE_OFFHOOK;
        Log.wtf(TAG, "unknown call state: " + callStateStr);
        return -1;
    }


    private final Context context;
    private final ViewGroup popupIncomingCallOverlay;
    private final TelephonyManager telephonyManager;
    private final TelecomManager telecomManager;

    private int callState = TelephonyManager.CALL_STATE_IDLE;
    private CallerId callerId = new CallerId();

    private final BroadcastReceiver phoneStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "phone state update: " + intent);
            Log.d(TAG, "extras: " + intent.getExtras());
            if (intent.getExtras() != null) {
                for (String key : intent.getExtras().keySet()) {
                    Log.v(TAG, "- " + key + " = " + intent.getStringExtra(key));
                }
            }

            if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

            String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            int newCallState = callStateStringToInt(intent.getStringExtra(TelephonyManager.EXTRA_STATE));
            if (newCallState == -1) {
                newCallState = telephonyManager.getCallState();
                Log.w(TAG, "using call state from telephony manager: " + newCallState);
            }

            boolean callerIdUpdated = false;

            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                callerIdUpdated = true;
                callerId = new CallerId();
            }

            if (phoneNumber != null && !phoneNumber.equals(callerId.phoneNumber())) {
                callerIdUpdated = true;
                callerId = queryCallerId(phoneNumber);
            }

            if (callerIdUpdated) onCallerIdUpdated();

            callState = newCallState;
            switch (callState) {
                case TelephonyManager.CALL_STATE_IDLE -> onCallIdle();
                case TelephonyManager.CALL_STATE_RINGING -> onCallRinging();
                case TelephonyManager.CALL_STATE_OFFHOOK -> onCallOffHook();
                default -> Log.wtf(TAG, "unhandled call state: " + callState, new AssertionError());
            }
        }
    };

    public PhoneCallDisplay(Context context, ViewGroup popupIncomingCallOverlay) {
        this.context = context;
        this.popupIncomingCallOverlay = popupIncomingCallOverlay;

        telephonyManager = context.getSystemService(TelephonyManager.class);
        telecomManager = context.getSystemService(TelecomManager.class);

        inflateIncomingCallPopup();

        context.registerReceiver(phoneStateReceiver, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
    }

    public void destroy() {
        context.unregisterReceiver(phoneStateReceiver);
    }

    private void animateIncomingCallOverlay(boolean show) {
        popupIncomingCallOverlay.animate()
                .setStartDelay(0)
                .setDuration(INCOMING_CALL_OVERLAY_ANIMATION_DURATION)
                .withStartAction(() -> { if (show) popupIncomingCallOverlay.setVisibility(View.VISIBLE); })
                .withEndAction(() -> { if (!show) popupIncomingCallOverlay.setVisibility(View.GONE); })
                .alpha(show ? 1 : 0)
                .translationX(show ? 0 : popupIncomingCallOverlay.getWidth());
    }

    private void onCallerIdUpdated() {
        TextView cidText = popupIncomingCallOverlay.findViewById(R.id.caller_id_text);
        ImageView cidImage = popupIncomingCallOverlay.findViewById(R.id.caller_id_image);
        cidText.setText(callerId.friendlyName(context));
        cidImage.setImageDrawable(callerId.thumbnailDrawable(context));
    }

    private void onCallRinging() {
        animateIncomingCallOverlay(true);
    }

    private void onCallOffHook() {
        animateIncomingCallOverlay(false);
    }

    private void onCallIdle() {
        animateIncomingCallOverlay(false);
    }

    private CallerId queryCallerId(String phoneNumber) {
        Log.d(TAG, "finding contact for " + phoneNumber);
        String[] projection = new String[] {
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME_PRIMARY,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
        };
        try (Cursor cur = context.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)), projection, null, null, null)) {
            if (cur == null || !cur.moveToFirst()) {
                Log.e(TAG, "contact lookup failed: " + (cur == null ? "null result" : "no results"));
                return new CallerId(phoneNumber);
            }

            String displayName = cur.getString(1);
            String photoUri = cur.getString(2);
            String photoThumbUri = cur.getString(3);

            return new CallerId(
                    phoneNumber, displayName,
                    photoUri != null ? Uri.parse(photoUri) : null,
                    photoThumbUri != null ? Uri.parse(photoThumbUri) : null
            );

        } catch (Throwable t) {
            Log.e(TAG, "contact lookup threw", t);
        }

        return new CallerId(phoneNumber);
    }

    private void inflateIncomingCallPopup() {
        View acceptCallButton = popupIncomingCallOverlay.findViewById(R.id.accept_call_button);
        View rejectCallButton = popupIncomingCallOverlay.findViewById(R.id.reject_call_button);
        View fallbackNotice = popupIncomingCallOverlay.findViewById(R.id.incoming_call_fallback_notice);

        Runnable showFallbackNotice = () -> {
            acceptCallButton.setVisibility(View.GONE);
            fallbackNotice.setVisibility(View.VISIBLE);
        };

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
            showFallbackNotice.run();

        acceptCallButton.setOnClickListener(v -> {
            try {
                telecomManager.acceptRingingCall();
            } catch (Throwable t) {
                Log.wtf(TAG, "failed to accept call", t);
                showFallbackNotice.run();
            }
        });

        rejectCallButton.setOnClickListener(v -> {
            // this doesn't work since if there's an active call and another incoming call it'll hang up the active call
//            try {
//                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
//                telecomManager.endCall();
//            } catch (Throwable t) {
//                Log.wtf(TAG, "failed to reject call", t);
//                showFallbackNotice.run();
//            }

            // unfortunately not much I can do here, USB doesn't count as a "companion device".
            // and figuring out how to do it via privd is a rabbit hole that I don't have the time for currently.
            // perhaps it could be doable with InCallService for wireless connections, but I don't actually use a wireless connection.
            animateIncomingCallOverlay(false);
        });
    }


}
