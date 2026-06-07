package tiling.xml;

import dr.app.beauti.util.XMLWriter;
import dr.util.Attribute;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XmlElement {

    private final String tag;
    private final List<Attribute> attributes;
    private final List<XmlElement> children;
    private final String text;

    public XmlElement(String tag) {
        this(tag, List.of(), List.of(), null);
    }

    public XmlElement(
            String tag,
            List<Attribute> attributes,
            List<XmlElement> children,
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

    public static XmlElement element(String tag) {
        return new XmlElement(tag);
    }

    public static XmlElement ref(String tag, String id) {
        return new XmlElement(
                tag,
                List.of(new Attribute.Default<>("idref", id)),
                List.of(),
                null
        );
    }

    public XmlElement withAttribute(String name, Object value) {
        List<Attribute> updated =
                new ArrayList<>(attributes);

        updated.add(new Attribute.Default<>(name, value));

        return new XmlElement(tag, updated, children, text);
    }

    public XmlElement withId(String id) {
        return withAttribute("id", id);
    }

    public XmlElement withText(String text) {
        return new XmlElement(tag, attributes, children, text);
    }

    public XmlElement withChild(XmlElement child) {
        List<XmlElement> updated =
                new ArrayList<>(children);

        updated.add(child);

        return new XmlElement(tag, attributes, updated, text);
    }

    public XmlElement withChildren(List<XmlElement> children) {
        return new XmlElement(tag, attributes, children, text);
    }

    public String tag() {
        return tag;
    }

    public List<Attribute> attributes() {
        return Collections.unmodifiableList(attributes);
    }

    public List<XmlElement> children() {
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

        for (XmlElement child : children) {
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