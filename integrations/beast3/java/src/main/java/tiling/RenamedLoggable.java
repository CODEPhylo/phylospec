package tiling;

import beast.base.core.BEASTObject;
import beast.base.core.Input;
import beast.base.core.Loggable;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Logs another loggable under a different name. */
public final class RenamedLoggable extends BEASTObject implements Loggable {

    public final Input<BEASTObject> loggableInput =
            new Input<>("loggable", "Loggable whose values are logged.", Input.Validate.REQUIRED);
    public final Input<String> labelInput =
            new Input<>("label", "Name under which the values are logged.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (!(this.loggableInput.get() instanceof Loggable)) {
            throw new IllegalArgumentException("Only loggable objects can be logged under a different name.");
        }
        if (this.labelInput.get().isBlank()) {
            throw new IllegalArgumentException("The name of a logged parameter must not be blank.");
        }
    }

    @Override
    public void init(PrintStream out) {
        for (String column : this.getRenamedColumns()) {
            out.print(column + "\t");
        }
    }

    /**
     * Returns the column names of the wrapped loggable with its own name replaced by the new name.
     * Columns that do not start with the name of the wrapped loggable are numbered instead.
     */
    private List<String> getRenamedColumns() {
        List<String> columns = this.getOriginalColumns();
        String originalName = this.loggableInput.get().getID();
        String name = this.labelInput.get();

        List<String> renamedColumns = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);

            if (originalName != null && column.startsWith(originalName)) {
                renamedColumns.add(name + column.substring(originalName.length()));
            } else if (columns.size() == 1) {
                renamedColumns.add(name);
            } else {
                renamedColumns.add(name + "." + (i + 1));
            }
        }

        return renamedColumns;
    }

    private List<String> getOriginalColumns() {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        this.getLoggable().init(new PrintStream(header, true, StandardCharsets.UTF_8));

        List<String> columns = new ArrayList<>();
        for (String column : header.toString(StandardCharsets.UTF_8).split("\t")) {
            if (!column.isBlank()) columns.add(column.trim());
        }

        return columns;
    }

    @Override
    public void log(long sample, PrintStream out) {
        this.getLoggable().log(sample, out);
    }

    @Override
    public void close(PrintStream out) {
        this.getLoggable().close(out);
    }

    private Loggable getLoggable() {
        return (Loggable) this.loggableInput.get();
    }
}
