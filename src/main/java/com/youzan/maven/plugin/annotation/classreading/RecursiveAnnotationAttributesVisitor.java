package com.youzan.maven.plugin.annotation.classreading;

import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * @author yiji@apache.org
 */
public class RecursiveAnnotationAttributesVisitor extends AbstractRecursiveAnnotationVisitor {
    protected final String annotationType;


    public RecursiveAnnotationAttributesVisitor(
            String annotationType, AnnotationAttributes attributes, ClassLoader classLoader) {

        super(classLoader, attributes);
        this.annotationType = annotationType;
    }


    @Override
    public void visitEnd() {
        AnnotationUtils.registerDefaultValues(this.attributes);
    }

}
