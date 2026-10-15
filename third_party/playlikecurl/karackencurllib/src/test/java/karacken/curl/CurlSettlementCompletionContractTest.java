package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;

/** API prerequisites, deliberately distinct from the original renderer behavioral bench. */
public class CurlSettlementCompletionContractTest {
    @Test
    public void producerRecordIsPublicFinalWithOnlyPrivateConstructorsAndReadOnlyGetters() {
        Class<?> completion = completionType();
        assertTrue("CURL_COMPLETION_PUBLIC_FINAL", Modifier.isPublic(completion.getModifiers())
                && Modifier.isFinal(completion.getModifiers()));
        Constructor<?>[] constructors = completion.getDeclaredConstructors();
        assertTrue("CURL_COMPLETION_PRIVATE_PRODUCER", constructors.length > 0);
        for (Constructor<?> constructor : constructors) {
            assertTrue("CURL_COMPLETION_PRIVATE_PRODUCER", Modifier.isPrivate(constructor.getModifiers()));
        }
        getter(completion, "getSourceGenerationId", long.class);
        getter(completion, "getGestureId", long.class);
        getter(completion, "getCurrentOrdinal", int.class);
        getter(completion, "getPageChange", PageChange.class);
        getter(completion, "getRendererGenerationAtStart", long.class);
        getter(completion, "getRendererGenerationAtCompletion", long.class);
        getter(completion, "getSequence", long.class);
        for (Method method : completion.getDeclaredMethods()) {
            assertTrue("CURL_COMPLETION_NO_PUBLIC_MUTATOR",
                    !Modifier.isPublic(method.getModifiers()) || method.getReturnType() != void.class);
            assertTrue("CURL_COMPLETION_NO_PUBLIC_FACTORY",
                    !Modifier.isPublic(method.getModifiers())
                            || !Modifier.isStatic(method.getModifiers())
                            || method.getReturnType() != completion);
        }
    }

    @Test
    public void terminalRequestRequiresOriginalCompletionAndExplicitSuccessorGeneration() {
        Method method = publicMethod(PageSurfaceView.class,
                "requestCurlSettlementTerminalPresentedFrame", completionType(), long.class, Runnable.class);
        assertEquals("CURL_COMPLETION_REQUEST_ORIGINAL_ID", long.class, method.getReturnType());
        Method current = publicMethod(PageSurfaceView.class,
                "isCurlSettlementCompletionCurrent", completionType());
        assertEquals("CURL_COMPLETION_READ_ONLY_CURRENTNESS", boolean.class, current.getReturnType());
    }

    @Test
    public void originalListenerExtensionIsBackwardCompatibleDefaultNotAnotherCallbackRegistry() {
        Method method = publicMethod(PageSurfaceListener.class, "onSettlementCompleted",
                long.class, long.class, String.class, int.class, PageChange.class, completionType());
        assertTrue("CURL_COMPLETION_LISTENER_DEFAULT", method.isDefault());
        assertEquals(void.class, method.getReturnType());
    }

    private static Class<?> completionType() {
        try {
            return Class.forName("karacken.curl.PageSurfaceView$CurlSettlementCompletion");
        } catch (ClassNotFoundException absent) {
            throw new AssertionError("CURL_PREREQUISITE_COMPLETION_API_MISSING");
        }
    }

    private static void getter(Class<?> completion, String name, Class<?> returnType) {
        assertEquals("CURL_PREREQUISITE_COMPLETION_GETTER", returnType,
                publicMethod(completion, name).getReturnType());
    }

    private static Method publicMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException absent) {
            throw new AssertionError("CURL_PREREQUISITE_COMPLETION_API_MISSING");
        }
    }
}
