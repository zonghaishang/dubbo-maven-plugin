package com.youzan.maven.plugin.annotation.classreading;

import com.youzan.maven.plugin.annotation.AnnotationMetadata;
import com.youzan.maven.plugin.annotation.ClassMetadata;

import org.springframework.asm.ClassReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author yiji@apache.org
 */
public final class SimpleMetadataReader implements MetadataReader {

    private final File file;

    private final ClassMetadata classMetadata;

    private final AnnotationMetadata annotationMetadata;

    public SimpleMetadataReader(File file, ClassLoader classLoader) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(file));
        ClassReader classReader;
        try {
            classReader = new ClassReader(is);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("ASM ClassReader failed to parse class file - " +
                    "probably due to a new Java class file version that isn't supported yet: " + file, ex);
        } finally {
            is.close();
        }

        AnnotationMetadataReadingVisitor visitor = new AnnotationMetadataReadingVisitor(classLoader);
        classReader.accept(visitor, ClassReader.SKIP_DEBUG);

        this.annotationMetadata = visitor;
        // (since AnnotationMetadataReadingVisitor extends ClassMetadataReadingVisitor)
        this.classMetadata = visitor;
        this.file = file;
    }


    @Override
    public File getResource() {
        return this.file;
    }

    @Override
    public ClassMetadata getClassMetadata() {
        return this.classMetadata;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadata;
    }

}
