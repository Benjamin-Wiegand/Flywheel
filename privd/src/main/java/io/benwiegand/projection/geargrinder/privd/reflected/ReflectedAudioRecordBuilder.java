package io.benwiegand.projection.geargrinder.privd.reflected;

import android.media.AudioAttributes;
import android.media.AudioRecord;

import java.lang.reflect.Method;

import io.benwiegand.projection.geargrinder.privd.reflection.ReflectedObject;
import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;

public class ReflectedAudioRecordBuilder extends ReflectedObject {
    public static final String SUBMIX_FIXED_VOLUME = "fixedVolume";

    private final Method setAudioAttributes;

    public ReflectedAudioRecordBuilder(AudioRecord.Builder instance) {
        super(instance, AudioRecord.Builder.class);

        setAudioAttributes = findMethod("setAudioAttributes", AudioAttributes.class);
    }

    public ReflectedAudioRecordBuilder setAudioAttributes(AudioAttributes attributes) throws ReflectionException {
        invokeMethodNoException(setAudioAttributes, attributes);
        return this;
    }

}
