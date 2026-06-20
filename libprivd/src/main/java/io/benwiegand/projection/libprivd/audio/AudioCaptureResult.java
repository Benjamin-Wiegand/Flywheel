package io.benwiegand.projection.libprivd.audio;

import android.os.Parcel;
import android.os.Parcelable;

public class AudioCaptureResult implements Parcelable {

    public AudioCaptureError error = AudioCaptureError.FAILURE;
    public int length = 0;
    public long timestamp = 0;
    public boolean silent = false;

    public AudioCaptureResult() { }

    protected AudioCaptureResult(Parcel in) {
        readFromParcel(in);
    }

    public static final Creator<AudioCaptureResult> CREATOR = new Creator<>() {
        @Override
        public AudioCaptureResult createFromParcel(Parcel in) {
            return new AudioCaptureResult(in);
        }

        @Override
        public AudioCaptureResult[] newArray(int size) {
            return new AudioCaptureResult[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(error.ordinal());
        dest.writeInt(length);
        dest.writeLong(timestamp);
        dest.writeByte((byte) (silent ? 1 : 0));
    }

    public void readFromParcel(Parcel in) {
        error = AudioCaptureError.parse(in.readInt());
        length = in.readInt();
        timestamp = in.readLong();
        silent = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
