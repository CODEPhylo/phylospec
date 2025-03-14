package org.phylospec.annotations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for PhyloSpec annotations.
 */
public class PhyloSpecTest {

    @Test
    public void testPhyloSpecAnnotation() {
        // Verify that annotation properties can be accessed
        PhyloSpec annotation = TestModel.class.getAnnotation(PhyloSpec.class);
        
        assertNotNull(annotation, "PhyloSpec annotation should be present");
        assertEquals("TestModel", annotation.value(), "Component name should match");
        assertEquals(PhyloSpec.Category.FUNCTION, annotation.category(), "Category should match");
        assertEquals(PhyloSpec.Role.SUBSTITUTION_MODEL, annotation.role(), "Role should match");
    }
    
    @Test
    public void testPhyloParamAnnotation() throws NoSuchFieldException {
        // Get the field with the PhyloParam annotation
        PhyloParam annotation = TestModel.class.getDeclaredField("parameter").getAnnotation(PhyloParam.class);
        
        assertNotNull(annotation, "PhyloParam annotation should be present");
        assertEquals("testParam", annotation.value(), "Parameter name should match");
        assertTrue(annotation.required(), "Parameter should be required");
        assertEquals("1.0", annotation.defaultValue(), "Default value should match");
    }
    
    // Simple test class with annotations
    @PhyloSpec(value = "TestModel", category = PhyloSpec.Category.FUNCTION, role = PhyloSpec.Role.SUBSTITUTION_MODEL)
    private static class TestModel {
        @PhyloParam(value = "testParam", defaultValue = "1.0")
        private double parameter;
        
        public double getParameter() {
            return parameter;
        }
        
        public void setParameter(double value) {
            this.parameter = value;
        }
    }
}