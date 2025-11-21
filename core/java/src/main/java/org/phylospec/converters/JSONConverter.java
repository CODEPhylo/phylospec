package org.phylospec.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.phylospec.ast.*;
import org.phylospec.ast.transformers.ResolveComponentQualifiersTransformation;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;

import java.io.IOException;
import java.util.*;

public class JSONConverter {

    /**
     * Converts the given statements into an JSON file.
     */
    public static String convertToJSON(List<Stmt> statements, String originalScript) throws JsonConversionError, IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        ResolveComponentQualifiersTransformation transformation = new ResolveComponentQualifiersTransformation(componentLibraries);
        statements = transformation.transformStatements(statements);

        JSONModel model = new JSONModel(statements, originalScript);

        try {
            return mapper.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new JsonConversionError(e.getMessage());
        }
    }

    private record JSONModel(List<Stmt> statements, String phyloSpecModel) {
    }


    public static class JsonConversionError extends Exception {
        public JsonConversionError(String s) {
            super(s);
        }
    }

}
