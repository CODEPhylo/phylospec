package org.phylospec.converters;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import org.phylospec.ast.Stmt;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public record JSONModel(List<Stmt> statements, String phyloSpecModel) {

    /**
     * Returns the JSON schema as a string corresponding to the {@link JSONModel} POJO.
     */
    public static String getJSONSchema() {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.forTypesInGeneral().withSubtypeResolver(new InnerClassesResolver());
        configBuilder.forFields().withDescriptionResolver(field ->
                field.getAnnotation(JsonPropertyDescription.class) != null ?
                        field.getAnnotation(JsonPropertyDescription.class).value() :
                        null
        );
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
        JsonNode jsonSchema = generator.generateSchema(JSONModel.class);
        return jsonSchema.toPrettyString();
    }

    /**
     * This class finds all static inner classes within the `org.phylospec.ast` package. This is used to add all
     * {@link org.phylospec.ast.Expr}, {@link org.phylospec.ast.Stmt}, and {@link org.phylospec.ast.AstType} subclasses
     * to the JSON schema.
     */
    static class InnerClassesResolver implements SubtypeResolver {

        @Override
        public List<ResolvedType> findSubtypes(ResolvedType declaredType, SchemaGenerationContext context) {
            TypeContext typeContext = context.getTypeContext();
            if (declaredType.getErasedType().getPackageName().startsWith("org.phylospec.ast")) {
                return Arrays.stream(declaredType.getErasedType().getDeclaredClasses())
                        .map(x -> typeContext.resolveSubtype(declaredType, x))
                        .collect(Collectors.toList());
            }
            return null;
        }
    }
}