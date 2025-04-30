package com.sandflow.smpte.mxf;

import java.io.OutputStream;

import com.sandflow.smpte.mxf.types.FileDescriptor;

public class StreamingWriter {

  public enum EssenceWrapping {
    CLIP, FRAME;
  }

  public record EssenceContainerInfo(
      FileDescriptor[] descriptors,
      EssenceWrapping wrapping) {
  }

  public record Configuration(EssenceContainerInfo[] containers) {
  }

  StreamingWriter(OutputStream os) {
    PartitionPack pp = new PartitionPack();

    
  
  }


}
