package com.sandflow.smpte.mxf;

interface ECIndex {
  long getECPosition(long editUnitIndex);

  long length();
}