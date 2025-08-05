# regmxflib


                                         _    _        _   
                                        | |  | |  o   | |  
     ,_     _    __,   _  _  _          | |  | |      | |  
    /  |   |/   /  |  / |/ |/ |   /\/   |/   |/   |   |/ \_
       |_/ |__/ \_/|/   |  |  |_/  /\_/ |__/ |__/ |_/  \_/ 
                  /|                    |\                 
                  \|                    |/                 

⚠️⚠️⚠️           CURRENTLY IN DEVELOPMENT          ⚠️⚠️⚠️

## Introduction

regmxflib is a pure Java library that:

- creates bindings between MXF header metadata classes and POJOs
  using the [SMPTE metadata registers](https://registry.smpte-ra.org/apps/pages/),
  allowing applications to remain up-to-date with recent additions to the MXF standard
  with minimal effort

- implements MXF reading and writing classes

- implements a simple tool that creates a JSON summary of an input MXF file

The following snippet illustrates the creation of an `RGBADescriptor` using the library:

    RGBADescriptor d = new RGBADescriptor();
    d.InstanceID = UUID.fromRandom();
    d.SampleRate = sampleRate;
    d.FrameLayout = LayoutType.FullFrame;
    d.StoredWidth = 640L;
    d.StoredHeight = 360L;
    d.DisplayF2Offset = 0;
    d.ImageAspectRatio = Fraction.of(640, 360);
    d.TransferCharacteristic = Labels.TransferCharacteristic_ITU709.asUL();
    d.PictureCompression = Labels.JPEG2000BroadcastContributionSingleTileProfileLevel5;
    d.ColorPrimaries = Labels.ColorPrimaries_ITU709.asUL();
    d.VideoLineMap = new Int32Array();
    d.VideoLineMap.add(0);
    d.VideoLineMap.add(0);
    d.ComponentMaxRef = 65535L;
    d.ComponentMinRef = 0L;
    d.ScanningDirection = ScanningDirectionType.ScanningDirection_LeftToRightTopToBottom;
    d.PixelLayout = new RGBALayout();
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompRed, (short) 16));
    ...

## Quick start

    mvn package
    java -cp library/target/library-1.0.0-alpha.1-jar-with-dependencies.jar com.sandflow.smpte.tools.RegMXFDump \
      library/src/test/resources/imps/imp_1/VIDEO_f031aa43-88c8-4de9-856f-904a33a78505.mxf > \
      library/target/test-output/VIDEO_f031aa43-88c8-4de9-856f-904a33a78505.json

## Organization

The library consists of 3 modules:

- `class-generator` generates POJO classes in <library/target/generated-sources> when
  compiling the library, using the register files at <library/src/main/resources>
- `library` holds the generated POJO classes and classes for reading and writing MXF files
- `common` holds classes that do not depend on the generated classes

