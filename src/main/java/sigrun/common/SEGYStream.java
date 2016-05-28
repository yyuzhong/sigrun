package sigrun.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sigrun.serialization.BinaryHeaderReader;
import sigrun.serialization.SEGYFormatException;
import sigrun.serialization.TextHeaderReader;
import sigrun.serialization.TraceHeaderReader;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
//import java.nio.channels.ReadableByteChannel;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by maksenov on 15/01/15.
 */
public class SEGYStream implements Iterable<LiteSeismicTrace>, Closeable {
    private static final Logger log = LoggerFactory.getLogger(SEGYStream.class);
	private final FileChannel chan;
    private final TraceHeaderReader traceHeaderReader;
    private TextHeader textHeader;
    private BinaryHeader binaryHeader;
    private long position = 0;
    private long headerLength = 0; /*YZ, the length of TextHeader + BinaeryHeader*/
    private Set<ParseProgressListener> listeners = new HashSet<ParseProgressListener>();
    private LiteSeismicTrace nextTrace;

	protected SEGYStream(FileChannel chan,
                         TextHeaderReader textHeaderReader,
                         BinaryHeaderReader binaryHeaderReader,
                         TraceHeaderReader traceHeaderReader,
                         Collection<ParseProgressListener> listeners) {
        this.position = 0;
        this.listeners.addAll(listeners);

        try {
            readTextHeader(chan, textHeaderReader);
            readBinaryHeader(chan, binaryHeaderReader);
            headerLength = this.position; /*YZ*/
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new IllegalArgumentException("Looks like file is not a SEGY, aborting");
        }

        this.traceHeaderReader = traceHeaderReader;
        this.chan = chan;
    }

	private void readTextHeader(FileChannel chan, TextHeaderReader textHeaderReader) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(TextHeader.TEXT_HEADER_SIZE);

        if (chan.read(buf) != TextHeader.TEXT_HEADER_SIZE) {
            throw new SEGYFormatException("Unexpected end of file");
        }

        this.textHeader = textHeaderReader.read(buf.array());
        increasePosition(TextHeader.TEXT_HEADER_SIZE);

