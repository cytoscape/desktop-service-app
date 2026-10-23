package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.types.CyColumnTypes;
import org.cytoscape.serviceapps.types.CyColumnTypes.ColumnType;

/**
 * The parts of a CX2 network that {@code updateNetwork} applies: attribute declarations, network
 * attributes, and nodes and edges with their attributes and positions. Parsing validates the whole
 * document, so nothing is applied from a document that is broken.
 *
 * @see <a
 *     href="https://cytoscape.org/cx/cx2/specification/cytoscape-exchange-format-specification-(version-2)/">CX2
 *     specification</a>
 */
final class Cx2Document {

    /** A declared attribute: its name, Desktop type, and default. */
    record Attribute(String name, ColumnType type, JsonElement defaultValue) {}

    record Node(long id, Map<String, JsonElement> values, Double x, Double y, Double z) {}

    record Edge(long id, long source, long target, Map<String, JsonElement> values) {}

    final Map<String, JsonElement> networkAttributes = new LinkedHashMap<>();
    final List<Node> nodes = new ArrayList<>();
    final List<Edge> edges = new ArrayList<>();
    private final Map<String, Map<String, Attribute>> declarations = new LinkedHashMap<>();
    private final Map<String, Map<String, Attribute>> aliases = new LinkedHashMap<>();

    /**
     * @throws IllegalArgumentException with a message for the user when the document is not CX2
     *     this app can apply
     */
    static Cx2Document parse(JsonElement json) {
        if (json == null || !json.isJsonArray() || json.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("updateNetwork data is not a CX2 network");
        }
        Cx2Document document = new Cx2Document();
        boolean versioned = false;
        List<JsonObject> nodeEntries = new ArrayList<>();
        List<JsonObject> edgeEntries = new ArrayList<>();

        for (JsonElement element : json.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(
                        "updateNetwork data has an aspect that is not an object");
            }
            JsonObject aspect = element.getAsJsonObject();
            if (aspect.has("CXVersion")) {
                versioned = "2.0".equals(aspect.get("CXVersion").getAsString());
            }
            if (aspect.has("attributeDeclarations")) {
                for (JsonElement declaration : aspect.getAsJsonArray("attributeDeclarations")) {
                    document.declare(declaration.getAsJsonObject());
                }
            }
            if (aspect.has("networkAttributes")) {
                for (JsonElement attributes : aspect.getAsJsonArray("networkAttributes")) {
                    attributes
                            .getAsJsonObject()
                            .entrySet()
                            .forEach(e -> document.networkAttributes.put(e.getKey(), e.getValue()));
                }
            }
            if (aspect.has("nodes")) {
                aspect.getAsJsonArray("nodes").forEach(n -> nodeEntries.add(n.getAsJsonObject()));
            }
            if (aspect.has("edges")) {
                aspect.getAsJsonArray("edges").forEach(e -> edgeEntries.add(e.getAsJsonObject()));
            }
        }
        if (!versioned) {
            throw new IllegalArgumentException("updateNetwork data is not CX version 2.0");
        }

        Set<Long> nodeIds = new HashSet<>();
        for (JsonObject entry : nodeEntries) {
            long id = number(entry, "id", "node");
            if (!nodeIds.add(id)) {
                throw new IllegalArgumentException("updateNetwork data repeats node " + id);
            }
            document.nodes.add(
                    new Node(
                            id,
                            document.values("nodes", entry),
                            decimal(entry, "x"),
                            decimal(entry, "y"),
                            decimal(entry, "z")));
        }
        Set<Long> edgeIds = new HashSet<>();
        for (JsonObject entry : edgeEntries) {
            long id = number(entry, "id", "edge");
            long source = number(entry, "s", "edge " + id);
            long target = number(entry, "t", "edge " + id);
            if (!edgeIds.add(id)) {
                throw new IllegalArgumentException("updateNetwork data repeats edge " + id);
            }
            if (!nodeIds.contains(source) || !nodeIds.contains(target)) {
                throw new IllegalArgumentException(
                        "updateNetwork edge " + id + " connects nodes that aren't in the result");
            }
            document.edges.add(new Edge(id, source, target, document.values("edges", entry)));
        }
        return document;
    }

    /** The declared attribute for an element kind and key, or null. */
    Attribute declared(String kind, String name) {
        Map<String, Attribute> attributes = declarations.get(kind);
        return attributes == null ? null : attributes.get(name);
    }

    /** The declared type of an attribute, or the type its value implies when undeclared. */
    ColumnType typeOf(String kind, String name, JsonElement value) {
        Attribute attribute = declared(kind, name);
        return attribute != null ? attribute.type() : CxValues.infer(value);
    }

    private void declare(JsonObject declaration) {
        for (Map.Entry<String, JsonElement> kind : declaration.entrySet()) {
            Map<String, Attribute> attributes = new LinkedHashMap<>();
            Map<String, Attribute> shortNames = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> attribute :
                    kind.getValue().getAsJsonObject().entrySet()) {
                JsonObject spec = attribute.getValue().getAsJsonObject();
                String type = spec.has("d") ? spec.get("d").getAsString() : "string";
                String name = attribute.getKey();
                ColumnType columnType =
                        CyColumnTypes.fromCx2(type)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "updateNetwork data declares attribute '"
                                                                + name
                                                                + "' with unknown type '"
                                                                + type
                                                                + "'"));
                Attribute declared = new Attribute(name, columnType, spec.get("v"));
                attributes.put(name, declared);
                if (spec.has("a")) {
                    // Values use the short key; the column keeps the full name.
                    shortNames.put(spec.get("a").getAsString(), declared);
                }
            }
            declarations.put(kind.getKey(), attributes);
            aliases.put(kind.getKey(), shortNames);
        }
    }

    /**
     * An element's values keyed by full attribute name, resolving aliases. A declared attribute the
     * element leaves out takes its declared default ({@code v}), as the CX2 specification says.
     */
    private Map<String, JsonElement> values(String kind, JsonObject entry) {
        Map<String, JsonElement> values = new LinkedHashMap<>();
        if (entry.has("v") && entry.get("v").isJsonObject()) {
            Map<String, Attribute> shortNames = aliases.getOrDefault(kind, Map.of());
            for (Map.Entry<String, JsonElement> value : entry.getAsJsonObject("v").entrySet()) {
                Attribute alias = shortNames.get(value.getKey());
                values.put(alias != null ? alias.name() : value.getKey(), value.getValue());
            }
        }
        for (Attribute attribute : declarations.getOrDefault(kind, Map.of()).values()) {
            if (attribute.defaultValue() != null && !attribute.defaultValue().isJsonNull()) {
                values.putIfAbsent(attribute.name(), attribute.defaultValue());
            }
        }
        return values;
    }

    private static long number(JsonObject entry, String key, String what) {
        JsonElement value = entry.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    "updateNetwork " + what + " has no numeric '" + key + "'");
        }
        return value.getAsLong();
    }

    private static Double decimal(JsonObject entry, String key) {
        JsonElement value = entry.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsDouble();
    }
}
