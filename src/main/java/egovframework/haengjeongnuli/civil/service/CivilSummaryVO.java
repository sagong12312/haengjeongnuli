package egovframework.haengjeongnuli.civil.service;

/** 민원 처리 현황 숫자 */
public class CivilSummaryVO {

    private int received;
    private int inProgress;
    private int imminent;
    private int doneThisMonth;

    public int getReceived() { return received; }
    public void setReceived(int received) { this.received = received; }
    public int getInProgress() { return inProgress; }
    public void setInProgress(int inProgress) { this.inProgress = inProgress; }
    public int getImminent() { return imminent; }
    public void setImminent(int imminent) { this.imminent = imminent; }
    public int getDoneThisMonth() { return doneThisMonth; }
    public void setDoneThisMonth(int doneThisMonth) { this.doneThisMonth = doneThisMonth; }
}
