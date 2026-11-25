import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;

/**
 * Skips a test instead of failing it if the BEAST X likelihood needs the native BEAGLE library and
 * it is not installed on this machine.
 */
public class SkipWithoutBeagle implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (XmlTestSupport.isMissingBeagleLibrary(throwable)) {
            throw new TestAbortedException("BEAGLE native library is not available.", throwable);
        }
        throw throwable;
    }
}
