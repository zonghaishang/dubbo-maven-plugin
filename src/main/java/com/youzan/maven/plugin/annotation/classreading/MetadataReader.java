package com.youzan.maven.plugin.annotation.classreading;

import com.youzan.maven.plugin.annotation.AnnotationMetadata;
import com.youzan.maven.plugin.annotation.ClassMetadata;

import java.io.File;

/**
 * @author yiji@apache.org
 */
public interface MetadataReader {

    /**
     * Return the resource reference for the class file.
     */
    File getResource();

    /**
     * Read basic class metadata for the underlying class.
     */
    ClassMetadata getClassMetadata();

    /**
     * Read full annotation metadata for the underlying class,
     * including metadata for annotated methods.
     */
    AnnotationMetadata getAnnotationMetadata();

}
