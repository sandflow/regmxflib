package com.sandflow.smpte.mxf;

import com.sandflow.smpte.util.UUID;

public interface MXFInputContext {
  Set getSet(UUID uuid);
  
}