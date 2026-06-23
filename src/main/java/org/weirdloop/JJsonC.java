package org.weirdloop;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class JJsonC {

    // ==========================================
    // 1. Lightweight Recursive-Descent JSON Parser
    // ==========================================
    public static class JsonParser {
        private final String src;
        private int pos = 0;

        public JsonParser(String src) {
            this.src = src;
        }

        public Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos < src.length()) {
                throw new RuntimeException("Unexpected character at pos " + pos + ": '" + src.charAt(pos) + "'");
            }
            return value;
        }

        private Object parseValue() {
            if (pos >= src.length()) {
                throw new RuntimeException("Unexpected end of input");
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == '"') {
                return parseString();
            } else if (c == 't' || c == 'f') {
                return parseBoolean();
            } else if (c == 'n') {
                return parseNull();
            } else if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            } else {
                throw new RuntimeException("Unexpected character '" + c + "' at pos " + pos);
            }
        }

        private Map<String, Object> parseObject() {
            consume('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (src.charAt(pos) == '}') {
                consume('}');
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                consume(':');
                skipWhitespace();
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                char c = src.charAt(pos);
                if (c == '}') {
                    consume('}');
                    break;
                } else if (c == ',') {
                    consume(',');
                } else {
                    throw new RuntimeException("Expected ',' or '}' at pos " + pos + ", found '" + c + "'");
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            consume('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (src.charAt(pos) == ']') {
                consume(']');
                return list;
            }
            while (true) {
                skipWhitespace();
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                char c = src.charAt(pos);
                if (c == ']') {
                    consume(']');
                    break;
                } else if (c == ',') {
                    consume(',');
                } else {
                    throw new RuntimeException("Expected ',' or ']' at pos " + pos + ", found '" + c + "'");
                }
            }
            return list;
        }

        private String parseString() {
            consume('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '"') {
                    consume('"');
                    return sb.toString();
                } else if (c == '\\') {
                    consume('\\');
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) {
                                throw new RuntimeException("Invalid unicode escape");
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new RuntimeException("Unknown escape char '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new RuntimeException("Expected boolean at pos " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new RuntimeException("Expected null at pos " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E' || src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            String numStr = src.substring(start, pos);
            if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                return Double.parseDouble(numStr);
            } else {
                try {
                    return Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    return Long.parseLong(numStr);
                }
            }
        }

        private void consume(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new RuntimeException("Expected '" + expected + "' at pos " + pos + ", found '" + (pos < src.length() ? src.charAt(pos) : "EOF") + "'");
            }
            pos++;
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }
    }

    // ==========================================
    // 2. Type Inference and Code Generator
    // ==========================================
    public static class ClassSchema {
        public final String name;
        public final Map<String, FieldSchema> fields = new LinkedHashMap<>();

        public ClassSchema(String name) {
            this.name = name;
        }
    }

    public static class FieldSchema {
        public final String jsonKey;
        public final String javaName;
        public TypeSchema type;

        public FieldSchema(String jsonKey, String javaName, TypeSchema type) {
            this.jsonKey = jsonKey;
            this.javaName = javaName;
            this.type = type;
        }
    }

    public static class TypeSchema {
        public enum Kind {
            INT("int", "Integer"),
            LONG("long", "Long"),
            DOUBLE("double", "Double"),
            BOOLEAN("boolean", "Boolean"),
            STRING("String", "String"),
            LIST("java.util.List", "java.util.List"),
            RECORD(null, null),
            OBJECT("Object", "Object"),
            NULL("Object", "Object");

            public final String primitiveName;
            public final String boxedName;

            Kind(String primitiveName, String boxedName) {
                this.primitiveName = primitiveName;
                this.boxedName = boxedName;
            }
        }

        public Kind kind;
        public ClassSchema recordSchema;     // Non-null if kind == RECORD
        public TypeSchema listElementType;  // Non-null if kind == LIST
        public boolean nullable = false;

        public TypeSchema(Kind kind) {
            this.kind = kind;
        }

        public String getJavaType(boolean forceBoxed) {
            if (kind == Kind.RECORD) {
                return recordSchema.name;
            }
            if (kind == Kind.LIST) {
                return "java.util.List<" + listElementType.getJavaType(true) + ">";
            }
            return (nullable || forceBoxed) ? kind.boxedName : kind.primitiveName;
        }
    }

    private final Map<String, ClassSchema> generatedRecords = new LinkedHashMap<>();
    private int recordNameCounter = 1;

    public String generateJavaSource(Object jsonRoot, String packageName, String className) {
        if (!(jsonRoot instanceof Map)) {
            throw new IllegalArgumentException("JSON root must be a JSON Object (Map)");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> rootMap = (Map<String, Object>) jsonRoot;
        ClassSchema rootSchema = inferClassSchema(className, rootMap);

        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.trim().isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        // Top level class declaration (final class or record? Let's use final class that contains nested records, or we can make the top level a record itself.
        // Making the top level class a Record is extremely clean and holds the values in its static fields or a single static final instance.
        // Let's generate a final class with static records and static final fields, so users can access them as AppConfig.width or AppConfig.window.x().
        sb.append("public final class ").append(className).append(" {\n\n");

        // Generate nested record definitions
        for (ClassSchema recordSchema : generatedRecords.values()) {
            if (recordSchema == rootSchema) continue;
            appendRecordDefinition(sb, recordSchema, "    ");
        }

        // Generate root's fields as static final variables in the top-level class
        for (FieldSchema field : rootSchema.fields.values()) {
            sb.append("    public static final ").append(field.type.getJavaType(false)).append(" ").append(field.javaName).append(" = ");
            appendValueLiteral(sb, rootMap.get(field.jsonKey), field.type);
            sb.append(";\n");
        }

        sb.append("\n    private ").append(className).append("() {}\n");
        sb.append("}\n");

        return sb.toString();
    }

    private void appendRecordDefinition(StringBuilder sb, ClassSchema schema, String indent) {
        sb.append(indent).append("public record ").append(schema.name).append("(\n");
        int size = schema.fields.size();
        int idx = 0;
        for (FieldSchema field : schema.fields.values()) {
            sb.append(indent).append("    ").append(field.type.getJavaType(false)).append(" ").append(field.javaName);
            if (++idx < size) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }
        sb.append(indent).append(") {}\n\n");
    }

    private void appendValueLiteral(StringBuilder sb, Object value, TypeSchema type) {
        if (value == null) {
            sb.append("null");
            return;
        }

        switch (type.kind) {
            case INT:
                sb.append(value);
                break;
            case LONG:
                sb.append(value).append("L");
                break;
            case DOUBLE:
                sb.append(value);
                break;
            case BOOLEAN:
                sb.append(value);
                break;
            case STRING:
                sb.append("\"").append(escapeJavaString((String) value)).append("\"");
                break;
            case LIST:
                sb.append("java.util.List.of(");
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(", ");
                    appendValueLiteral(sb, list.get(i), type.listElementType);
                }
                sb.append(")");
                break;
            case RECORD:
                sb.append("new ").append(type.recordSchema.name).append("(");
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                int idx = 0;
                int size = type.recordSchema.fields.size();
                for (FieldSchema field : type.recordSchema.fields.values()) {
                    if (idx > 0) sb.append(", ");
                    appendValueLiteral(sb, map.get(field.jsonKey), field.type);
                    idx++;
                }
                sb.append(")");
                break;
            default:
                sb.append("null");
        }
    }

    private static String escapeJavaString(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private ClassSchema inferClassSchema(String className, Map<String, Object> map) {
        // Reuse generated records with the exact same structure if possible
        ClassSchema schema = new ClassSchema(className);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            String javaName = toJavaIdentifier(key);
            TypeSchema type = inferType(key, entry.getValue());
            schema.fields.put(key, new FieldSchema(key, javaName, type));
        }

        generatedRecords.put(className, schema);
        return schema;
    }

    private TypeSchema inferType(String key, Object value) {
        if (value == null) {
            return new TypeSchema(TypeSchema.Kind.NULL);
        }

        if (value instanceof Integer) {
            return new TypeSchema(TypeSchema.Kind.INT);
        } else if (value instanceof Long) {
            return new TypeSchema(TypeSchema.Kind.LONG);
        } else if (value instanceof Double) {
            return new TypeSchema(TypeSchema.Kind.DOUBLE);
        } else if (value instanceof Boolean) {
            return new TypeSchema(TypeSchema.Kind.BOOLEAN);
        } else if (value instanceof String) {
            return new TypeSchema(TypeSchema.Kind.STRING);
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            String subRecordName = toClassName(key);
            if (generatedRecords.containsKey(subRecordName)) {
                // Resolve name collision by suffixing
                subRecordName = subRecordName + "_" + (recordNameCounter++);
            }
            ClassSchema subSchema = inferClassSchema(subRecordName, map);
            TypeSchema t = new TypeSchema(TypeSchema.Kind.RECORD);
            t.recordSchema = subSchema;
            return t;
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            TypeSchema listType = new TypeSchema(TypeSchema.Kind.LIST);
            if (list.isEmpty()) {
                listType.listElementType = new TypeSchema(TypeSchema.Kind.OBJECT);
                return listType;
            }

            // Infer and merge types of all elements in the list
            TypeSchema elementMergedType = null;
            for (Object item : list) {
                TypeSchema itemType = inferType(singularize(key), item);
                if (elementMergedType == null) {
                    elementMergedType = itemType;
                } else {
                    elementMergedType = mergeTypes(elementMergedType, itemType);
                }
            }
            listType.listElementType = elementMergedType;
            return listType;
        }

        return new TypeSchema(TypeSchema.Kind.OBJECT);
    }

    private TypeSchema mergeTypes(TypeSchema t1, TypeSchema t2) {
        if (t1.kind == TypeSchema.Kind.NULL) return t2;
        if (t2.kind == TypeSchema.Kind.NULL) {
            t1.nullable = true;
            return t1;
        }

        if (t1.kind == t2.kind) {
            if (t1.kind == TypeSchema.Kind.RECORD) {
                // Merge ClassSchemas
                ClassSchema merged = mergeClassSchemas(t1.recordSchema, t2.recordSchema);
                TypeSchema res = new TypeSchema(TypeSchema.Kind.RECORD);
                res.recordSchema = merged;
                return res;
            }
            if (t1.kind == TypeSchema.Kind.LIST) {
                TypeSchema res = new TypeSchema(TypeSchema.Kind.LIST);
                res.listElementType = mergeTypes(t1.listElementType, t2.listElementType);
                return res;
            }
            t1.nullable = t1.nullable || t2.nullable;
            return t1;
        }

        // Numeric promotions
        if (isNumeric(t1.kind) && isNumeric(t2.kind)) {
            TypeSchema.Kind common = getCommonNumeric(t1.kind, t2.kind);
            TypeSchema res = new TypeSchema(common);
            res.nullable = t1.nullable || t2.nullable;
            return res;
        }

        // Incompatible types: fall back to Object
        TypeSchema res = new TypeSchema(TypeSchema.Kind.OBJECT);
        res.nullable = true;
        return res;
    }

    private ClassSchema mergeClassSchemas(ClassSchema s1, ClassSchema s2) {
        String mergedName = s1.name;
        ClassSchema merged = new ClassSchema(mergedName);

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(s1.fields.keySet());
        allKeys.addAll(s2.fields.keySet());

        for (String key : allKeys) {
            FieldSchema f1 = s1.fields.get(key);
            FieldSchema f2 = s2.fields.get(key);

            if (f1 != null && f2 != null) {
                TypeSchema mergedType = mergeTypes(f1.type, f2.type);
                merged.fields.put(key, new FieldSchema(key, f1.javaName, mergedType));
            } else if (f1 != null) {
                f1.type.nullable = true;
                merged.fields.put(key, f1);
            } else {
                f2.type.nullable = true;
                merged.fields.put(key, f2);
            }
        }

        generatedRecords.put(mergedName, merged);
        if (!s1.name.equals(s2.name)) {
            generatedRecords.remove(s2.name);
        }
        return merged;
    }

    private static boolean isNumeric(TypeSchema.Kind k) {
        return k == TypeSchema.Kind.INT || k == TypeSchema.Kind.LONG || k == TypeSchema.Kind.DOUBLE;
    }

    private static TypeSchema.Kind getCommonNumeric(TypeSchema.Kind k1, TypeSchema.Kind k2) {
        if (k1 == TypeSchema.Kind.DOUBLE || k2 == TypeSchema.Kind.DOUBLE) return TypeSchema.Kind.DOUBLE;
        if (k1 == TypeSchema.Kind.LONG || k2 == TypeSchema.Kind.LONG) return TypeSchema.Kind.LONG;
        return TypeSchema.Kind.INT;
    }

    private static String toJavaIdentifier(String str) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                nextUpper = true;
            } else if (Character.isJavaIdentifierPart(c)) {
                if (sb.length() == 0) {
                    if (Character.isJavaIdentifierStart(c)) {
                        sb.append(Character.toLowerCase(c));
                    } else {
                        sb.append('_').append(c);
                    }
                } else {
                    if (nextUpper) {
                        sb.append(Character.toUpperCase(c));
                        nextUpper = false;
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        String id = sb.toString();
        return isJavaKeyword(id) ? id + "_" : id;
    }

    private static String toClassName(String str) {
        String id = toJavaIdentifier(str);
        if (id.isEmpty()) return "Item";
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private static String singularize(String str) {
        if (str.endsWith("ies") && str.length() > 3) {
            return str.substring(0, str.length() - 3) + "y";
        }
        if (str.endsWith("s") && !str.endsWith("ss") && str.length() > 1) {
            return str.substring(0, str.length() - 1);
        }
        return str;
    }

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally",
        "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
        "native", "new", "null", "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "true",
        "try", "void", "volatile", "while", "record", "yield"
    ));

    private static boolean isJavaKeyword(String str) {
        return JAVA_KEYWORDS.contains(str);
    }

    // ==========================================
    // 3. CLI Harness
    // ==========================================
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java JJsonC <input.json> <output_dir> <class_name> [package_name]");
            System.exit(1);
        }

        String inputJsonPath = args[0];
        String outputDirPath = args[1];
        String className = args[2];
        String packageName = args.length > 3 ? args[3] : "";

        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(inputJsonPath)), "UTF-8");
            Object root = new JsonParser(jsonContent).parse();
            JJsonC compiler = new JJsonC();
            String javaSource = compiler.generateJavaSource(root, packageName, className);

            File outDir = new File(outputDirPath);
            if (!outDir.exists()) {
                outDir.mkdirs();
            }

            File outFile = new File(outDir, className + ".java");
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"))) {
                out.print(javaSource);
            }
            System.out.println("Generated compiled JSON class successfully at: " + outFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Compilation failed:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
