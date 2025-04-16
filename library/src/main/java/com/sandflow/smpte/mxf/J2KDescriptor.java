package com.sandflow.smpte.mxf;

import com.sandflow.smpte.mxf.annotationprocessor.MXFClassDefinition;
import com.sandflow.smpte.mxf.annotationprocessor.MXFPropertyDefinition;

@MXFClassDefinition(
  Identification="urn:smpte:ul:060e2b34.01010102.06010104.01020000"
)
public class J2KDescriptor {
  static {
    System.err.println("J2KDescriptor.<static>()");
  }

  @MXFPropertyDefinition(
    Identification = "urn:smpte:ul:060e2b34.01010102.06010104.02010000",
    Symbol = "ComponentWidth",
    Type = "Integer",
    isOptional = false,
    LocalIdentification = 12454
  )
  int width;
}
