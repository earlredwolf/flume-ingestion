package com.stratio.ingestion.deserializer.compression;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.IOUtils;
import org.apache.flume.serialization.DurablePositionTracker;
import org.apache.flume.serialization.PositionTracker;
import org.apache.flume.serialization.ResettableFileInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import static org.fest.assertions.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ResettableDecompressInputStreamRandomTest {

    private int rawBytesSize;
    private byte[] rawBytes;
    private File compressedFile;
    private File metaFile;
    private ResettableFileInputStream resettableFileInputStream;

    public ResettableDecompressInputStreamRandomTest(int compressedFileSize) {
        this.rawBytesSize = compressedFileSize;
    }

    @Parameterized.Parameters
    public static Collection parameters() {
        return Arrays.asList(
                new Object[]{0},
                new Object[]{1},
                new Object[]{16},
                new Object[]{1024},
                new Object[]{1024 * 10},
                new Object[]{1024 * 100}
        );
    }

    @Before
    public void setUp() throws IOException, CompressorException {
        final Random r = new Random();
        rawBytes = new byte[rawBytesSize];
        r.nextBytes(rawBytes);

        compressedFile = File.createTempFile("compressed_data", ".gz");
        final FileOutputStream fileOutputStream = new FileOutputStream(compressedFile);
        final OutputStream outputStream = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.GZIP, fileOutputStream);
        IOUtils.write(rawBytes, outputStream);
        outputStream.close();
        fileOutputStream.close();
        System.out.println(compressedFile.toPath().toAbsolutePath().toString());

        metaFile = File.createTempFile("meta_file", ".meta");
        metaFile.delete();
        resettableFileInputStream = new ResettableFileInputStream(
                compressedFile,
                DurablePositionTracker.getInstance(metaFile, compressedFile.getAbsolutePath()));
    }

    @After
    public void tearDown() {
        try {
            metaFile.delete();
            compressedFile.delete();
            resettableFileInputStream.close();
        } catch (IOException ex) {

        }

    }

    @Test
    public void readSimple() throws IOException, CompressorException {
        final ResettableDecompressInputStream resettableDecompressInputStream = new ResettableDecompressInputStream(resettableFileInputStream, CompressionFormat.GZIP, new TransientPositionTracker("DUMMY"));
        final byte[] uncompressedBytes = new byte[rawBytesSize];
        int offset = 0;
        int read = 0;
        while ((read = resettableDecompressInputStream.read(uncompressedBytes, offset, uncompressedBytes.length - offset)) != -1 && offset < uncompressedBytes.length) {
            System.out.println("READ: " + read);
            offset += read;
        }
        assertThat(uncompressedBytes).isEqualTo(rawBytes);
    }

    @Test
    public void readWithResets() throws IOException, CompressorException {
        final ResettableDecompressInputStream resettableDecompressInputStream = new ResettableDecompressInputStream(resettableFileInputStream, CompressionFormat.GZIP, new TransientPositionTracker("DUMMY"));
        final byte[] uncompressedBytes = new byte[rawBytesSize];
        int offset = 0;
        int markOffset = 0;
        int read = 0;
        int i = 0;
        while ((read = resettableDecompressInputStream.read(uncompressedBytes, offset, uncompressedBytes.length - offset)) != -1 && offset < uncompressedBytes.length) {
            System.out.println("READ: " + read);
            offset += read;
            if (i == 2) {
                resettableDecompressInputStream.mark();
                markOffset = offset;
            }
            if (i > 0 && i % (rawBytesSize / 8) == 0) {
                resettableDecompressInputStream.reset();
                offset = markOffset;
            }
            i++;
        }
        assertThat(uncompressedBytes).isEqualTo(rawBytes);
    }

    @Test
    public void readWithFullResets() throws IOException, CompressorException {
        final PositionTracker positionTracker = new TransientPositionTracker("DUMMY");
        ResettableDecompressInputStream resettableDecompressInputStream = new ResettableDecompressInputStream(
                resettableFileInputStream, CompressionFormat.GZIP, positionTracker);
        final byte[] uncompressedBytes = new byte[rawBytesSize];
        int offset = 0;
        int markOffset = 0;
        int read = 0;
        int i = 0;
        while ((read = resettableDecompressInputStream.read(uncompressedBytes, offset, uncompressedBytes.length - offset)) != -1 && offset < uncompressedBytes.length) {
            System.out.println("READ: " + read);
            offset += read;
            if (i == 2) {
                resettableDecompressInputStream.mark();
                markOffset = offset;
            }
            if (i > 0 && i % (rawBytesSize / 8) == 0) {
                resettableDecompressInputStream.close();
                resettableDecompressInputStream = new ResettableDecompressInputStream(
                        resettableFileInputStream, CompressionFormat.GZIP, positionTracker);
                offset = markOffset;
            }
            i++;
        }
        assertThat(uncompressedBytes).isEqualTo(rawBytes);
    }

}