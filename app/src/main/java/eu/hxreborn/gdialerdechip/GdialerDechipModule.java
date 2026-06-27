package eu.hxreborn.gdialerdechip;

import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class GdialerDechipModule extends XposedModule {

    private static final String TAG = "GdialerDechip";
    private static final String MODEL_ADAPTER_ANCHOR = "exception thrown when producing chip";
    private static final String FEEDBACK_CHIP_KEY = "TRANSCRIPT_AUDIO_FEEDBACK";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage() || !BuildConfig.TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (!loadDexkit()) {
            return;
        }
        try {
            hookModelAdapters(param.getClassLoader());
        } catch (Exception e) {
            log(Log.ERROR, TAG, "hook install failed", e);
        }
    }

    private boolean loadDexkit() {
        try {
            System.loadLibrary("dexkit");
            return true;
        } catch (UnsatisfiedLinkError e) {
            log(Log.ERROR, TAG, "libdexkit.so failed to load", e);
            return false;
        }
    }

    private void hookModelAdapters(ClassLoader classLoader) {
        var adapters = modelAdapters(classLoader);
        adapters.forEach(adapter -> hook(adapter).intercept(this::modelWithoutChip));
        log(Log.INFO, TAG, "hooked model adapters " + adapters);
    }

    private static List<Method> modelAdapters(ClassLoader classLoader) {
        try (var dexkit = DexKitBridge.create(classLoader, true)) {
            return dexkit.findMethod(modelAdapterQuery()).stream()
                .map(match -> resolveMethod(match, classLoader))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
    }

    private static FindMethod modelAdapterQuery() {
        return new FindMethod().matcher(new MethodMatcher().usingStrings(MODEL_ADAPTER_ANCHOR));
    }

    private static Method resolveMethod(MethodData match, ClassLoader classLoader) {
        try {
            return match.getMethodInstance(classLoader);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Object modelWithoutChip(XposedInterface.Chain chain) throws Throwable {
        var rowModel = chain.proceed();
        if (rowModel != null) {
            removeFeedbackChips(rowModel);
        }
        return rowModel;
    }

    private void removeFeedbackChips(Object rowModel) {
        try {
            chipLists(rowModel).forEach(chips -> chips.removeIf(GdialerDechipModule::isFeedbackChip));
        } catch (Exception e) {
            log(Log.WARN, TAG, "chip remove failed", e);
        }
    }

    private static Stream<List<?>> chipLists(Object rowModel) {
        return Arrays.stream(rowModel.getClass().getDeclaredFields())
            .filter(field -> List.class.isAssignableFrom(field.getType()))
            .<List<?>>map(field -> listValue(rowModel, field))
            .filter(Objects::nonNull);
    }

    private static boolean isFeedbackChip(Object chip) {
        if (chip == null) {
            return false;
        }
        return Arrays.stream(chip.getClass().getDeclaredFields())
            .filter(field -> field.getType().isEnum())
            .map(field -> enumName(chip, field))
            .anyMatch(FEEDBACK_CHIP_KEY::equalsIgnoreCase);
    }

    private static List<?> listValue(Object owner, Field field) {
        field.setAccessible(true);
        try {
            return field.get(owner) instanceof List<?> list ? list : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static String enumName(Object owner, Field field) {
        field.setAccessible(true);
        try {
            return field.get(owner) instanceof Enum<?> constant ? constant.name() : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
