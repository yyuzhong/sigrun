package sigrun;

public class Section {

    protected long start = 0;
    protected long end  = 0;
                    
    public Section() {}
                        
    public Section(long start, long end) {
        init(start,end);
    }
                                
    public void init(long start,long end) {
        setStart(start);
        setEnd(end);
    }
                                    
                                        
    public void setStart(long start) {
        this.start = start >= 0 ? start : 0;
    }
                                            
    public void setEnd(long end) {
        this.end = end >= 0 ? end : 0;
    }
                                                
    public long getStart() {
         return start;
    }
                        
    public long getEnd() {
         return end;
    }
}
