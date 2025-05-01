package com.sandflow.smpte.mxf;

import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public interface MXFOutputContext {
  UUID getPackageInstanceID(UMID packageID);
  int getLocalTag(AUID auid);
  void putSet(Set set);
}