        assert this.position == TextHeader.TEXT_HEADER_SIZE;
    }

	private void readBinaryHeader(FileChannel chan, BinaryHeaderReader binaryHeaderReader) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(BinaryHeader.BIN_HEADER_LENGTH);

        if (chan.read(buf) != BinaryHeader.BIN_HEADER_LENGTH) {
            throw new SEGYFormatException("Unexpected end of file");
        }

        this.binaryHeader = binaryHeaderReader.read(buf.array());
        increasePosition(BinaryHeader.BIN_HEADER_LENGTH);

        assert this.position == (TextHeader.TEXT_HEADER_SIZE + BinaryHeader.BIN_HEADER_LENGTH);
    }

    private boolean tryReadTrace() {
        ByteBuffer traceBuf = ByteBuffer.allocate(TraceHeader.TRACE_HEADER_LENGTH);

        try {
            if (!chan.isOpen()) {
                return false;
            }

            if (chan.read(traceBuf) != TraceHeader.TRACE_HEADER_LENGTH) {
                log.info("Not enough bytes for next trace. Closing.");
                chan.close();
                return false;
            }

            final TraceHeader header = traceHeaderReader.read(traceBuf.array());
            final int dataLength = binaryHeader.getDataSampleCode().getSize() * header.getNumberOfSamples();

            long currPos = chan.position();
            if(chan.position(currPos+dataLength)==null)
            {
                log.info("Not enough bytes to read trace data. Looks like file is corrupted. Exiting.");
                chan.close();
                return false;
            }
            /* YZ, Only parse header without reading data.
            ByteBuffer dataBuf = ByteBuffer.allocate(dataLength);
            if (chan.read(dataBuf) != dataLength) {
                log.info("Not enough bytes to read trace data. Looks like file is corrupted. Exiting.");
                chan.close();
                return false;
            }
            this.nextTrace = SeismicTrace.create(header, dataBuf.array(), binaryHeader.getDataSampleCode());
            increasePosition(TraceHeader.TRACE_HEADER_LENGTH + dataLength);
            */

            this.nextTrace = LiteSeismicTrace.create(header, binaryHeader.getDataSampleCode());
            notifyProgressListeners(currPos+dataLength);

            return true;
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());

            return false;
        }
    }

    /*YZ, add some interfaces for SEGY trace parser*/
    public long getFileSize() {
        long res = -1;
        try {
            res = chan.size();
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        }
        return res;
    }

    public long getTraceLength(long samples) {
        long dataLength = binaryHeader.getDataSampleCode().getSize() * samples;
        long fileLen = getFileSize();
        long traceLength = TraceHeader.TRACE_HEADER_LENGTH + dataLength;
        return traceLength;
    }

    public boolean isIdeaFile(long samples) {
        long fileLen = getFileSize();
        long traceLength = getTraceLength(samples);
        return (fileLen - headerLength)%traceLength == 0;
    }


    public long getNumberOfTrace(long samples) {
        if(!isIdeaFile(samples)) {
            System.out.println("File size error");
            return -1;
        }
        long fileLen = getFileSize();
        long traceLength = getTraceLength(samples);
        return (fileLen - headerLength)/traceLength;
    }

    /*Seek to being of trace, did not change position in file*/
    public TraceHeader peekTraceHeader() {
        TraceHeader header;
        ByteBuffer traceBuf = ByteBuffer.allocate(TraceHeader.TRACE_HEADER_LENGTH);

        try {
            if (!chan.isOpen()) {
                return null;
            }
            if(chan.position(headerLength)==null)
                System.out.println("File size error");           
            if (chan.read(traceBuf) != TraceHeader.TRACE_HEADER_LENGTH) {
                log.info("Not enough bytes for next trace. Closing.");
                chan.close();
                return null;
            }

            header = traceHeaderReader.read(traceBuf.array());
            return header;
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        }
        return null;
    }

    public TraceHeader getTraceHeader(long index, long samples) {
        TraceHeader header;
        ByteBuffer traceBuf = ByteBuffer.allocate(TraceHeader.TRACE_HEADER_LENGTH);

        long traceLength = getTraceLength(samples);

        try {
            if (!chan.isOpen()) {
                return null;
            }
            if(chan.position(headerLength + traceLength*index)==null)
                System.out.println("File size error");           
            if (chan.read(traceBuf) != TraceHeader.TRACE_HEADER_LENGTH) {
                log.info("Not enough bytes for next trace. Closing.");
                chan.close();
                return null;
            }
            notifyProgressListeners(headerLength + traceLength*(index+1));
            header = traceHeaderReader.read(traceBuf.array());
            return header;
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        }

        return null;
      
    } 

    public boolean seekAbs(long absPos) {
        try {
            if (!chan.isOpen()) {
                return false;
            }
            if(chan.position(absPos)==null)
                System.out.println("File size error");           
                return false;
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        }
        return true;
    }

    public boolean seekOffset(long offset) {
        try {
            if (!chan.isOpen()) {
                return false;
            }
            long currPos = chan.position();
            if(chan.position(currPos + offset)==null)
                System.out.println("File size error");           
                return false;
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        }
        return true;
    }
    /*YZ, end of all*/

    private void increasePosition(long increment) {
        this.position += increment;
        notifyProgressListeners(this.position);
    }

    /**
     * Called when stream parses next portion of bytes.
     *
     * @param progress
     */
    private void notifyProgressListeners(long progress) {
        for (ParseProgressListener listener : listeners) {
            listener.progress(progress);
        }
    }

    public synchronized void registerListener(ParseProgressListener listener) {
        this.listeners.add(listener);
    }

    public synchronized void unregisterListener(ParseProgressListener listener) {
        this.listeners.remove(listener);
    }

    public TextHeader getTextHeader() {
        return textHeader;
    }

    public BinaryHeader getBinaryHeader() {
        return binaryHeader;
    }

    public long getPosition() {
        return this.position;
    }

    @Override
    public Iterator<LiteSeismicTrace> iterator() {
        return new SeismicTraceIterator(this);
    }

    private class SeismicTraceIterator implements Iterator<LiteSeismicTrace> {
        private final SEGYStream parent;

        private SeismicTraceIterator(SEGYStream parent) {
            this.parent = parent;
        }

        @Override
        public boolean hasNext() {
            if (parent.nextTrace == null) {
                parent.tryReadTrace();
            }

            return parent.nextTrace != null;
        }

        @Override
        public LiteSeismicTrace next() {
            if (hasNext()) {
                LiteSeismicTrace result = parent.nextTrace;
                parent.nextTrace = null;

                return result;
            }

            return null;
        }

        /**
         * Not implemented.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Operation is not supported");
        }
    }

    public void close() throws IOException {
        this.chan.close();
    }
}
