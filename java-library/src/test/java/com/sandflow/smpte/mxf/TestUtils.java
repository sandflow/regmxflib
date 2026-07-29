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

package com.sandflow.smpte.mxf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.stream.Stream;

import com.sandflow.smpte.mxf.types.Identification;
import com.sandflow.smpte.tools.RegMXFDump;
import com.sandflow.smpte.util.AUID;

public class TestUtils {

  public static void assertTextFilesEqual(File expected, File actual) throws IOException {
    try (Stream<String> refLines = Files.lines(expected.toPath());
        Stream<String> actualLines = Files.lines(actual.toPath())) {

      Iterator<String> refLine = refLines.iterator();
      Iterator<String> actualLine = actualLines.iterator();

      long cnt = 1;

      while (refLine.hasNext() && actualLine.hasNext()) {
        assertEquals(refLine.next(), actualLine.next(), "Mismatch at line " + cnt++);
      }

      if (refLine.hasNext()) {
        fail("Actual file is shorter than expected file. First missing line at " + cnt);
      }

      if (actualLine.hasNext()) {
        fail("Actual file is longer than expected file. First extra line at " + cnt);
      }
    }
  }

  final static AUID APPLICATION_PRODUCT_ID = AUID.fromURN("urn:uuid:5c1a9040-d234-41f1-86f3-5a78991f5b9e");

  public static Identification makeIdentification(UIDGenerator uidg) {
    Identification identification = new Identification();

    identification.InstanceID = uidg.generate(identification);
    identification.GenerationID = new AUID(identification.InstanceID);
    identification.ApplicationVersionString = "n/a";
    identification.ApplicationSupplierName = "regmxflib";
    identification.ApplicationName = "regmxflib unit tests";
    identification.ApplicationProductID = APPLICATION_PRODUCT_ID;
    identification.FileModificationDate = LocalDateTime.of(2025, 1, 1, 0, 0);

    return identification;
  }

  private static final String outputDirPath = "target/test-output/";

  static {
    File outputDir = new File(outputDirPath);
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }
  }

  public static File getOutputFile(String filename) {
    return new File(outputDirPath, filename);
  }

  private static final String referenceDirPath = "regmxf-ref-files/";

  public static void compareToReference(File mxfFile, final String refJsonFilename)
      throws FileNotFoundException, Exception, IOException, URISyntaxException {
    File tempFile = getOutputFile(refJsonFilename);

    FileOutputStream tos = new FileOutputStream(tempFile);

    RegMXFDump.dump(new FileInputStream(mxfFile), tos);

    tos.close();

    File rf = new File(
        ClassLoader.getSystemResource(referenceDirPath + refJsonFilename).toURI());

    assertTextFilesEqual(rf, tempFile);
  }

}