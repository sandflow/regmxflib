package com.sandflow.smpte.mxf.annotationprocessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MXFPropertyDefinition {
  String Identification();
  String Symbol();
  String Type();
  boolean isOptional();
  int LocalIdentification();
}