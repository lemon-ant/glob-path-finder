package io.github.lemon_ant.globpathfinder;

import java.lang.reflect.Method;
import lombok.experimental.UtilityClass;

@UtilityClass
class ReflectiveMethodInvoker {

    /**
     * Invoke a private static method by name using only argument values.
     * Strategy:
     * 1) Try exact lookup with inferred parameter types (wrapper -> primitive where possible).
     * 2) If not found, fallback to scanning declared methods for a compatible signature.
     */
    @SuppressWarnings("unchecked")
    static <T> T invokePrivateStatic(Class<?> targetClass, String methodName, Object... arguments) throws Exception {
        Method resolvedMethod;
        try {
            Class<?>[] exactParameterTypes = inferExactParameterTypes(arguments);
            resolvedMethod = targetClass.getDeclaredMethod(methodName, exactParameterTypes);
        } catch (NoSuchMethodException noSuchMethod) {
            resolvedMethod = resolveCompatibleDeclaredMethod(targetClass, methodName, arguments);
        }
        resolvedMethod.setAccessible(true);
        return (T) resolvedMethod.invoke(null, arguments);
    }

    // ---------- resolution helpers ----------

    private static Method resolveCompatibleDeclaredMethod(Class<?> targetClass, String methodName, Object[] arguments)
            throws NoSuchMethodException {

        Method[] declaredMethods = targetClass.getDeclaredMethods();
        for (Method candidateMethod : declaredMethods) {
            if (!candidateMethod.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] parameterTypes = candidateMethod.getParameterTypes();
            int argumentCount = (arguments == null) ? 0 : arguments.length;
            if (parameterTypes.length != argumentCount) {
                continue;
            }
            boolean allParametersCompatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!isParameterCompatible(parameterTypes[index], arguments[index])) {
                    allParametersCompatible = false;
                    break;
                }
            }
            if (allParametersCompatible) {
                return candidateMethod;
            }
        }
        throw new NoSuchMethodException("No compatible method '" + methodName + "' found in " + targetClass.getName());
    }

    private static Class<?>[] inferExactParameterTypes(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return new Class<?>[0];
        }
        Class<?>[] parameterTypes = new Class<?>[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            Object argumentValue = arguments[index];
            if (argumentValue == null) {
                // Exact lookup cannot match primitive here; Object.class is a safe placeholder to trigger fallback.
                parameterTypes[index] = Object.class;
            } else {
                parameterTypes[index] = toPrimitiveIfWrapper(argumentValue.getClass());
            }
        }
        return parameterTypes;
    }

    private static boolean isParameterCompatible(Class<?> parameterType, Object argumentValue) {
        if (argumentValue == null) {
            // null cannot be assigned to primitives
            return !parameterType.isPrimitive();
        }
        Class<?> argumentClass = argumentValue.getClass();
        if (parameterType.isPrimitive()) {
            return toPrimitiveIfWrapper(argumentClass) == parameterType;
        }
        return parameterType.isAssignableFrom(argumentClass);
    }

    private static Class<?> toPrimitiveIfWrapper(Class<?> maybeWrapper) {
        if (maybeWrapper == Boolean.class) return boolean.class;
        if (maybeWrapper == Byte.class) return byte.class;
        if (maybeWrapper == Short.class) return short.class;
        if (maybeWrapper == Character.class) return char.class;
        if (maybeWrapper == Integer.class) return int.class;
        if (maybeWrapper == Long.class) return long.class;
        if (maybeWrapper == Float.class) return float.class;
        if (maybeWrapper == Double.class) return double.class;
        return maybeWrapper;
    }
}