_NOTE_: `common` is largely based on [regxmllib](https://github.com/sandflow/regxmllib) and
combining the two libraries is expected in the long run.

## MXF concepts

### Data model

At the highest level, an MXF file consists of:

- essence containers and generic streams, which each consists of a sequence of KLV packets containing essence or metadata;

- header metadata, which describes the contents of these essence containers and generic streams and contains additional metadata; and

- index tables, which allow temporal offset within these these essence containers and generic streams to be accessed in constant time and in any order.

### Physical structure

An MXF file is divided into partitions:

- two copies of the header metadata is typically stored in an MXF: at the beginning of the file (file header) and at the end of the file (file footer). The latter is assumed to contain the definitive information, once the entire file is has been written.

- each essence container and generic stream is partitioned into one or more partitions on KLV Triplet boundaries. Partitions from different essence containers and generic streams can be interleaved.

- Each partition that contains data from an essence container or generic stream that is indexed is followed by a partition that contains an index table for that partition.

- At the very end of the file, a random index pack (RIP) contains a table of contents of all the partitions contained in the file

### Essence wrapping

#### Frame-wrapping

In the case of frame-wrapping, each access unit of the essence or data stream is
wrapped into its own KLV triplet (called an _element_) and all elements that belong to
the same edit unit are grouped into a logical _content package_.

Index entries point to the first byte of the K of each element.

#### Clip-wrapping

In the case of clip-wrapping, the entire essence stream is wrapped into a single KLV triplet (also called an _element_).

Index entries are relative to the first byte of the V of each element, _with the
exception of IAB Track Files, where they are relative to the K of the IAB Clip
Wrap element.

### Indexing

Indexes come in one of two forms:

- CBE, where all index entries point to elements of the same size in bytes
- VBE, in all other cases

## Reading

### General

The library implements two ways of reading the contents of an MXF file:

- the `StreamingReader` reads an MXF file sequentially, from beginning to end, starting with the header metadata and then each essence element as they occur in the file, across all Essence Containers and Generic Streams, and across all partitions. Index Tables, the RIP and any Header Metadata other than that from the Header Partition are ignored.

- the `RandomAccessReader` requires random access to the file, but allows seeking to any access unit within the file in constant time and in any order. The file must contain a RIP and Index Tables. It is limited to a single Essence Container but can contain any number of Generic Stream partitions.

### Streaming reader

The first step to using the `StreamingReader` is to read the Header Metadata from the file's header by instantiating a `StreamingFileInfo` object, which advances the file pointer just past the file header. The application can retrieve and inspect the Header Metadata using the `getPreface()` method. The Header Metadata can be used, for example, to determine which tracks are present in the file using the `GCEssenceTracks` helper class.

The next steps is to instantiate a `StreamingReader` object (typically using the same `InputStream` used with the `StreamingFileInfo` object). Each essence and generic stream element contained in the file can then be read in turn by calling the `nextElement()` method until it returns `false`. Each time the method returns, the `StreamingReader` object, which extends `InputStream` will be positioned at the first byte of the value of the element. The element key and length can be read using the `getElementKey()` and `getElementLength()`, respectively.

The `StreamingReader` does not differentiate between kinds of essence wrapping and between essence containers and generic streams: clip-wrapped essence is returned a single element, each element of a frame-wrapped essence container is returned as an individual element and each element within a Generic Stream Partition is also returned as an individual element. 

The operation of the `StreamingReader` is demonstrated at
<library/src/test/java/com/sandflow/smpte/mxf/StreamingReaderTest.java> and at <library/src/test/java/com/sandflow/smpte/mxf/ReadWriteTest.java>.

### Random access reader

The first step to using the `RandomAccessReader` is to read-in the file's Header Metadata, Index Tables and RIP by instantiating a `RandomAccessFileInfo` object. In addition to retrieving the Header Metadata (`getPreface()`), this object can be used, for example, to determine which generic streams tracks are present in the file (`getGenericStreams()`) or the number of essence edit units present (`getEUCount()`).

The next step depends on the kind of wrapping used for the essence, something that the library cannot unfortunately determine on its own and which determines how the essence is indexed:

- if the essence is clip-wrapped, then a `ClipReader` is instantiated and the `seek()` method seeks to the first byte of essence of the specified edit unit.

- if the essence is frame-wrapped, then a `FrameReader` is instantiated and the `seek()` method seeks to the first byte of the key of the first element of the specified edit unit. Each subsequent element of the edit unit can be accessed using the `nextElement()` method

To access a Generic Stream, a `GenericStreamReader` is instantiated and the `seek()` method seeks to the first byte of the key of the first element of the specified generic stream.

The `ClipReader`, `FrameReader` and `GenericStreamReader` objects extend `InputStream` and behave similarly to the `StreamingReader`.

The operation of the `RandomAccessReader` is demonstrated at
<library/src/test/java/com/sandflow/smpte/mxf/RandomAccessReaderTest.java>.

## Writing

The library implements a `StreamingWriter` class that writes an MXF file sequentially, from beginning to end.

The first step is to instantiate a `StreamingWriter` object from a complete snapshot of the Header Metadata. This snapshot can be generated from an existing file, manually or by using the `OP1aHelper` class. The latter generates the Header Metadata for an OP1a MXF file that contains one or more essence tracks.

The next step is to register the essence containers and generic stream that the file contains:

- `addCBEClipWrappedGC()` registers a clip-wrapped essence container with constant rate essence, e.g., multichannel audio samples, and returns a `GCClipCBEWriter` instance;

- `addVBEClipWrappedGC()` registers a clip-wrapped essence container with variable rate essence, e.g., IA bitstream, and returns a `GCClipVBEWriter` instance;

- `addVBEFrameWrappedGC()` registers a frame-wrapped essence container, e.g., J2K image essence, and returns a `GCFrameVBEWriter` instance;

- `addGenericStream()` registers a generic stream, and returns a `GSWriter` instance.

These functions return a `ContainerWriter` instance that extends `OutputStream` will be used the write the essence or generic stream data across file partitions. 

The writing of the file body can then begin by calling the `start()` method.

Each body partition contained within the file is written in turn by calling the `startPartition()` method with the `ContainerWriter` subclass instance returned previously and then writing the partition data using the methods of the `ContainerWriter` instance:

- in the case of `GSWriter`, each element within the Generic Stream partition is written in turn by calling the `nextElement()` method and writing the value of the element using the `GSWriter` itself;

- in the case of `GCClipCBEWriter`, the clip is written by calling the `nextClip()` method and writing the contents of the clip using the `GCClipCBEWriter` itself;

- in the case of `GCClipVBEWriter`, the clip is written by calling the `nextClip()` method and writing the contents of the clip using the `GCClipVBEWriter` itself, prefacing each access unit by a call to `nextAccessUnit()`;

- in the case of `GCFrameVBEWriter`, each element within a content package is written by calling the `nextElement()` method and writing the contents of the element using the `GCFrameVBEWriter` itself, prefacing each access unit by a call to `nextContentPackage()`;

The writing of the file ends with the `finish()` method.

The operation of the `StreamingWriter` is demonstrated at
<library/src/test/java/com/sandflow/smpte/mxf/StreamingWriterTest.java> and at <library/src/test/java/com/sandflow/smpte/mxf/ReadWriteTest.java>.

## Prerequisites

- Java 17
- Maven
- (recommended) Git
