package com.sandflow.smpte.mxf;

import java.util.ArrayList;

class VBEIndex implements ECIndex {
  private ArrayList<Long> positions = new ArrayList<>();

  protected void addECPosition(long ecPosition) {
    this.positions.add(ecPosition);
  }

  @Override
  public long getECPosition(long editUnit) {
    if (editUnit >= this.positions.size()) {
      throw new IllegalArgumentException();
    }
    return (long) this.positions.get((int) editUnit);
  }

  @Override
  public long length() {
    return this.positions.size();
  }
}