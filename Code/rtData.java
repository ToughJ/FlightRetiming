package src.main.retime2;

import java.util.ArrayList;
import java.util.HashMap;

public class rtData {

    static class Flight implements Comparable<Flight> {
        // Basic Info
        int index;
        String orginalID;
        int flightNum; // NewFlightNum
        int year, month, day;

        int CRSdt, CRSat;
        int CRSdepartureTime, CRSarrivalTime;
        int ACTdt, ACTat;
        int ACTdepartureTime, ACTarrivalTime;
        int OPRdt, OPRat;
        int OPRdepartureTime, OPRarrivalTime;

        String tailNum;
        String carrier;
        String oriPortCode, desPortCode;
        int oriPort, desPort;

        // Route
        Flight preF, aftF;
        int preFID, aftFID;
        String preOriginalID = null, aftOriginalID = null;

        int minimalTurnTime;
        int minimalFlightTime;

        int CRSBufferTT, CRSBufferFT;
        int CRSTT, CRSFT;
        int ACTTT, ACTFT;
        int OPRBufferTT, OPRBufferFT;

        int permitDepPostpone, permitArrPostpone;
        int permitDepAdvance, permitArrAdvance;

        int primaryDepDelay, primaryArrDelay;

        double MAXPossiblePPG = 0;

        double flightMu;
        double flightSigma;
        HashMap<Integer, Double> flightLambda;

        boolean possible = true;

        int depTimeBlockHead,depTimeBlockTail;
        int arrTimeBlockHead,arrTimeBlockTail;

        int Stringindex;

        Flight() {
        }

//        public String outputAllInfo() {
//            StringBuffer sb = new StringBuffer();
//            sb.append("Flight Number: ").append(flightNum).append(" ");
//            sb.append("Tail: ").append(tail).append(" ");
//            sb.append(oriPortCode).append("-->").append(desPortCode).append(" ");
//            sb.append(CRSDepTime).append("-->").append(CRSArrTime).append(" ");
//            sb.append(PDepTime).append("-->").append(PArrTime).append(" ");
//            return sb.toString();
//        }

//        public String outputFirstLineCOMMA() {
//            StringBuilder sb = new StringBuilder();
//            sb.append(outputREADFirstLineCOMMA());
//            sb.append(",").append("PDepTime").append(",").append("PArrTime");
//            sb.append(",").append("PDT").append(",").append("PAT");
//            sb.append(",").append("ACTTime").append(",").append("ACTTime");
//            sb.append(",").append("ACTDT").append(",").append("ACTAT");
//            return sb.toString();
//        }

        private int changeToTIME(int min) {
            if (min < 0) min += 24 * 60;
            if (min >= 24 * 60) min -= 24 * 60;
            StringBuilder sb = new StringBuilder();
            int hh = min / 60;
            if (hh == 0) sb.append("00");
            else if (hh < 10) sb.append("0").append(hh);
            else sb.append(hh);
            int mm = min % 60;
            if (mm == 0) sb.append("00");
            else if (mm < 10) sb.append("0").append(mm);
            else sb.append(mm);
            return Integer.parseInt(sb.toString());
        }

        private int changeToNum(int time) {
            int min = 0;
            min += time % 100;
            time /= 100;
            min += time * 60;
            return min;
        }

        public boolean hasPreFlight() {
            return preF != null;
        }

        public boolean hasAftFlight() {
            return aftF != null;
        }

        @Override
        public int compareTo(Flight o) {
            return changeToNum(this.CRSdt) - changeToNum(o.CRSdt);
        }

        public void setMAXPossiblePPG(double p){
            this.MAXPossiblePPG = p;
        }

