package tiles.mcmc;

import java.util.List;

public class LoggerParameterNames {

    public final List<String> names;

    public LoggerParameterNames(List<String> names) {
        this.names = List.copyOf(names);
    }
}
