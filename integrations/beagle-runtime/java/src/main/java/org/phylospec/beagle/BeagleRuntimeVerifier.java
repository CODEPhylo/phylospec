package org.phylospec.beagle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Verifies native BEAGLE through the Java API available on the launch classpath. */
public final class BeagleRuntimeVerifier {

    public enum Status {
        AVAILABLE,
        API_UNAVAILABLE,
        LOAD_FAILED,
        NO_RESOURCES
    }

    public record Resource(
            int number,
            String name,
            String description,
            long flags
    ) {
    }

    public record Result(
            Status status,
            String version,
            List<Resource> resources,
            String problem
    ) {
        public Result {
            resources = List.copyOf(resources);
        }

        public boolean available() {
            return status == Status.AVAILABLE;
        }
    }

    private static final String DEFAULT_FACTORY_CLASS = "beagle.BeagleFactory";

    private final String factoryClassName;

    public BeagleRuntimeVerifier() {
        this(DEFAULT_FACTORY_CLASS);
    }

    BeagleRuntimeVerifier(String factoryClassName) {
        this.factoryClassName = factoryClassName;
    }

    public Result verify() {
        try {
            Class<?> factoryClass = Class.forName(factoryClassName);
            String version = invokeString(factoryClass, "getVersion");
            List<?> nativeResources = invokeResourceList(factoryClass);
            List<Resource> resources =
                    mapResources(nativeResources);

            if (resources.isEmpty()) {
                return new Result(
                        Status.NO_RESOURCES,
                        version,
                        List.of(),
                        "The BEAGLE Java API loaded, but it exposed no native resources."
                );
            }

            return new Result(
                    Status.AVAILABLE,
                    version,
                    resources,
                    null
            );
        } catch (ClassNotFoundException exception) {
            return failure(
                    Status.API_UNAVAILABLE,
                    "The launch classpath does not provide " + factoryClassName + "."
            );
        } catch (LinkageError exception) {
            return failure(
                    Status.LOAD_FAILED,
                    describe(exception)
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Throwable cause = exception instanceof InvocationTargetException
                    && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            return failure(
                    Status.LOAD_FAILED,
                    describe(cause)
            );
        }
    }

    private String invokeString(Class<?> factoryClass, String methodName)
            throws ReflectiveOperationException {
        Object value = factoryClass.getMethod(methodName).invoke(null);
        return value == null ? "unknown" : value.toString();
    }

    private List<?> invokeResourceList(Class<?> factoryClass)
            throws ReflectiveOperationException {
        Object value = factoryClass.getMethod("getResourceDetails").invoke(null);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> resources)) {
            throw new IllegalStateException(
                    "BEAGLE getResourceDetails() returned "
                            + value.getClass().getName()
                            + " instead of a List."
            );
        }
        return resources;
    }

    private List<Resource> mapResources(List<?> nativeResources)
            throws ReflectiveOperationException {
        List<Resource> resources = new ArrayList<>();
        for (Object nativeResource : nativeResources) {
            if (nativeResource == null) {
                continue;
            }

            Class<?> resourceClass = nativeResource.getClass();
            resources.add(new Resource(
                    invokeNumber(resourceClass, nativeResource, "getNumber").intValue(),
                    invokeText(resourceClass, nativeResource, "getName"),
                    invokeText(resourceClass, nativeResource, "getDescription"),
                    invokeNumber(resourceClass, nativeResource, "getFlags").longValue()
            ));
        }
        return resources;
    }

    private Number invokeNumber(Class<?> type, Object target, String methodName)
            throws ReflectiveOperationException {
        Object value = method(type, methodName).invoke(target);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException(
                    type.getName() + "." + methodName + "() did not return a number."
            );
        }
        return number;
    }

    private String invokeText(Class<?> type, Object target, String methodName)
            throws ReflectiveOperationException {
        Object value = method(type, methodName).invoke(target);
        return value == null ? "" : value.toString();
    }

    private Method method(Class<?> type, String methodName)
            throws NoSuchMethodException {
        return type.getMethod(methodName);
    }

    private Result failure(
            Status status,
            String problem
    ) {
        return new Result(status, null, List.of(), problem);
    }

    private String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
