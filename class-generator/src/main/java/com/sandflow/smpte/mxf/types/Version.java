package com.sandflow.smpte.mxf.types;

public class Version {
  private int major;
  private int minor;

  public Version(int major, int minor) {
    this.major = major;
    this.minor = minor;
  }
  public int getMajor() {
    return major;
  }
  public void setMajor(int major) {
    if (major > 255)
      throw new IllegalArgumentException("Major version must between 0 and 255");
  
    this.major = major;
  }
  public int getMinor() {
    return minor;
  }
  public void setMinor(int minor) {
    this.minor = minor;
  }

}
