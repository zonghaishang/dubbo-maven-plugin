package com.youzan.maven.plugin.annotation.classreading;


import com.youzan.maven.plugin.annotation.AnnotationMetadata;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MethodMetadataReadingVisitor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author yiji@apache.org
 */
public class AnnotationMetadataReadingVisitor extends ClassMetadataReadingVisitor implements AnnotationMetadata {
    protected final ClassLoader classLoader;

    protected final Set<String> annotationSet = new LinkedHashSet<String>(4);

    protected final Map<String, Set<String>> metaAnnotationMap = new LinkedHashMap<String, Set<String>>(4);

    /**
     * Declared as a {@link LinkedMultiValueMap} instead of a {@link MultiValueMap}
     * to ensure that the hierarchical ordering of the entries is preserved.
     *
     * @see AnnotationReadingVisitorUtils#getMergedAnnotationAttributes
     */
    protected final LinkedMultiValueMap<String, AnnotationAttributes> attributesMap =
            new LinkedMultiValueMap<String, AnnotationAttributes>(4);

    protected final Set<MethodMetadata> methodMetadataSet = new LinkedHashSet<MethodMetadata>(4);


    public AnnotationMetadataReadingVisitor(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // Skip bridge methods - we're only interested in original annotation-defining user methods.
        // On JDK 8, we'd otherwise run into double detection of the same annotated method...
        if ((access & Opcodes.ACC_BRIDGE) != 0) {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
        return new MethodMetadataReadingVisitor(name, access, getClassName(),
                Type.getReturnType(desc).getClassName(), this.classLoader, this.methodMetadataSet);
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc, boolean visible) {
        String className = Type.getType(desc).getClassName();
        this.annotationSet.add(className);
        return new AnnotationAttributesReadingVisitor(
                className, this.attributesMap, this.metaAnnotationMap, this.classLoader);
    }


    @Override
    public Set<String> getAnnotationTypes() {
        return this.annotationSet;
    }

    @Override
    public Set<String> getMetaAnnotationTypes(String annotationName) {
        return this.metaAnnotationMap.get(annotationName);
    }

    @Override
    public boolean hasAnnotation(String annotationName) {
        return this.annotationSet.contains(annotationName);
    }

    @Override
    public boolean hasMetaAnnotation(String metaAnnotationType) {
        Collection<Set<String>> allMetaTypes = this.metaAnnotationMap.values();
        for (Set<String> metaTypes : allMetaTypes) {
            if (metaTypes.contains(metaAnnotationType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAnnotated(String annotationName) {
        return (!AnnotationUtils.isInJavaLangAnnotationPackage(annotationName) &&
                this.attributesMap.containsKey(annotationName));
    }

    @Override
    public AnnotationAttributes getAnnotationAttributes(String annotationName) {
        return getAnnotationAttributes(annotationName, false);
    }

    @Override
    public AnnotationAttributes getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
        AnnotationAttributes raw = AnnotationReadingVisitorUtils.getMergedAnnotationAttributes(
                this.attributesMap, this.metaAnnotationMap, annotationName);
        return AnnotationReadingVisitorUtils.convertClassValues(
                "class '" + getClassName() + "'", this.classLoader, raw, classValuesAsString);
    }

    @Override
    public Map<String, Object> getAllAnnotationAttributes(String annotationName) {
        return getAllAnnotationAttributes(annotationName, false);
    }

    @Override
    public Map<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
        MultiValueMap<String, Object> allAttributes = new LinkedMultiValueMap<String, Object>();
        List<AnnotationAttributes> attributes = this.attributesMap.get(annotationName);
        if (attributes == null) {
            return null;
        }
        for (AnnotationAttributes raw : attributes) {
            for (Map.Entry<String, Object> entry : AnnotationReadingVisitorUtils.convertClassValues(
                    "class '" + getClassName() + "'", this.classLoader, raw, classValuesAsString).entrySet()) {
                allAttributes.add(entry.getKey(), entry.getValue());
            }
        }
        return (Map) allAttributes;
    }
}