        public void setDate(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        public void setCRSdt(int parseInt) {
            this.CRSdt = parseInt;
            this.CRSdepartureTime = changeToNum(parseInt);
        }

        public void setCRSat(int parseInt) {
            this.CRSat = parseInt;
            this.CRSarrivalTime = changeToNum(parseInt);
        }

        public void setACTdt(int parseInt) {
            this.ACTdt = parseInt;
            this.ACTdepartureTime = changeToNum(parseInt);
        }

        public void setACTat(int parseInt) {
            this.ACTat = parseInt;
            this.ACTarrivalTime = changeToNum(parseInt);
        }

        public void setOPRdt(int parseInt) {
            this.OPRdt = parseInt;
            this.OPRdepartureTime = changeToNum(parseInt);
        }

        public void setOPRat(int parseInt) {
            this.OPRat = parseInt;
            this.OPRarrivalTime = changeToNum(parseInt);
        }

        public void setCarrier(String unit) {
            this.carrier = unit;
        }

        public void setTailNum(String unit) {
            this.tailNum = unit;
        }

        public void setFlightNum(int parseInt) {
            this.flightNum = parseInt;
        }

        public void setPort(String unit1, String unit2, int indexOf1, int indexOf2) {
            this.oriPortCode = unit1;
            this.desPortCode = unit2;
            this.oriPort = indexOf1;
            this.desPort = indexOf2;
        }

//        public void setLeg(String legName, int indexOf) {
//            this.Leg = legName;
//            this.index = indexOf;
//        }

        public void setConnection(String unit1, String unit2) {
            this.preOriginalID = unit1;
            this.aftOriginalID = unit2;
        }

        public void setTurnTime(int parseInt, int parseInt1, int parseInt2, int parseInt3) {
            this.CRSTT = parseInt;
            this.ACTTT = parseInt1;
            this.minimalTurnTime = Math.min(parseInt1,parseInt2);
            this.CRSBufferTT = parseInt3;
        }

        public void setFlightTime(int parseInt, int parseInt1, int parseInt2, int parseInt3) {
            this.CRSFT = parseInt;
            this.ACTFT = parseInt1;
            this.minimalFlightTime =Math.min(parseInt,parseInt2);
            this.CRSBufferFT = parseInt3;
        }

        public void setOriginalID(String parseString) {
            this.orginalID = parseString;
        }

        public void setID(int id) {
            this.index = id;
        }

        public void setPrimaryDelay(int parseInt, int parseInt1) {
            this.primaryDepDelay = parseInt;
            this.primaryArrDelay = parseInt1;
        }

        public void setPostpone(int depPostpone, int arrPostpone) {
            this.permitDepPostpone = depPostpone;
            this.permitArrPostpone = arrPostpone;
        }

        public void setAdvance(int depAdvance, int arrAdvance) {
            this.permitDepAdvance = depAdvance;
            this.permitArrAdvance = arrAdvance;
        }

        public void setPlanTime(int xdep, int xarr) {
            this.OPRdepartureTime = xdep;
            this.OPRarrivalTime = xarr;
            if (xdep > 24*60) xdep-=24*60;
            if (xdep < 0)  xdep += 24*60;
            if (xarr > 24*60) xarr-=24*60;
            if (xarr < 0)  xarr += 24*60;
            this.OPRdt = changeToTIME(xdep);
            this.OPRat = changeToTIME(xarr);
        }

        public void initialFlightSet(){
            this.flightLambda = new HashMap<>();
        }

        public void setMuSigma(double d1, double d2){
            this.flightMu = d1;
            this.flightSigma = d2;
        }

        public void setFlightLambda(int hmKey, double parseDouble) {
            this.flightLambda.put(hmKey, parseDouble);
        }
    }

    static class Airport {
        int airport;
        String airportCode;
        HashMap<Integer, Double> depLambda,arrLambda;
        ArrayList<Double> depMu, arrMu, depSigma, arrSigma;
        ArrayList<Boolean> depFlag, arrFlag;

        double MAXDepDElayinAllPERIOD = 0, MAXArrDElayinAllPERIOD = 0;

        Airport() {
        }

        Airport(String code, int index) {
            this.airport = index;
            this.airportCode = code;
        }

        public void initialAirportStatInfo(){
            this.depLambda = new HashMap<>();
            this.arrLambda = new HashMap<>();
            this.depMu = new ArrayList<>();
            this.arrMu = new ArrayList<>();
            this.depSigma = new ArrayList<>();
            this.arrSigma = new ArrayList<>();
        }

        public void setDepLambda(int hmKey, double parseDouble) {
            if(parseDouble <0.09 && parseDouble > -0.09) parseDouble = 0;
            this.depLambda.put(hmKey, (double) Math.round(parseDouble * 100) / 100);
        }

        public void setArrLambda(int hmKey, double parseDouble) {
            if(parseDouble <0.09 && parseDouble > -0.09) parseDouble = 0;
            this.arrLambda.put(hmKey, (double) Math.round(parseDouble * 100) / 100);
        }

        public void setDepSigmaMu(double parseDouble, double parseDouble1) {
            this.depMu.add((double) Math.round(parseDouble * 100) / 100);
            this.depSigma.add((double) Math.round(parseDouble1 * 100) / 100);
        }

        public void setArrSigmaMu(double parseDouble, double parseDouble1) {
            this.arrMu.add((double) Math.round(parseDouble * 100) / 100);
            this.arrSigma.add((double) Math.round(parseDouble1 * 100) / 100);
        }

        public void setDepMAX(double v) {
            this.MAXDepDElayinAllPERIOD = this.MAXDepDElayinAllPERIOD < v ? v: this.MAXDepDElayinAllPERIOD;
        }

        public void setArrMAX(double v) {
            this.MAXArrDElayinAllPERIOD = this.MAXArrDElayinAllPERIOD < v ? v: this.MAXArrDElayinAllPERIOD;
        }
    }

}
