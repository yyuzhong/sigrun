package sigrun.common;

import sigrun.converters.SeismicValuesConverter;

/**
 * Created by maksenov on 15/01/15.
 */
public class LiteSeismicTrace {
    private final TraceHeader header;
    private final float[] values;

    public static LiteSeismicTrace create(final TraceHeader header, DataSample sample) {
        SeismicValuesConverter converter = ConverterFactory.getConverter(sample);
        return new LiteSeismicTrace(header);
    }

    private LiteSeismicTrace(TraceHeader header) {
        values = new float[1];
        this.header = header;
    }

    public TraceHeader getHeader() {
        return header;
    }

    public float getMin() {
        return -1.0f;
    }

    public float getMax() {
        return 1.0f;
    }

    public float[] getValues() {
        return values;
    }
}
