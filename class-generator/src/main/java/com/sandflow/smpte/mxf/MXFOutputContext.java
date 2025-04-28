package com.sandflow.smpte.mxf;

import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public interface MXFOutputContext {
  UUID getPackageInstanceID(UMID packageID);
  long getLocalTag(AUID auid);
}