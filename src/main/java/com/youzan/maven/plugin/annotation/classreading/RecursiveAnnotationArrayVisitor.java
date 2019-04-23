package com.youzan.maven.plugin.annotation.classreading;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.Type;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yiji@apache.org
 */
public class RecursiveAnnotationArrayVisitor extends AbstractRecursiveAnnotationVisitor {

    private final String attributeName;

    private final List<AnnotationAttributes> allNestedAttributes = new ArrayList<AnnotationAttributes>();


    public RecursiveAnnotationArrayVisitor(
            String attributeName, AnnotationAttributes attributes, ClassLoader classLoader) {

        super(classLoader, attributes);
        this.attributeName = attributeName;
    }


    @Override
    public void visit(String attributeName, Object attributeValue) {
        Object value = attributeValue;
        Object existingValue = this.attributes.get(this.attributeName);
        if (existingValue != null) {
            value = ObjectUtils.addObjectToArray((Object[]) existingValue, value);
        } else {
            Class<?> arrayClass = value.getClass();
            if (Enum.class.isAssignableFrom(arrayClass)) {
                while (arrayClass.getSuperclass() != null && !arrayClass.isEnum()) {
                    arrayClass = arrayClass.getSuperclass();
                }
            }
            Object[] newArray = (Object[]) Array.newInstance(arrayClass, 1);
            newArray[0] = value;
            value = newArray;
        }
        this.attributes.put(this.attributeName, value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String attributeName, String asmTypeDescriptor) {
        String annotationType = Type.getType(asmTypeDescriptor).getClassName();
        AnnotationAttributes nestedAttributes = new AnnotationAttributes(annotationType, this.classLoader);
        this.allNestedAttributes.add(nestedAttributes);
        return new RecursiveAnnotationAttributesVisitor(annotationType, nestedAttributes, this.classLoader);
    }

    @Override
    public void visitEnd() {
        if (!this.allNestedAttributes.isEmpty()) {
            this.attributes.put(this.attributeName,
                    this.allNestedAttributes.toArray(new AnnotationAttributes[this.allNestedAttributes.size()]));
        }
    }

}
