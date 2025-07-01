package com.sandflow.smpte.mxf.helpers;

import java.time.LocalDateTime;

import com.sandflow.smpte.mxf.types.Identification;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UUID;

public class IdentificationHelper {
  final static AUID APPLICATION_PRODUCT_ID = AUID.fromURN("urn:uuid:5c1a9040-d234-41f1-86f3-5a78991f5b9e");

  public static Identification makeIdentification() {
    Identification identification = new Identification();

    identification.InstanceID = UUID.fromRandom();
    identification.GenerationID = new AUID(UUID.fromRandom());
    identification.ApplicationVersionString = "1.0.0";
    identification.ApplicationSupplierName = "Sandflow Consulting, LLC";
    identification.ApplicationName = "regmxflib";
    identification.ApplicationProductID = APPLICATION_PRODUCT_ID;
    identification.FileModificationDate = LocalDateTime.now();

    return identification;
  }
}
