package com.sandflow.smpte.mxf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MXFPropertyDefinition {
  String Identification();
  String AdapterClass();
  boolean isOptional();
  int LocalIdentification();
}