package com.youzan.maven.plugin.annotation;

import org.springframework.util.StringUtils;

import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;

/**
 * @author yiji@apache.org
 */
public class DefaultClassMetadata implements ClassMetadata {

    private final Class<?> introspectedClass;

    /**
     * Create a new DefaultClassMetadata wrapper for the given Class.
     *
     * @param introspectedClass the Class to introspect
     */
    public DefaultClassMetadata(Class<?> introspectedClass) {
        this.introspectedClass = introspectedClass;
    }

    /**
     * Return the underlying Class.
     */
    public final Class<?> getIntrospectedClass() {
        return this.introspectedClass;
    }


    @Override
    public String getClassName() {
        return this.introspectedClass.getName();
    }

    @Override
    public boolean isInterface() {
        return this.introspectedClass.isInterface();
    }

    @Override
    public boolean isAnnotation() {
        return this.introspectedClass.isAnnotation();
    }

    @Override
    public boolean isAbstract() {
        return Modifier.isAbstract(this.introspectedClass.getModifiers());
    }

    @Override
    public boolean isConcrete() {
        return !(isInterface() || isAbstract());
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(this.introspectedClass.getModifiers());
    }

    @Override
    public boolean isIndependent() {
        return (!hasEnclosingClass() ||
                (this.introspectedClass.getDeclaringClass() != null &&
                        Modifier.isStatic(this.introspectedClass.getModifiers())));
    }

    @Override
    public boolean hasEnclosingClass() {
        return (this.introspectedClass.getEnclosingClass() != null);
    }

    @Override
    public String getEnclosingClassName() {
        Class<?> enclosingClass = this.introspectedClass.getEnclosingClass();
        return (enclosingClass != null ? enclosingClass.getName() : null);
    }

    @Override
    public boolean hasSuperClass() {
        return (this.introspectedClass.getSuperclass() != null);
    }

    @Override
    public String getSuperClassName() {
        Class<?> superClass = this.introspectedClass.getSuperclass();
        return (superClass != null ? superClass.getName() : null);
    }

    @Override
    public String[] getInterfaceNames() {
        Class<?>[] interfaces = this.introspectedClass.getInterfaces();
        String[] names = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            names[i] = interfaces[i].getName();
        }
        return names;
    }

    @Override
    public String[] getMemberClassNames() {
        LinkedHashSet<String> memberClassNames = new LinkedHashSet<String>(4);
        for (Class<?> nestedClass : this.introspectedClass.getDeclaredClasses()) {
            memberClassNames.add(nestedClass.getName());
        }
        return StringUtils.toStringArray(memberClassNames);
    }

}