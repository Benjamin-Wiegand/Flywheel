package io.benwiegand.projection.geargrinder.privd.reflection;

import android.content.Context;
import android.hardware.display.DisplayManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ReflectionUtils {
    public static DisplayManager createPrivilegedDisplayManager(Context context) throws ReflectionException {
        try {
            // noinspection JavaReflectionMemberAccess: it exists trust me bro
            Constructor<DisplayManager> constructor = DisplayManager.class.getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);
            return constructor.newInstance(context);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
            throw new ReflectionException(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) throw re;
            throw new ReflectionException("unexpected exception while getting instance of DisplayManager", e.getTargetException());
        }
    }
}
