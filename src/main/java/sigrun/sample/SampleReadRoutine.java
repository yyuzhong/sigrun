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
import java.util.Vector;

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
                withTransductionConstantFormat(FormatEntry.create(204, 210)).
                withTransductionUnitsFormat(FormatEntry.create(210, 212)).
                withSourceEnergyDirectionFormat(FormatEntry.create(218, 224)).
                withSourceMeasurementFormat(FormatEntry.create(224, 230)).
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
            /*
            if(true) {
                for (LiteSeismicTrace trace : segyStream) {
                    printTraceInfo(trace);
                }
            } else {
                TraceHeader t1 = segyStream.peekTraceHeader();
                long sn = t1.getNumberOfSamples();
                long tn = segyStream.getNumberOfTrace(sn);
                for(int i=0;i<tn;i++)
                {
                    TraceHeader tmp = segyStream.getTraceHeader(i,sn);
                    printTraceHeader(tmp);
                }
            }
            */

            TraceHeader tp1 = segyStream.peekTraceHeader();
            long sn = tp1.getNumberOfSamples();
            long tn = segyStream.getNumberOfTrace(sn);
            TraceHeader th1 =  segyStream.getTraceHeader(0,sn);
            TraceHeader th2 =  segyStream.getTraceHeader(1,sn);
            TraceHeader thN =  segyStream.getTraceHeader(tn-1,sn);

            printTraceHeader(th1);
            printTraceHeader(th2);
            printTraceHeader(thN);

            //TraceHeader thM = getMaxXlineTraceHeader(segyStream, sn, tn);
            checkTraceHeaderConfig(th1,th2,thN,tn);

            final long timeEnd = System.currentTimeMillis() - startTime;
            System.out.println("Parsing took: " + timeEnd + " ms.");
        } catch (FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
            System.exit(2);
        }


        System.exit(0);
    }

    private static TraceHeader getMaxXlineTraceHeader(SEGYStream segyStream, long numSamples, long numTraces) {
        TraceHeader res = null;

        TraceHeader prev = segyStream.getTraceHeader(0,numSamples);
        for(int i=1;i<numTraces;i++)
        {
            TraceHeader tmp = segyStream.getTraceHeader(i,numSamples);
            if(prev.getCrossLineNumber() > tmp.getCrossLineNumber()) break;
            if(prev.getEnsembleNumber() > tmp.getEnsembleNumber()) break;
            if(prev.getTransductionUnitsRev() > tmp.getTransductionUnitsRev()) break;
            if(prev.getSourceMeasurementRev() > tmp.getSourceMeasurementRev()) break;
            if(prev.getyOfCDPPosition() > tmp.getyOfCDPPosition()) break;
            if(prev.getEnergySourcePointNumber() > tmp.getEnergySourcePointNumber()) break;
            
            prev = tmp;   
        }

        return prev;
    }


    private static boolean checkTraceHeaderConfig(TraceHeader thd1, TraceHeader thd2, 
            TraceHeader thdN, long numTraces) {
        Vector<Integer> ilnPositions1 = new Vector<Integer>(4);
        Vector<Integer> xlnPositions1 = new Vector<Integer>(4);
        Vector<Integer> ilnPositions2 = new Vector<Integer>(4);
        Vector<Integer> xlnPositions2 = new Vector<Integer>(4);
        Vector<Integer> ilnPositionsN = new Vector<Integer>(4);
        Vector<Integer> xlnPositionsN = new Vector<Integer>(4);

        ilnPositions1.addElement(thd1.getInLineNumber()); /*189*/
        ilnPositions1.addElement(thd1.getSourceEnergyDirectionRev()); /*221*/
        ilnPositions1.addElement(thd1.getTransductionConstantRev()); /*205*/
        ilnPositions1.addElement(thd1.getTraceSequenceNumberWS()); /*5*/
        ilnPositions1.addElement(thd1.getOriginalFieldRecordNumber());/*9*/
        ilnPositions1.addElement(thd1.getTraceNumberWOFR()); /*13*/
        ilnPositions1.addElement(thd1.getxOfCDPPosition()); /*181*/

        ilnPositions2.addElement(thd2.getInLineNumber());
        ilnPositions2.addElement(thd2.getSourceEnergyDirectionRev());
        ilnPositions2.addElement(thd2.getTransductionConstantRev());
        ilnPositions2.addElement(thd2.getTraceSequenceNumberWS());
        ilnPositions2.addElement(thd2.getOriginalFieldRecordNumber());
        ilnPositions2.addElement(thd2.getTraceNumberWOFR());
        ilnPositions2.addElement(thd2.getxOfCDPPosition());

        ilnPositionsN.addElement(thdN.getInLineNumber());
        ilnPositionsN.addElement(thdN.getSourceEnergyDirectionRev());
        ilnPositionsN.addElement(thdN.getTransductionConstantRev());
        ilnPositionsN.addElement(thdN.getTraceSequenceNumberWS());
        ilnPositionsN.addElement(thdN.getOriginalFieldRecordNumber());
        ilnPositionsN.addElement(thdN.getTraceNumberWOFR());
        ilnPositionsN.addElement(thdN.getxOfCDPPosition());

        xlnPositions1.addElement(thd1.getCrossLineNumber()); /*193*/
        xlnPositions1.addElement(thd1.getEnsembleNumber());  /*21*/
        xlnPositions1.addElement(thd1.getTransductionUnitsRev()); /*209*/
        xlnPositions1.addElement(thd1.getSourceMeasurementRev()); /*225*/
        xlnPositions1.addElement(thd1.getyOfCDPPosition()); /*185*/
        xlnPositions1.addElement(thd1.getEnergySourcePointNumber()); /*17, YZ*/

        xlnPositions2.addElement(thd2.getCrossLineNumber()); /*193*/
        xlnPositions2.addElement(thd2.getEnsembleNumber());  /*21*/
        xlnPositions2.addElement(thd2.getTransductionUnitsRev()); /*209*/
        xlnPositions2.addElement(thd2.getSourceMeasurementRev()); /*225*/
        xlnPositions2.addElement(thd2.getyOfCDPPosition()); /*185*/
        xlnPositions2.addElement(thd2.getEnergySourcePointNumber()); /*17, YZ*/

        xlnPositionsN.addElement(thdN.getCrossLineNumber()); /*193*/
        xlnPositionsN.addElement(thdN.getEnsembleNumber());  /*21*/
        xlnPositionsN.addElement(thdN.getTransductionUnitsRev()); /*209*/
        xlnPositionsN.addElement(thdN.getSourceMeasurementRev()); /*225*/
        xlnPositionsN.addElement(thdN.getyOfCDPPosition()); /*185*/
        xlnPositionsN.addElement(thdN.getEnergySourcePointNumber()); /*17, YZ*/

        int ilinSize = ilnPositions1.size();
        int xlinSize = xlnPositions1.size();

        int il1, il2, ilN, ilinc, ilcount;
        int xl1, xl2, xlN, xlinc, xlcount;

        boolean cfgOK = false;

        for(int i=0; i<ilinSize; i++) {

            il1 = ilnPositions1.get(i);
            ilN = ilnPositionsN.get(i);
            System.out.println("-------------------------------" + i + "---------------------------------"); 
            for(int x=0; x<xlinSize; x++) {
                xl1 = xlnPositions1.get(x);
                xl2 = xlnPositions2.get(x);
                xlN = xlnPositionsN.get(x);

                xlinc = xl2 - xl1;
                xlcount = (xlinc==0) ? 1:(int) Math.floor(1.5 + (xlN-xl1)/xlinc);
                ilcount = (xlcount == 0) ? 1:(int) Math.floor(0.5 + numTraces / xlcount );
                ilinc = (ilcount ==0) ? 0 : 1 + Math.abs(ilN - il1 - 1) / ilcount;
                ilinc = (ilN>il1) ? ilinc: -ilinc;
                ilcount = (ilinc ==0) ? 1: (int) Math.floor(1.5 + (ilN - il1) / ilinc);

                System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxx"+x+"xxxxxxxxxxxxxxxxxxxxxx"); 
                System.out.println("Xline Min: "  + xl1);
                System.out.println("Xline 2: "  + xl2);
                System.out.println("Xline Max: "  + xlN);
                System.out.println("Xline Step: "  + xlinc);
                System.out.println("Xline count: "  + xlcount);

                System.out.println("Inline Min: "  + il1);
                System.out.println("Inline Max: "  + ilN);
                System.out.println("Inline Step: " + ilinc);
                System.out.println("Inline Count: "  + ilcount);

                System.out.println("Number of Traces " + numTraces);


                if(cfgOK = (ilcount * xlcount == numTraces && xlcount > 1 && 
                            Math.abs(xlinc) < 100000 && Math.abs(ilinc)<100000)) {
                    System.out.println("******************" + x + "*********************"); 
                    System.out.println("Xline Min: "  + xl1);
                    System.out.println("Xline 2: "  + xl2);
                    System.out.println("Xline Max: "  + xlN);
                    System.out.println("Xline Step: "  + xlinc);
                    System.out.println("Xline Count: "  + xlcount);
                    System.out.println("Inline Min: " + il1);
                    System.out.println("Inline Max: " + ilN);
                    System.out.println("Inline Step: " + ilinc);
                    System.out.println("Inline Count: " + ilcount);
                    System.out.println("Number of Traces " + numTraces);
                    break;
                }
            }
            if(cfgOK) break;
        }    

        return cfgOK;
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

    private static void printTraceHeader(TraceHeader thd) {
        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<Trace Header info<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        //System.out.println("Trace Seq in Line " + thd.getTraceSequenceNumberWL());
        System.out.println("5 Trace Seq in File " + thd.getTraceSequenceNumberWS());
        System.out.println("9 Original Record Number " + thd.getOriginalFieldRecordNumber());
        System.out.println("13 Original Trace Number " + thd.getTraceNumberWOFR());
        System.out.println("181 X of CDP: " + thd.getxOfCDPPosition());
        System.out.println("189 Inline Number: " + thd.getInLineNumber());
        System.out.println("205 Transduction Constant: " + thd.getTransductionConstantRev());
        System.out.println("221 Source Engergy Direction: " + thd.getSourceEnergyDirectionRev());

        System.out.println("**************************");
        System.out.println("17 Endergy Source Point Number " + thd.getEnergySourcePointNumber());
        System.out.println("21 Ensemble Number " + thd.getEnsembleNumber());
        System.out.println("185 Y of CDP: " + thd.getyOfCDPPosition());
        System.out.println("193 Cross Number: " + thd.getCrossLineNumber());
        System.out.println("209 Transduction Unites Format: " + thd.getTransductionUnitsRev());
        System.out.println("225 Source Mesaurement: " + thd.getSourceMeasurementRev());

        System.out.println("**************************");
        System.out.println("115 Number of samples: " + thd.getNumberOfSamples());

        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>End of Trace Header info>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }
    private static void printTraceInfo(LiteSeismicTrace trace) {
        printTraceHeader(trace.getHeader());
        //System.out.println("Size of array: " + trace.getValues().length);
        //System.out.printf("Values: %.10f : %.10f%n", trace.getMin(), trace.getMax());
        //System.out.printf("Diff: %.10f%n", trace.getMax() - trace.getMin());
    }
}
