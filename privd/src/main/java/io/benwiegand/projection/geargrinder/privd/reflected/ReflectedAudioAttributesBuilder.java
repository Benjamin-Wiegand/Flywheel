package io.benwiegand.projection.geargrinder.privd.reflected;

import android.media.AudioAttributes;

import java.lang.reflect.Method;

import io.benwiegand.projection.geargrinder.privd.reflection.ReflectedObject;
import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;

public class ReflectedAudioAttributesBuilder extends ReflectedObject {
    private final Method setInternalCapturePreset;
    private final Method addTag;

    public ReflectedAudioAttributesBuilder(AudioAttributes.Builder instance) {
        super(instance, AudioAttributes.Builder.class);

        setInternalCapturePreset = findMethod("setInternalCapturePreset", int.class);
        addTag = findMethod("addTag", String.class);
    }

    public ReflectedAudioAttributesBuilder setInternalCapturePreset(int preset) throws ReflectionException {
        invokeMethodNoException(setInternalCapturePreset, preset);
        return this;
    }

    public ReflectedAudioAttributesBuilder addTag(String tag) throws ReflectionException {
        invokeMethodNoException(addTag, tag);
        return this;
    }

}
