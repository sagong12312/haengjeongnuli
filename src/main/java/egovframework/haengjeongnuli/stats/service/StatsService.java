package egovframework.haengjeongnuli.stats.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import egovframework.haengjeongnuli.civil.service.CivilKind;
import egovframework.haengjeongnuli.hr.service.DutyKind;
import egovframework.haengjeongnuli.stats.service.impl.StatsMapper;

/** 업무 통계 (합계만, 개인정보 없음) */
@Service
public class StatsService {

    /** 한 줄: 이름과 숫자 열 */
    public record Row(String label, List<Number> values) {}

    private final StatsMapper mapper;

    public StatsService(StatsMapper mapper) {
        this.mapper = mapper;
    }

    private static long n(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    /** 월별 민원: 접수, 처리, 기한 내 처리, 기한 내 처리율 (1~12월 모두) */
    public List<Row> civilMonthly(int year) {
        Map<Integer, Map<String, Object>> byMonth = new LinkedHashMap<>();
        for (Map<String, Object> r : mapper.civilMonthly(year)) {
            byMonth.put((int) n(r.get("month")), r);
        }
        List<Row> out = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Map<String, Object> r = byMonth.get(m);
            long rec = r == null ? 0 : n(r.get("received"));
            long done = r == null ? 0 : n(r.get("done"));
            long on = r == null ? 0 : n(r.get("onTime"));
            out.add(new Row(m + "월", List.of(rec, done, on, rate(on, done))));
        }
        return out;
    }

    public List<Row> civilByKind(int year) {
        List<Row> out = new ArrayList<>();
        for (Map<String, Object> r : mapper.civilByKind(year)) {
            String kind = String.valueOf(r.get("kind"));
            String label;
            try {
                label = CivilKind.valueOf(kind).getLabel();
            } catch (IllegalArgumentException e) {
                label = kind;
            }
            long done = n(r.get("done"));
            out.add(new Row(label, List.of(n(r.get("received")), done, n(r.get("onTime")), rate(n(r.get("onTime")), done),
                    n(r.get("extended")))));
        }
        return out;
    }

    public Map<String, Long> approval(int year) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String s : List.of("DRAFT", "PROG", "DONE", "REJECTED")) {
            m.put(s, 0L);
        }
        mapper.approvalByStatus(year).forEach(r -> m.put(String.valueOf(r.get("status")), n(r.get("cnt"))));
        return m;
    }

    public List<Row> finance(int year) {
        List<Row> out = new ArrayList<>();
        for (Map<String, Object> r : mapper.financeByDept(year)) {
            long b = n(r.get("budget"));
            long u = n(r.get("used"));
            out.add(new Row(r.get("dept") == null ? "(부서 없음)" : String.valueOf(r.get("dept")), List.of(b, u, b - u, rate(u, b))));
        }
        return out;
    }

    public Map<String, Long> welfare(int year) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String s : List.of("RECEIVED", "SURVEY", "GRANTED", "DENIED", "WITHDRAWN")) {
            m.put(s, 0L);
        }
        long onTime = 0;
        for (Map<String, Object> r : mapper.welfareByStatus(year)) {
            m.put(String.valueOf(r.get("status")), n(r.get("cnt")));
            onTime += n(r.get("onTime"));
        }
        m.put("ON_TIME", onTime);
        return m;
    }

    public Map<String, Long> hygiene(int year) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("적합", 0L);
        m.put("부적합", 0L);
        mapper.hygieneByResult(year).forEach(r -> m.put(String.valueOf(r.get("result")), n(r.get("cnt"))));
        return m;
    }

    public Map<String, Long> building() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String k : List.of("PERMIT_VALID", "PERMIT_STARTED", "PERMIT_CANCELLED", "REPORT_VALID", "REPORT_STARTED",
                "REPORT_CANCELLED", "OVERDUE")) {
            m.put(k, 0L);
        }
        long overdue = 0;
        for (Map<String, Object> r : mapper.buildingByStatus(java.time.LocalDate.now())) {
            m.put(r.get("kind") + "_" + r.get("status"), n(r.get("cnt")));
            overdue += n(r.get("overdue"));
        }
        m.put("OVERDUE", overdue);
        return m;
    }

    public List<Row> duty(int year) {
        List<Row> out = new ArrayList<>();
        for (Map<String, Object> r : mapper.dutyByKind(year)) {
            String label;
            try {
                label = DutyKind.valueOf(String.valueOf(r.get("kind"))).getLabel();
            } catch (IllegalArgumentException e) {
                label = String.valueOf(r.get("kind"));
            }
            out.add(new Row(label, List.of(n(r.get("cnt")), ((Number) r.get("days")).doubleValue(), ((Number) r.get("hours")).doubleValue())));
        }
        return out;
    }

    /** 민원 통계 CSV (엑셀에서 열 수 있게 UTF-8 BOM) */
    public String civilCsv(int year) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(year).append("년 월별 민원\n월,접수,처리,기한 내 처리,기한 내 처리율(%)\n");
        for (Row r : civilMonthly(year)) {
            sb.append(r.label());
            r.values().forEach(v -> sb.append(',').append(v));
            sb.append('\n');
        }
        sb.append("\n종류별 민원\n종류,접수,처리,기한 내 처리,기한 내 처리율(%),처리기간 연장\n");
        for (Row r : civilByKind(year)) {
            sb.append(r.label());
            r.values().forEach(v -> sb.append(',').append(v));
            sb.append('\n');
        }
        return sb.toString();
    }
}
