/*
 * Copyright (c) Sandflow Consulting, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/**
* @author Pierre-Anthony Lemieux
*/

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
