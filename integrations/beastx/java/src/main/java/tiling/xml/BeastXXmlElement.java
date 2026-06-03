package tiling.xml;

import dr.app.beauti.util.XMLWriter;
import dr.util.Attribute;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BeastXXmlElement {

    private final String tag;
    private final List<Attribute> attributes;
    private final List<BeastXXmlElement> children;
    private final String text;

    public BeastXXmlElement(String tag) {
        this(tag, List.of(), List.of(), null);
    }

    public BeastXXmlElement(
            String tag,
            List<Attribute> attributes,
            List<BeastXXmlElement> children,
            String text
    ) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("XML element tag must not be blank.");
        }

        this.tag = tag;
        this.attributes = List.copyOf(attributes);
        this.children = List.copyOf(children);
        this.text = text;
    }

    public static BeastXXmlElement element(String tag) {
        return new BeastXXmlElement(tag);
    }

    public static BeastXXmlElement ref(String tag, String id) {
        return new BeastXXmlElement(
                tag,
                List.of(new Attribute.Default<>("idref", id)),
                List.of(),
                null
        );
    }

    public BeastXXmlElement withAttribute(String name, Object value) {
        List<Attribute> updated =
                new ArrayList<>(attributes);

        updated.add(new Attribute.Default<>(name, value));

        return new BeastXXmlElement(tag, updated, children, text);
    }

    public BeastXXmlElement withId(String id) {
        return withAttribute("id", id);
    }

    public BeastXXmlElement withText(String text) {
        return new BeastXXmlElement(tag, attributes, children, text);
    }

    public BeastXXmlElement withChild(BeastXXmlElement child) {
        List<BeastXXmlElement> updated =
                new ArrayList<>(children);

        updated.add(child);

        return new BeastXXmlElement(tag, attributes, updated, text);
    }

    public BeastXXmlElement withChildren(List<BeastXXmlElement> children) {
        return new BeastXXmlElement(tag, attributes, children, text);
    }

    public String tag() {
        return tag;
    }

    public List<Attribute> attributes() {
        return Collections.unmodifiableList(attributes);
    }

    public List<BeastXXmlElement> children() {
        return Collections.unmodifiableList(children);
    }

    public String text() {
        return text;
    }

    public void write(XMLWriter writer) {
        if (children.isEmpty() && (text == null || text.isBlank())) {
            writer.writeTag(tag, attributes.toArray(Attribute[]::new), true);
            return;
        }

        writer.writeOpenTag(tag, attributes);

        if (text != null && !text.isBlank()) {
            writer.writeText(text);
        }

        for (BeastXXmlElement child : children) {
            child.write(writer);
        }

        writer.writeCloseTag(tag);
    }

    @Override
    public String toString() {
        StringWriter stringWriter =
                new StringWriter();

        XMLWriter xmlWriter =
                new XMLWriter(stringWriter);

        write(xmlWriter);
        xmlWriter.flush();

        return stringWriter.toString();
    }
}