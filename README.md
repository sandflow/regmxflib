# regmxflib


                                         _    _        _   
                                        | |  | |  o   | |  
     ,_     _    __,   _  _  _          | |  | |      | |  
    /  |   |/   /  |  / |/ |/ |   /\/   |/   |/   |   |/ \_
       |_/ |__/ \_/|/   |  |  |_/  /\_/ |__/ |__/ |_/  \_/ 
                  /|                    |\                 
                  \|                    |/                 

## Introduction

regmxflib is a collection of tools and libraries for manipulating MXF files.
Because regmxflib is built from the  [SMPTE metadata registers](https://registry.smpte-ra.org/apps/pages/), applications can remain
up-to-date with recent additions to the MXF standard with minimal effort.

regmxflib currently includes:

- Java bindings for MXF header metadata classes (SMPTE ST 377-1);

- Java and C++ implementation of the RegXML (SMPTE ST 2001-1) standard to
  express MXF Header Metadata as XML elements; and

- Java classes for reading and writing MXF files.

regmxflib includes all functionality of [regxmlib](https://github.com/sandflow/regxmllib), which is no longer maintained.

## Quick start

The RegMXFDump utility provides an example of the use of the bindings to generate a JSON representation of an MXF file:

    mvn package -P with-dependencies
    java -cp java-library/target/regmxflib-jar-with-dependencies.jar \
      com.sandflow.smpte.tools.RegMXFDump \
      test-resources/imps/imp_1/VIDEO_f031aa43-88c8-4de9-856f-904a33a78505.mxf > \
      java-library/target/test-output/VIDEO_f031aa43-88c8-4de9-856f-904a33a78505.json

## Overall architecture

### Registers and metadictionaries

regmxflib relies on the SMPTE Metadata Registers, which contain a complete description of MXF Header Metadata and are [published by SMPTE](https://smpte-ra.org/smpte-metadata-registry). Before they are used by regmxflib, the registers are transformed into RegXML metadictionaries, which are normalized representations optimized for MXF. This transformation is performed using the `com.sandflow.smpte.tools.XMLRegistersToDict` tool. This repo includes [a recent copy of the registers](./resources/registers) and their corresponding [RegXML metadictionary representation](./resources/regxml-dicts).

The metadictionaries are used:

* by `com.sandflow.smpte.mxf.ClassGenerator.main()` tool at compile time to generate POJO bindings for MXF header metadata classes;
* at runtime by `com.sandflow.smpte.regxml.FragmentBuilder.fragmentFromTriplet()` to generate RegXML fragments from MXF Header Metadata objects;
* by `com.sandflow.smpte.regxml.XMLSchemaBuilder.fromDictionary()` to generate an XML Schema that can be used to validate RegXML fragments.

### MXF Header Metadata POJOs

The MXF Header Metadata POJOs live in the `com.sandflow.smpte.mxf.types` package. The following snippet illustrates their use using the creation of an `RGBADescriptor` as an example:

    import com.sandflow.smpte.mxf.types.RGBADescriptor;
    ...
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

### MXF Reading

#### General

The library implements two ways of reading the contents of an MXF file:

- the `StreamingReader` reads an MXF file sequentially, from beginning to end, starting with the header metadata and then each essence element as they occur in the file, across all Essence Containers and Generic Streams, and across all partitions. Index Tables, the RIP and any Header Metadata other than that from the Header Partition are ignored.

- random access readers (`ClipReader`, `FrameReader`, `GenericStreamReader`) require random access to the file, but allows seeking to any access unit within the file in constant time and in any order. The file must contain a RIP and Index Tables. It is limited to a single Essence Container but can contain any number of Generic Stream partitions.

#### Streaming reader

The first step to using the `StreamingReader` is to read the Header Metadata from the file's header by instantiating a `StreamingFileInfo` object, which advances the file pointer just past the file header. The application can retrieve and inspect the Header Metadata using the `getPreface()` method. The Header Metadata can be used, for example, to determine which tracks are present in the file using the `GCEssenceTracks` helper class.

The next steps is to instantiate a `StreamingReader` object (typically using the same `InputStream` used with the `StreamingFileInfo` object). Each essence and generic stream element contained in the file can then be read in turn by calling the `nextElement()` method until it returns `false`. Each time the method returns, the `StreamingReader` object, which extends `InputStream` will be positioned at the first byte of the value of the element. The element key and length can be read using the `getElementKey()` and `getElementLength()`, respectively.

The `StreamingReader` does not differentiate between kinds of essence wrapping and between essence containers and generic streams: clip-wrapped essence is returned a single element, each element of a frame-wrapped essence container is returned as an individual element and each element within a Generic Stream Partition is also returned as an individual element. 

The operation of the `StreamingReader` is demonstrated at
[StreamingReaderTest.java](java-library/src/test/java/com/sandflow/smpte/mxf/StreamingReaderTest.java) and at [ReadWriteTest.java](java-library/src/test/java/com/sandflow/smpte/mxf/ReadWriteTest.java).

#### Random access reader

The first step is to read-in the file's Header Metadata, Index Tables and RIP by instantiating a `RandomAccessFileInfo` object. In addition to retrieving the Header Metadata (`getPreface()`), this object can be used, for example, to determine which generic streams tracks are present in the file (`getGenericStreams()`) or the number of essence edit units present (`getEUCount()`).

The next step depends on the kind of wrapping used for the essence, something that the library cannot unfortunately determine on its own and which determines how the essence is indexed:

- if the essence is clip-wrapped, then a `ClipReader` is instantiated and the `seek()` method seeks to the first byte of essence of the specified edit unit.

- if the essence is frame-wrapped, then a `FrameReader` is instantiated and the `seek()` method seeks to the first byte of the key of the first element of the specified edit unit. Each subsequent element of the edit unit can be accessed using the `nextElement()` method

To access a Generic Stream, a `GenericStreamReader` is instantiated and the `seek()` method seeks to the first byte of the key of the first element of the specified generic stream.

The `ClipReader`, `FrameReader` and `GenericStreamReader` objects extend `InputStream` and behave similarly to the `StreamingReader`.

The operation of the `RandomAccessReader` is demonstrated at
[RandomAccessReaderTest.java](java-library/src/test/java/com/sandflow/smpte/mxf/RandomAccessReaderTest.java).

### Writing

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
[StreamingWriterTest.java](./java-library/src/test/java/com/sandflow/smpte/mxf/StreamingWriterTest.java) and at [ReadWriteTest.java](./java-library/src/test/java/com/sandflow/smpte/mxf/ReadWriteTest.java).

## Structure

The library consists of 3 Java modules and one C++ library

- `java-class-generator` generates [POJO classes](./java-library/target/generated-sources) using [register files](./resources/registers).

- `java-library` holds the generated POJO classes and classes for reading and writing MXF files
- `java-common` holds classes that do not depend on the generated classes

A separate C++ implementation of the RegXML fragment builder, also ported from regxmllib,
lives under `cpp/`; see [RegXML](#regxml) below.



### Tools

- `RegXMLDump` dumps either the first essence descriptor or the entire header metadata of an MXF
  file as a RegXML structure
- `XMLRegistersToDict` converts XML-based SMPTE metadata registers to RegXML metadictionaries
- `GenerateDictionaryXMLSchema` generates XSDs for RegXML Fragments from RegXML metadictionaries
- `GenerateXMLSchemaDocuments` generates XSDs for the SMPTE metadata registers

### Known limitations and issues

RegXML generation deviates from ST 2001-1:2013 in one narrow instance: no baseline
metadictionary is used; instead, one extension metadictionary is used per namespace.

Issues are tracked at https://github.com/sandflow/regmxflib/issues.


## Prerequisites

### General

* (recommended) Container engine, e.g. Docker or Podman
* (recommended) Git

### Java

* Java 17
* Maven

### C++

* C++03 toolchain
* Metadictionaries generated by regxmllibj (see _Building Metadictionaries_ above)
* [Xerces-C++](https://xerces.apache.org/xerces-c/) Version 3.1.4 (or above)
* CMake

## Known issues and limitations

regmxflib relies on SMPTE Metadata Registers that conform to SMPTE ST 335, ST
395, ST 400, ST 2003. These registers are published at [1].

[1] https://smpte-ra.org/smpte-metadata-registry

regmxflib deviates from ST 2001-1:2013 in a few narrow instances. Such deviations
are noted in the source code and are expected to be submitted for consideration at
the next revision of ST 2001-1. In particular:

* no baseline metadictionary is used, instead one extension metadictionary per
  namespace is used

