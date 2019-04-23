package com.youzan.maven.plugin.annotation;

import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author yiji@apache.org
 */
public class DefaultAnnotationMetadata extends DefaultClassMetadata implements AnnotationMetadata {

    private final Annotation[] annotations;

    private final boolean nestedAnnotationsAsMap;


    /**
     * Create a new {@code DefaultAnnotationMetadata} wrapper for the given Class.
     *
     * @param introspectedClass the Class to introspect
     * @see #DefaultAnnotationMetadata(Class, boolean)
     */
    public DefaultAnnotationMetadata(Class<?> introspectedClass) {
        this(introspectedClass, false);
    }

    /**
     * Create a new {@link DefaultAnnotationMetadata} wrapper for the given Class,
     * providing the option to return any nested annotations or annotation arrays in the
     * form of {@link org.springframework.core.annotation.AnnotationAttributes} instead
     * of actual {@link Annotation} instances.
     *
     * @param introspectedClass      the Class to introspect
     * @param nestedAnnotationsAsMap return nested annotations and annotation arrays as
     *                               {@link org.springframework.core.annotation.AnnotationAttributes} for compatibility
     *                               with ASM-based {@link AnnotationMetadata} implementations
     */
    public DefaultAnnotationMetadata(Class<?> introspectedClass, boolean nestedAnnotationsAsMap) {
        super(introspectedClass);
        this.annotations = introspectedClass.getAnnotations();
        this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
    }


    @Override
    public Set<String> getAnnotationTypes() {
        Set<String> types = new LinkedHashSet<String>();
        for (Annotation ann : this.annotations) {
            types.add(ann.annotationType().getName());
        }
        return types;
    }

    @Override
    public Set<String> getMetaAnnotationTypes(String annotationName) {
        return (this.annotations.length > 0 ?
                AnnotatedElementUtils.getMetaAnnotationTypes(getIntrospectedClass(), annotationName) : null);
    }

    @Override
    public boolean hasAnnotation(String annotationName) {
        for (Annotation ann : this.annotations) {
            if (ann.annotationType().getName().equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasMetaAnnotation(String annotationName) {
        return (this.annotations.length > 0 &&
                AnnotatedElementUtils.hasMetaAnnotationTypes(getIntrospectedClass(), annotationName));
    }

    @Override
    public boolean isAnnotated(String annotationName) {
        return (this.annotations.length > 0 &&
                AnnotatedElementUtils.isAnnotated(getIntrospectedClass(), annotationName));
    }

    @Override
    public Map<String, Object> getAnnotationAttributes(String annotationName) {
        return getAnnotationAttributes(annotationName, false);
    }

    @Override
    public Map<String, Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
        return (this.annotations.length > 0 ? AnnotatedElementUtils.getMergedAnnotationAttributes(
                getIntrospectedClass(), annotationName, classValuesAsString, this.nestedAnnotationsAsMap) : null);
    }

    @Override
    public Map<String, Object> getAllAnnotationAttributes(String annotationName) {
        return getAllAnnotationAttributes(annotationName, false);
    }

    @Override
    public Map<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
        return (this.annotations.length > 0 ? (Map)AnnotatedElementUtils.getAllAnnotationAttributes(
                getIntrospectedClass(), annotationName, classValuesAsString, this.nestedAnnotationsAsMap) : null);
    }

}
