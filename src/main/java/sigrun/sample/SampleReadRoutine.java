package sigrun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sigrun.common.*;
import sigrun.serialization.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

/**
 *
 *
 *
 * Created by maksenov on 17/01/15.
 */
public class SampleReadRoutine {
    private static final Logger logger = LoggerFactory.getLogger(SampleReadRoutine.class.getName());

    private static ParseProgressListener makeListener() {
        return new ParseProgressListener() {
            @Override
            public void progress(long read) {
                System.out.println("Progress changed to: " + read);
            }
        };
    }

    private static Set<ParseProgressListener> makeListenerSet() {
        Set<ParseProgressListener> result = new HashSet<ParseProgressListener>();
        result.add(makeListener());

        return result;
    }

    public static BinaryHeaderFormat makeBinHeaderFormat() {
        return BinaryHeaderFormatBuilder.aBinaryHeaderFormat()
                .withLineNumberFormat(FormatEntry.create(4, 8))
                .withSampleIntervalFormat(FormatEntry.create(16, 18))
                .withSamplesPerDataTraceFormat(FormatEntry.create(20, 22))
                .withDataSampleCodeFormat(FormatEntry.create(24, 26))
                .withSegyFormatRevNumberFormat(FormatEntry.create(300, 302))
                .withFixedLengthTraceFlagFormat(FormatEntry.create(302, 304))
                .withNumberOf3200ByteFormat(FormatEntry.create(304, 306))
                .build();
    }

    public static TraceHeaderFormat makeTraceHeaderFormat() {
        return TraceHeaderFormatBuilder.aTraceHeaderFormat().
                withTraceSequenceNumberWLFormat(FormatEntry.create(0, 4)).
                withTraceSequenceNumberWSFormat(FormatEntry.create(4, 8)).
                withOriginalFieldRecordNumberFormat(FormatEntry.create(8, 12)).
                withTraceNumberWOFRFormat(FormatEntry.create(12, 16)).
                withEnergySourcePointNumberFormat(FormatEntry.create(16, 20)).
                withEnsembleNumberFormat(FormatEntry.create(20, 24)).
                withSourceXFormat(FormatEntry.create(72, 76)).
                withSourceYFormat(FormatEntry.create(76, 80)).
                withNumberOfSamplesFormat(FormatEntry.create(114, 116)).
                withXOfCDPPositionFormat(FormatEntry.create(180, 184)).
                withYOfCDPPositionFormat(FormatEntry.create(184, 188)).
                withInLineNumberFormat(FormatEntry.create(188, 192)).
                withCrossLineNumberFormat(FormatEntry.create(192, 196)).
                withSourceEnergyDirectionFormat(FormatEntry.create(220, 224)).
                build();
    }


    public static void main(String[] args) {
        String path = args[0];

        if (path == null || path.isEmpty()) {
            logger.error("Path is empty. Aborting");
            System.exit(1);
        }

        logger.info(path);

        try {
            FileChannel chan = new FileInputStream(path).getChannel();

            SEGYStreamFactory streamFactory = SEGYStreamFactory.create(
                    Charset.forName("Cp1047"),
                    makeBinHeaderFormat(),
                    makeTraceHeaderFormat());

            final long startTime = System.currentTimeMillis();

            SEGYStream segyStream = streamFactory.makeStream(chan, makeListenerSet());

            printTextHeader(segyStream.getTextHeader());
            printBinHeaderInfo(segyStream.getBinaryHeader());

            for (SeismicTrace trace : segyStream) {
                printTraceInfo(trace);
            }

            final long timeEnd = System.currentTimeMillis() - startTime;
            System.out.println("Parsing took: " + timeEnd + " ms.");
        } catch (FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
            System.exit(2);
        }


        System.exit(0);
    }

    private static void printTextHeader(TextHeader header) {
        System.out.println("Text Header info...");
        for (String s : header.getContents()) {
            System.out.println(s);
        }
    }

    private static void printBinHeaderInfo(BinaryHeader binaryHeader) {
        System.out.println("Binary Header info...");
        System.out.println("Data sample code:" + binaryHeader.getDataSampleCode());
    }

    private static void printTraceInfo(SeismicTrace trace) {
        System.out.println("-------------------Trace Header info----------------------------");
        //System.out.println("Trace Seq in Line " + trace.getHeader().getTraceSequenceNumberWL());
        System.out.println("5 Trace Seq in File " + trace.getHeader().getTraceSequenceNumberWS());
        System.out.println("9 Original Record Number " + trace.getHeader().getOriginalFieldRecordNumber());
        System.out.println("13 Original Trace Number " + trace.getHeader().getTraceNumberWOFR());
        System.out.println("181 X of CDP: " + trace.getHeader().getxOfCDPPosition());
        System.out.println("189 Inline Number: " + trace.getHeader().getInLineNumber());
        System.out.println("221 Inline number: " + trace.getHeader().getSourceEnergyDirection());

        System.out.println("**********************************************************");
        System.out.println("17 Endergy Source Point Number " + trace.getHeader().getEnergySourcePointNumber());
        System.out.println("21 Ensemble Number " + trace.getHeader().getEnsembleNumber());
        System.out.println("185 Y of CDP: " + trace.getHeader().getyOfCDPPosition());
        System.out.println("193 Cross Number: " + trace.getHeader().getCrossLineNumber());

        System.out.println("**********************************************************");
        System.out.println("115 Number of samples: " + trace.getHeader().getNumberOfSamples());

        //System.out.println("Size of array: " + trace.getValues().length);
        //System.out.printf("Values: %.10f : %.10f%n", trace.getMin(), trace.getMax());
        //System.out.printf("Diff: %.10f%n", trace.getMax() - trace.getMin());
        System.out.println("<<<<<<<<<<<<<<<<<<<<< End of Trace Header info>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }
}
