package com.sandflow.smpte.mxf;

import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public interface MXFOutputContext {
  UUID getPackageInstanceID(UMID packageID);
  void putSet(Set set);
}