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
                //System.out.println("Progress changed to: " + read);
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

            checkTraceHeaderConfig(th1,th2,thN,tn);

            int xidx = checkXlineIndex(th1,th2);

            TraceHeader ilinc = getInlineIncTrace(segyStream, sn, tn);
            //printTraceHeader(ilinc);
            int iidx = checkInlineIndex(th1,th2,ilinc);

            TraceHeader mxth = getXlineTraceInfo(segyStream, sn, tn, xidx);

            getInlineTraceInfo(th1,ilinc,thN,iidx);

            //printTraceHeader(mxth);

            final long timeEnd = System.currentTimeMillis() - startTime;
            System.out.println("Parsing took: " + timeEnd + " ms.");
        } catch (FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
            System.exit(2);
        }


        System.exit(0);
    }

    private static int checkInlineIndex(TraceHeader thd1, TraceHeader thd2, TraceHeader ilinc) {
        int MAX_NUM = 10000000;
        int MAX_INC = 1000;
        int res = -1;

        int iln1 = thd1.getInLineNumber();
        int iln2 = thd2.getInLineNumber();
        int ilnc = ilinc.getInLineNumber();
        if((iln1<MAX_NUM)&&(iln2<MAX_NUM)&&(ilnc<MAX_NUM)&&
                (iln2==iln1)&&(ilnc-iln1<MAX_INC)&&(ilnc-iln1>0)) res = 189;

        int sed1 = thd1.getSourceEnergyDirectionRev();
        int sed2 = thd2.getSourceEnergyDirectionRev();
        int sedc = ilinc.getSourceEnergyDirectionRev();
        if((sed1<MAX_NUM)&&(sed2<MAX_NUM)&&(sedc<MAX_NUM)&&
                (sed2==sed1)&&(sedc-sed1<MAX_INC)&&(sedc-sed1>0)) res = 221;

        int tc1 = thd1.getTransductionConstantRev();
        int tc2 = thd2.getTransductionConstantRev();
        int tcc = ilinc.getTransductionConstantRev();
        if((tc1<MAX_NUM)&&(tc2<MAX_NUM)&&(tcc<MAX_NUM)&&
                (tc2==tc1)&&(tcc-tc1<MAX_INC)&&(tcc-tc1>0)) res = 205;

        int tsn1 = thd1.getTraceSequenceNumberWS();
        int tsn2 = thd2.getTraceSequenceNumberWS();
        int tsnc = ilinc.getTraceSequenceNumberWS();
        if((tsn1<MAX_NUM)&&(tsn2<MAX_NUM)&&(tsnc<MAX_NUM)&&
                (tsn2==tsn1)&&(tsnc-tsn1<MAX_INC)&&(tsnc-tsn1>0)) res = 5;

        int ofrn1 = thd1.getOriginalFieldRecordNumber();
        int ofrn2 = thd2.getOriginalFieldRecordNumber();
        int ofrnc = ilinc.getOriginalFieldRecordNumber();
        if((ofrn1<MAX_NUM)&&(ofrn2<MAX_NUM)&&(ofrnc<MAX_NUM)&&
                (ofrn2==ofrn1)&&(ofrnc-ofrn1<MAX_INC)&&(ofrnc-ofrn1>0)) res = 9;

        int tn1 = thd1.getTraceNumberWOFR();
        int tn2 = thd2.getTraceNumberWOFR();
        int tnc = ilinc.getTraceNumberWOFR();
        if((tn1<MAX_NUM)&&(tn2<MAX_NUM)&&(tnc<MAX_NUM)&&
                (tn2==tn1)&&(tnc-tn1<MAX_INC)&&(tnc-tn1>0)) res = 13;

        int cdp1 = thd1.getxOfCDPPosition();
        int cdp2 = thd2.getxOfCDPPosition();
        int cdpc = ilinc.getxOfCDPPosition();
        if((cdp1<MAX_NUM)&&(cdp2<MAX_NUM)&&(cdpc<MAX_NUM)&&
                (cdp2==cdp1)&&(cdpc-cdp1<MAX_INC)&&(cdpc-cdp1>0)) res = 181;

        System.out.println("Find Inline Index at:" + res);
        return res;
    }

    private static int checkXlineIndex(TraceHeader thd1, TraceHeader thd2) {
        int MAX_NUM = 10000000;
        int MAX_INC = 1000;
        int res = -1;

        int cln1 = thd1.getCrossLineNumber();
        int cln2 = thd2.getCrossLineNumber();
        if((cln1<MAX_NUM)&&(cln2<MAX_NUM)&&(cln2-cln1<MAX_INC)&&(cln2-cln1>0)) res = 193;

        int en1 = thd1.getEnsembleNumber();
        int en2 = thd2.getEnsembleNumber();
        if((en1<MAX_NUM)&&(en2<MAX_NUM)&&(en2-en1<MAX_INC)&&(en2-en1>0)) res = 21;

        int tu1 = thd1.getTransductionUnitsRev();
        int tu2 = thd2.getTransductionUnitsRev();
        if((tu1<MAX_NUM)&&(tu2<MAX_NUM)&&(tu2-tu1<MAX_INC)&&(tu2-tu1>0)) res = 209;

        int sm1 = thd1.getSourceMeasurementRev();
        int sm2 = thd2.getSourceMeasurementRev();
        if((sm1<MAX_NUM)&&(sm2<MAX_NUM)&&(sm2-sm1<MAX_INC)&&(sm2-sm1>0)) res = 225;

        int cdp1 = thd1.getyOfCDPPosition();
        int cdp2 = thd2.getyOfCDPPosition();
        if((cdp1<MAX_NUM)&&(cdp2<MAX_NUM)&&(cdp2-cdp1<MAX_INC)&&(cdp2-cdp1>0)) res = 185;

        int espn1 = thd1.getEnergySourcePointNumber();
        int espn2 = thd2.getEnergySourcePointNumber();
        if((espn1<MAX_NUM)&&(espn2<MAX_NUM)&&(espn2-espn1<MAX_INC)&&(espn2-espn1>0)) res = 17;

        System.out.println("Find Xline Index at:" + res);
        return res;
    }

    private static TraceHeader getXlineTraceInfo(SEGYStream segyStream, long numSamples, long numTraces, int xidx) {

        TraceHeader res = null;
        int xlineDiff = Integer.MIN_VALUE;
        int xlineMax = Integer.MIN_VALUE;
        int xlineTmp = -1;
        int xlineMin = Integer.MAX_VALUE;
        int xlineInc = 0;

        TraceHeader thd1 = segyStream.getTraceHeader(0,numSamples);
        TraceHeader thd2 = segyStream.getTraceHeader(1,numSamples);

        Vector<Vector<Section>> linesInfo = new Vector<Vector<Section>>();
        int secStart = -1;

        switch(xidx)
        {
            case 193:
                xlineInc = thd2.getCrossLineNumber() - thd1.getCrossLineNumber(); 
                secStart = thd1.getCrossLineNumber();
                break;
            case 21: 
                xlineInc = thd2.getEnsembleNumber() - thd1.getEnsembleNumber(); 
                secStart = thd1.getEnsembleNumber();
                break;
            case 209:
                xlineInc = thd2.getTransductionUnitsRev() - thd1.getTransductionUnitsRev();
                secStart = thd1.getTransductionUnitsRev();
                break;
            case 225:
                xlineInc = thd2.getSourceMeasurementRev() - thd1.getSourceMeasurementRev();
                secStart = thd1.getSourceMeasurementRev();
                break;
            case 185:
                xlineInc = thd2.getyOfCDPPosition() - thd1.getyOfCDPPosition();
                secStart = thd1.getyOfCDPPosition();
                break;
            case 17:
                xlineInc = thd2.getEnergySourcePointNumber() - thd1.getEnergySourcePointNumber();
                secStart = thd1.getEnergySourcePointNumber();
                break;
        }

        int i = 0;

        TraceHeader tmp = segyStream.getTraceHeader(i,numSamples);
        while(i<numTraces-1)
        {
            // TraceHeader header = segyStream.getTraceHeader(i,numSamples);
            TraceHeader header = tmp;
            Vector<Section> line = new Vector<Section>();
            switch(xidx)
            {
                case 193:
                     if(header.getCrossLineNumber() < xlineMin) xlineMin = header.getCrossLineNumber(); 
                     break;
                case 21:
                     if(header.getEnsembleNumber() < xlineMin) xlineMin = header.getEnsembleNumber(); 
                     break;
                case 209:
                     if(header.getTransductionUnitsRev() < xlineMin) xlineMin = header.getTransductionUnitsRev(); 
                     break;
                case 225:
                     if(header.getSourceMeasurementRev() < xlineMin) xlineMin = header.getSourceMeasurementRev(); 
                     break;
                case 185:
                     if(header.getyOfCDPPosition() < xlineMin) xlineMin = header.getyOfCDPPosition(); 
                     break;
                case 17:
                     if(header.getEnergySourcePointNumber() < xlineMin) xlineMin = header.getEnergySourcePointNumber(); 
                     break;
            }

            while(i<numTraces-1) {
                i++;
                TraceHeader prev = tmp;
                long prevPos = segyStream.getPos();
                tmp = segyStream.getTraceHeader(i,numSamples);
                boolean found = false;

                //System.out.println("To check trace: " + i);
                switch(xidx)
                {
                    case 193:
                        xlineTmp = tmp.getCrossLineNumber() - header.getCrossLineNumber(); 
                        if(tmp.getCrossLineNumber() > xlineMax) xlineMax = tmp.getCrossLineNumber(); 
                        if(tmp.getCrossLineNumber() - prev.getCrossLineNumber() != xlineInc) 
                        {
                            Section sec = new Section(secStart, prev.getCrossLineNumber());
                            sec.setPos(prevPos);
                            line.addElement(sec);
                            System.out.println("Add Section at " + i + " : " + sec.getStart() + " to " + sec.getEnd() + 
                                    " Position: " + sec.getPos());
                            secStart = tmp.getCrossLineNumber();
                        }
                        break;
                    case 21: 
                        xlineTmp = tmp.getEnsembleNumber() - header.getEnsembleNumber(); 
                        if(tmp.getEnsembleNumber() > xlineMax) xlineMax = tmp.getEnsembleNumber(); 
                        if(tmp.getEnsembleNumber() - prev.getEnsembleNumber() != xlineInc) 
                        {
                            Section sec = new Section(secStart, prev.getEnsembleNumber());
                            sec.setPos(prevPos);
                            line.addElement(sec);
                            System.out.println("Add Section at " + i + " : " + sec.getStart() + " to " + sec.getEnd() + 
                                    " Position: " + sec.getPos());
                            secStart = tmp.getEnsembleNumber();
                        }
                        break;
                    case 209:
                        xlineTmp = tmp.getTransductionUnitsRev() - header.getTransductionUnitsRev();
                        if(tmp.getTransductionUnitsRev() > xlineMax) xlineMax = tmp.getTransductionUnitsRev(); 
                        if(tmp.getTransductionUnitsRev() - prev.getTransductionUnitsRev() != xlineInc) 
                        {
                            Section sec = new Section(secStart, prev.getTransductionUnitsRev());
                            sec.setPos(prevPos);
                            line.addElement(sec);
                            System.out.println("Add Section at " + i + " : " + sec.getStart() + " to " + sec.getEnd() + 
                                    " Position: " + sec.getPos());
                            secStart = tmp.getTransductionUnitsRev();
                        }
                        break;
                    case 225:
                        xlineTmp = tmp.getSourceMeasurementRev() - header.getSourceMeasurementRev();
                        if(tmp.getSourceMeasurementRev() > xlineMax) xlineMax = tmp.getSourceMeasurementRev(); 
                        if(tmp.getSourceMeasurementRev() - prev.getSourceMeasurementRev() != xlineInc) 
                        {
                            Section sec = new Section(secStart, prev.getSourceMeasurementRev());
                            sec.setPos(prevPos);
                            line.addElement(sec);
                            System.out.println("Add Section at " + i + " : " + sec.getStart() + " to " + sec.getEnd() + 
                                    " Position: " + sec.getPos());
                            secStart = tmp.getSourceMeasurementRev();
                        }
                        break;
                    case 185:
                        xlineTmp = tmp.getyOfCDPPosition() - header.getyOfCDPPosition();
                        if(tmp.getyOfCDPPosition() > xlineMax) xlineMax = tmp.getyOfCDPPosition(); 
                        if(tmp.getyOfCDPPosition() - prev.getyOfCDPPosition() != xlineInc) 
                        {
                            Section sec = new Section(secStart, prev.getyOfCDPPosition());
                            sec.setPos(prevPos);
                            line.addElement(sec);
                            System.out.println("Add Section at " + i + " : " + sec.getStart() + " to " + sec.getEnd() + 
                                    " Position: " + sec.getPos());
                            secStart = tmp.getyOfCDPPosition();
                        }
                        break;
                    case 17:
                        xlineTmp = tmp.getEnergySourcePointNumber() - header.getEnergySourcePointNumber();
                        if(tmp.getEnergySourcePointNumber() > xlineMax) xlineMax = tmp.getEnergySourcePointNumber(); 
                        if(tmp.getEnergySourcePointNumber() - prev.getEnergySourcePointNumber() != xlineInc) 
                        {
                            Section sec = new Section(secStart, prev.getEnergySourcePointNumber());
                            sec.setPos(prevPos);
                            line.addElement(sec);
                            System.out.println("Add Section at " + i + " : " + sec.getStart() + " to " + sec.getEnd() + 
                                    " Position: " + sec.getPos());
                            secStart = tmp.getEnergySourcePointNumber();
                        }
                        break;
                }
                if(xlineTmp>0) {
                    if(xlineDiff < xlineTmp) {
                        xlineDiff = xlineTmp;
                        res = tmp;
                    }
                    found = true;
                } else {
                    break;
                }
            }
            linesInfo.addElement(line);
        }

        System.out.println("Min Xline: " + xlineMin + ", Max Xline: " + xlineMax + 
                " Inc: " + xlineInc + ", Max Diff: " + xlineDiff);

        return res;
    }

    private static boolean getInlineTraceInfo(TraceHeader thd1, TraceHeader ilinc, TraceHeader thdN, int iidx) {
        boolean res = false;
        
        int inlineInc = Integer.MIN_VALUE;
        int inlineMax = Integer.MIN_VALUE;
        int inlineMin = Integer.MAX_VALUE;

        switch(iidx)
        {
            case 189:
                inlineMin = thd1.getInLineNumber();
                inlineInc = ilinc.getInLineNumber() - thd1.getInLineNumber();
                inlineMax = thdN.getInLineNumber();
                break;
            case 221:
                inlineMin = thd1.getSourceEnergyDirectionRev();
                inlineInc = ilinc.getSourceEnergyDirectionRev() - thd1.getSourceEnergyDirectionRev();
                inlineMax = thdN.getSourceEnergyDirectionRev();
                break;
            case 205:
                inlineMin = thd1.getTransductionConstantRev();
                inlineInc = ilinc.getTransductionConstantRev() - thd1.getTransductionConstantRev();
                inlineMax = thdN.getTransductionConstantRev();
                break;
            case 5:
                inlineMin = thd1.getTraceSequenceNumberWS();
                inlineInc = ilinc.getTraceSequenceNumberWS() - thd1.getTraceSequenceNumberWS();
                inlineMax = thdN.getTraceSequenceNumberWS();
                break;
            case 9:
                inlineMin = thd1.getOriginalFieldRecordNumber();
                inlineInc = ilinc.getOriginalFieldRecordNumber() - thd1.getOriginalFieldRecordNumber();
                inlineMax = thdN.getOriginalFieldRecordNumber();
                break;
            case 13:
                inlineMin = thd1.getTraceNumberWOFR();
                inlineInc = ilinc.getTraceNumberWOFR() - thd1.getTraceNumberWOFR();
                inlineMax = thdN.getTraceNumberWOFR();
                break;
            case 181:
                inlineMin = thd1.getxOfCDPPosition();
                inlineInc = ilinc.getxOfCDPPosition() - thd1.getxOfCDPPosition();
                inlineMax = thdN.getxOfCDPPosition();
                break;
        }
        
        System.out.println("Min Inline: " + inlineMin + ", Max Xline: " + inlineMax + ", Inc: " + inlineInc);
        return true;
    }

    private static TraceHeader getInlineIncTrace(SEGYStream segyStream, long numSamples, long numTraces) {

        TraceHeader prev = segyStream.getTraceHeader(0,numSamples);
        TraceHeader tmp = segyStream.getTraceHeader(1,numSamples);;

        boolean ininc = false;
        boolean sedinc = false;
        boolean tcinc = false;
        boolean tsninc = false;
        boolean ofrninc = false;
        boolean tninc = false;
        boolean cdpinc = false;
        if(prev.getInLineNumber() < tmp.getInLineNumber()) ininc = true;
        if(prev.getSourceEnergyDirectionRev() < tmp.getSourceEnergyDirectionRev()) sedinc = true;
        if(prev.getTransductionConstantRev() < tmp.getTransductionConstantRev()) tcinc = true;
        if(prev.getTraceSequenceNumberWS() < tmp.getTraceSequenceNumberWS()) tsninc = true;
        if(prev.getOriginalFieldRecordNumber() < tmp.getOriginalFieldRecordNumber()) ofrninc = true;
        if(prev.getTraceNumberWOFR() < tmp.getTraceNumberWOFR()) tninc = true;
        if(prev.getxOfCDPPosition() < tmp.getxOfCDPPosition()) cdpinc = true;

        //System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&");
        //printTraceHeader(prev);
        //printTraceHeader(tmp);

        prev = tmp;

        int i = 2;

        while(i < numTraces)
        {
            tmp = segyStream.getTraceHeader(i,numSamples);
            if(prev.getInLineNumber() < tmp.getInLineNumber() && !ininc) break;
            if(prev.getSourceEnergyDirectionRev() < tmp.getSourceEnergyDirectionRev() && !sedinc) break;
            if(prev.getTransductionConstantRev() < tmp.getTransductionConstantRev() && !tcinc) break;
            if(prev.getTraceSequenceNumberWS() < tmp.getTraceSequenceNumberWS() && !tsninc) break;
            if(prev.getOriginalFieldRecordNumber() < tmp.getOriginalFieldRecordNumber() && !ofrninc) break;
            if(prev.getTraceNumberWOFR() < tmp.getTraceNumberWOFR() && ! tninc) break;
            if(prev.getxOfCDPPosition() < tmp.getxOfCDPPosition() && !cdpinc) break;
            
            prev = tmp;   
            i++;
        }

        return tmp;
    }


    private static boolean checkTraceHeaderConfig(TraceHeader thd1, TraceHeader thd2, 
            TraceHeader thdN, long numTraces) {
        int MAX_INC = 1000;
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
                /*
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
                */

                if(cfgOK = (ilcount * xlcount == numTraces && xlcount > 1 && 
                            Math.abs(xlinc) < MAX_INC && Math.abs(ilinc)<MAX_INC)) {
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

    private static boolean checkMissingTraces(TraceHeader thd1, TraceHeader thd2, 
            TraceHeader thdM, TraceHeader thdN, long numTraces) {
        int MAX_INC = 1000;
        Vector<Integer> ilnPositions1 = new Vector<Integer>(4);
        Vector<Integer> xlnPositions1 = new Vector<Integer>(4);
        Vector<Integer> ilnPositions2 = new Vector<Integer>(4);
        Vector<Integer> xlnPositions2 = new Vector<Integer>(4);
        Vector<Integer> xlnPositionsM = new Vector<Integer>(4);
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

        xlnPositionsM.addElement(thdM.getCrossLineNumber()); /*193*/
        xlnPositionsM.addElement(thdM.getEnsembleNumber());  /*21*/
        xlnPositionsM.addElement(thdM.getTransductionUnitsRev()); /*209*/
        xlnPositionsM.addElement(thdM.getSourceMeasurementRev()); /*225*/
        xlnPositionsM.addElement(thdM.getyOfCDPPosition()); /*185*/
        xlnPositionsM.addElement(thdM.getEnergySourcePointNumber()); /*17, YZ*/

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
        int xlM;

        boolean cfgOK = false;

        for(int i=0; i<ilinSize; i++) {

            il1 = ilnPositions1.get(i);
            ilN = ilnPositionsN.get(i);
            System.out.println("-------------------------------" + i + "---------------------------------"); 
            for(int x=0; x<xlinSize; x++) {
                xl1 = xlnPositions1.get(x);
                xl2 = xlnPositions2.get(x);
                xlN = xlnPositionsN.get(x);
                xlM = xlnPositionsM.get(x);

                xlinc = xl2 - xl1;
                xlcount = (xlinc==0) ? 1:(int) Math.floor(1.5 + (Math.max(xlM,xlN)-xl1)/xlinc);
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
                            Math.abs(xlinc) < MAX_INC && Math.abs(ilinc) < MAX_INC)) {
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
