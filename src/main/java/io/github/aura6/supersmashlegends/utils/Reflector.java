package io.github.aura6.supersmashlegends.utils;

import java.lang.reflect.InvocationTargetException;

public class Reflector {

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<? extends T> clazz, Object... params) {
        try {
            return (T) clazz.getDeclaredConstructors()[0].newInstance(params);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
