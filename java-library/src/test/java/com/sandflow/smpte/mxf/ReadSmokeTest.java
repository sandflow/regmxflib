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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sandflow.smpte.util.FileRandomAccessInputSource;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

/**
 * Reads MXF files, e.g. files produced by third-party tools (asdcplib, bmx,
 * ffmpeg), using both the streaming and the random access readers.
 *
 * <pre>
 * ReadSmokeTest &lt;file-or-directory&gt;...
 * </pre>
 *
 * Every MXF file in the specified directories is read, and the program fails if
 * any of them cannot be read without errors.
 */
public class ReadSmokeTest {

  /* collects ERROR and FATAL events, and lets processing continue */
  static class EventCollector implements EventHandler {
    final List<String> errors = new ArrayList<>();

    @Override
    public boolean handle(Event evt) {
      if (evt.getSeverity() == Event.Severity.ERROR || evt.getSeverity() == Event.Severity.FATAL) {
        errors.add(evt.getCode() + " " + evt.getMessage());
      }
      return true;
    }

    void checkNoErrors() {
      if (!errors.isEmpty()) {
        throw new IllegalStateException("Errors reported:\n  " + String.join("\n  ", errors));
      }
    }
  }

  @FunctionalInterface
  interface Reader {
    void read(File f) throws Exception;
  }

  /**
   * Reads the specified MXF files, and the MXF files in the specified
   * directories.
   *
   * @param args Files and directories to read
   * @throws Exception If no MXF file is found or if any of them cannot be read
   */
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("Usage: ReadSmokeTest <file-or-directory>...");
    }

    List<File> files = new ArrayList<>();
    for (String arg : args) {
      File f = new File(arg);
      if (f.isDirectory()) {
        File[] dirFiles = f.listFiles((d, n) -> n.endsWith(".mxf"));
        if (dirFiles != null) {
          Arrays.sort(dirFiles);
          files.addAll(Arrays.asList(dirFiles));
        }
      } else if (f.isFile()) {
        files.add(f);
      } else {
        throw new IllegalArgumentException("No such file or directory: " + arg);
      }
    }

    if (files.isEmpty()) {
      throw new IllegalArgumentException("No MXF files found in " + String.join(", ", args));
    }

    int failures = 0;
    for (File f : files) {
      failures += run(f, "streaming", ReadSmokeTest::readStreaming);
      failures += run(f, "random access", ReadSmokeTest::readRandomAccess);
    }

    System.out.printf("%d files read, %d failures%n", files.size(), failures);

    if (failures > 0) {
      throw new IllegalStateException(failures + " read failure(s)");
    }
  }

  static int run(File f, String readerName, Reader reader) {
    try {
      reader.read(f);
      System.out.printf("PASS %s (%s)%n", f.getName(), readerName);
      return 0;
    } catch (Throwable t) {
      System.out.printf("FAIL %s (%s): %s%n", f.getName(), readerName, t);
      return 1;
    }
  }

  static void check(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  /* reads the stream to its end and returns the number of bytes read */
  static long drain(InputStream is) throws Exception {
    byte[] buffer = new byte[65536];
    long count = 0;
    int n;
    while ((n = is.read(buffer)) >= 0) {
      count += n;
    }
    return count;
  }

  static void readStreaming(File f) throws Exception {
    EventCollector ec = new EventCollector();

    try (InputStream is = new BufferedInputStream(new FileInputStream(f))) {
      StreamingFileInfo info = new StreamingFileInfo(is, ec);
      GCEssenceTracks tracks = new GCEssenceTracks(info.getPreface());
      check(tracks.getTrackCount() > 0, "No essence tracks found");

      StreamingReader sr = new StreamingReader(is, ec);
      long count = 0;
      while (sr.nextElement()) {
        /* read the entire payload to make sure it is all there */
        long length = sr.getElementLength();
        check(drain(sr) == length, "Truncated essence element " + count);
        count++;
      }
      sr.close();

      check(count > 0, "No essence elements found");
    }

    ec.checkNoErrors();
  }

  static void readRandomAccess(File f) throws Exception {
    EventCollector ec = new EventCollector();

    try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
      FileRandomAccessInputSource rais = new FileRandomAccessInputSource(raf);

      RandomAccessFileInfo fi = new RandomAccessFileInfo(rais, ec);
      GCEssenceTracks tracks = new GCEssenceTracks(fi.getPreface());
      check(tracks.getTrackCount() > 0, "No essence tracks found");

      long euCount = fi.getEUCount();
      check(euCount > 0, "The number of edit units is unknown");

      /*
       * The file is clip-wrapped if all edit units fall within the first essence
       * element
       */
      rais.position(fi.ecToFilePositions(0));
      MXFDataInput mis = new MXFDataInput(rais);
      mis.readAUID();
      long firstElementLength = mis.readBERLength();
      long firstElementEnd = rais.position() - fi.ecToFilePositions(0) + firstElementLength;
      boolean isClipWrapped = euCount > 1 && fi.euToECPosition(euCount - 1) < firstElementEnd;

      if (isClipWrapped) {
        ClipReader cr = new ClipReader(fi, rais);
        for (long eu : new long[] { 0, euCount / 2, euCount - 1 }) {
          cr.seek(eu);
          check(cr.getRemainingElementBytes() > 0, "No essence bytes at edit unit " + eu);
          check(cr.read() >= 0, "Cannot read essence at edit unit " + eu);
        }
        check(cr.getEUCount() == euCount, "Inconsistent edit unit count");
        checkSeekRejected(() -> cr.seek(euCount), euCount);
        cr.close();
      } else {
        FrameReader fr = new FrameReader(fi, rais);
        for (long eu = 0; eu < euCount; eu++) {
          fr.seek(eu);
          check(fr.nextElement(), "No essence element at edit unit " + eu);
          long length = fr.getElementLength();
          check(drain(fr) == length, "Truncated essence element at edit unit " + eu);
        }
        check(fr.getSize() == euCount, "Inconsistent edit unit count");
        checkSeekRejected(() -> fr.seek(euCount), euCount);
        fr.close();
      }

      GenericStreamReader gsr = new GenericStreamReader(fi, rais);
      for (long sid : fi.getGenericStreams()) {
        gsr.seek(sid);
        check(gsr.getElementLength() > 0, "Empty generic stream " + sid);
      }
      gsr.close();
    }

    ec.checkNoErrors();
  }

  @FunctionalInterface
  interface Seek {
    void seek() throws Exception;
  }

  /* checks that seeking past the last edit unit is rejected */
  static void checkSeekRejected(Seek seek, long euCount) throws Exception {
    try {
      seek.seek();
    } catch (IllegalArgumentException e) {
      return;
    }
    throw new IllegalStateException("Seeking to edit unit " + euCount + " was not rejected");
  }
}
