package src.main.retime2;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import src.main.retime2.rtData.Airport;
import src.main.retime2.rtData.Flight;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

//  numVar x
//  x -> u
public class work_sos_hd {

    static ArrayList<String> AirportName;
    static ArrayList<Flight> Flights;
    static ArrayList<Airport> Airports;
    static HashMap<String, Integer> flightIDMap;

    static ArrayList<ArrayList<ArrayList<Double>>> arrDiruptionToAdd;
    static ArrayList<ArrayList<ArrayList<Double>>> depDiruptionToAdd;

    static String path = "../data/event/";
    static String INPUT_DIR = "../data/event/";
    static String ROOT_OUTPUT_DIR = "../data/event/out_gamma3/";
    static String OUTPUT_DIR = null;

    static int startDay, endDay;
    static int startMonth, endMonth;

    static int defaultPostpone = 20;
    static int defaultAdvance = -20;
    static int PERIOD = 20;
    static int numPeriods = 24 * 60 / PERIOD;

    static double TOTMAXPPG = 0;
    static double MAXPPG = 80;// 80
    static double MAXPPG1 = 80;// 80

    static double thisPsi;
    static double toAddPsi;

    static int numString;
    static int AirportLimit;

    static double[] psirecord;

    // para  TODO  参数修改
    static double MGap = 0.01, SGap = 0.01, HGap = 0.05;   // gap   M -master  S - separation H - whole
    static int STL = 60 * 20, HTL = 60 * 60, MTL = 20 * 60; //  time limit
    static double MINI = 0.00000000001;
    static double Gamma = 2;  // Gamma

    static double gamma = Gamma * 51;
    // bufferLimit TODO
    static ArrayList<Double> totalBuffer;
    static ArrayList<Double> totalLast;
    static int control = 0;

    public static void main(String[] args) throws IloException, IOException {
//        String filename = "Month8_Flights_test.csv"; // default
//        String filename = "Month8_Flights.csv"; // default
//        control = 0;
//            startDay = 24;
//            endDay = 24;
//            startMonth = 7;
//            endMonth = 7;
//            run();

//        control = 0;
        for (int i = 21; i <= 31; i++){
            startDay = i;
            endDay = i;
            startMonth = 7;
            endMonth = 7;
            run();
        }
        for (int i = 1; i <= 20; i++){
            startDay = i;
            endDay = i;
            startMonth = 8;
            endMonth = 8;
            run();
        }
    }

    private static void run() {
        try {
            String filename = "Month8_Flights.csv";
            OUTPUT_DIR = ROOT_OUTPUT_DIR + String.format("M%dD%d_M%dD%d/", startMonth, startDay, endMonth, endDay);

            // Create the output directory.
            new File(OUTPUT_DIR).mkdirs();

            // Clear and write an empty file for this name, so don't need to these manually.
            FileWriter init;
            for (String p : new String[]{"checkingEBTD.csv", "checkingPSI.csv", "EBTDModel.log", "SPModel.log"}) {
                init = new FileWriter(OUTPUT_DIR + p);
                init.close();
            }
//             readFlightsInfo(filename);
            AirportLimit = 15;
            readReducedFlightsInfo(filename, AirportLimit);
            readStatInfo();
            clearImpossibleFlight();
            setTimeBlock();
            outputFlightIndex();
//            setBufferLimit();
            solve(filename);
            outputFinalOPRPlan();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setBufferLimit() {
        totalBuffer = new ArrayList<>();
        totalLast = new ArrayList<>();
        boolean[] visit = new boolean[Flights.size()];
        for (Flight f : Flights){
            if (visit[f.index]) continue;
            double tot = 0;
            double last = 0;
            do {
                if (visit[f.index]) f = f.aftF;
                visit[f.index] = true;
                f.Stringindex = totalBuffer.size();
                tot += Math.max(0,f.CRSBufferFT) + Math.max(0,f.CRSBufferTT);
                last += f.minimalFlightTime;
                if (f.hasPreFlight()) last += f.minimalTurnTime;
            }
            while (f.hasAftFlight());
            if (tot < 0) tot =0;
            totalBuffer.add(tot);
            totalLast.add(last);
        }
        numString = totalBuffer.size();
    }

    private static void outputFinalOPRPlan() throws IOException {
        String outputName = OUTPUT_DIR + "final_plan" + startMonth + "_" + startDay;
        File out = new File(outputName + ".csv");
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        for (Flight f : Flights) {
            bw.write(f.index + "," + f.oriPortCode + "," + f.desPortCode + "," +f.CRSdt  + "," + f.CRSat
                    +"," + f.ACTdt  + "," +f.ACTat  + "," + f.OPRdt + "," + f.OPRat  + "\n");
        }
        bw.close();
        fw.close();
    }

    private static void setTimeBlock() {
        for (Flight f : Flights) {
            int r1 = (f.CRSdepartureTime + f.permitDepAdvance) / PERIOD;
            int r2 = (f.CRSdepartureTime + f.permitDepAdvance + (int) MAXPPG1) / PERIOD;
            int depPort = f.oriPort;
            while (!Airports.get(depPort).depFlag.get(properPeriod(r1)) && r1 < r2) {
                r1++;
            }
            f.depTimeBlockHead = r1;
            while (Airports.get(depPort).depFlag.get(properPeriod(r1 + 1)) && r1 < r2) r1++;
            f.depTimeBlockTail = r1;

            r1 = (f.CRSarrivalTime + f.permitArrAdvance) / PERIOD;
            r2 = (f.CRSarrivalTime + f.permitArrPostpone + (int) MAXPPG1) / PERIOD;
            int arrPort = f.desPort;
            while (!Airports.get(arrPort).arrFlag.get(properPeriod(r1)) && r1 < r2) {
                r1++;
            }
            f.arrTimeBlockHead = r1;
            while (Airports.get(arrPort).arrFlag.get(properPeriod(r1 + 1)) && r1 < r2) r1++;
            f.arrTimeBlockTail = r1;
        }
    }

    private static void outputFlightIndex() throws IOException {
        String outputName = OUTPUT_DIR + startMonth + "_" + startDay;
        File out = new File(outputName + ".csv");
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        for (Flight f : Flights) {
            bw.write(f.index + "," + f.orginalID + "," + f.oriPortCode + "," + f.desPortCode + "\n");
//            bw.flush();
        }
        bw.close();
        fw.close();
    }


    private static void clearImpossibleFlight() {
        for (Flight f : Flights) {
            int ori = f.oriPort;
            int des = f.desPort;
            int ro = f.ACTdepartureTime / PERIOD;
            int rd = f.ACTarrivalTime / PERIOD;
            if (!Airports.get(ori).depFlag.get(properPeriod(ro)) ||
                    !Airports.get(des).arrFlag.get(properPeriod(rd))) {
                f.possible = false;
                if (f.hasAftFlight()) f.aftF.preF = null;
                if (f.hasPreFlight()) f.preF.aftF = null;
                f.preF = null;
                f.aftF = null;
            }
            if (!f.hasAftFlight() && !f.hasPreFlight()) {
                f.possible = false;
            }
        }
        int tnt = 0;
        while (tnt < Flights.size()) {
            while (tnt < Flights.size() && (!Flights.get(tnt).possible ||
                    (!Flights.get(tnt).hasPreFlight() && !Flights.get(tnt).hasAftFlight()))) {
                flightIDMap.remove(Flights.get(tnt).orginalID);
                Flights.remove(tnt);
            }
            tnt++;
        }
        for (Flight f : Flights) {
            f.index = Flights.indexOf(f);
            flightIDMap.replace(f.orginalID, f.index);
        }
        for (Flight f : Flights) {
            if (f.hasPreFlight()) {
                if (!flightIDMap.containsKey(f.preF.orginalID)){
                    f.preF = null; f.preFID = -1;
                }
                else{
                    f.preF.aftFID = f.index;
                }
            }
            if (f.hasAftFlight()) {
                if (!flightIDMap.containsKey(f.aftF.orginalID)){
                    f.aftF = null; f.aftFID = -1;
                }
                else {
                    f.aftF.preFID = f.index;
                }
            }
        }
    }

    private static void readReducedFlightsInfo(String filename, int airportLimit) throws IOException {
        AirportName = new ArrayList<>();
        Flights = new ArrayList<>();
        Airports = new ArrayList<>();
        flightIDMap = new HashMap<>();
        // read from data
        String file = INPUT_DIR + filename;
        File tFile = new File(file);
        BufferedReader read = new BufferedReader(new FileReader(tFile));
        read.readLine();
        String thisLine = null;
        while ((thisLine = read.readLine()) != null) {
            String[] units = thisLine.split(",");
            int day = Integer.parseInt(units[3]);
            int month = Integer.parseInt(units[2]);
            if (day < startDay || day > endDay || month < startMonth || month > endMonth) continue;
            String origin = units[15], dest = units[16];
            if ((!AirportName.contains(origin) || !AirportName.contains(dest)) &&
                    AirportName.size() > airportLimit) continue;
            if (!AirportName.contains(origin)) {
                AirportName.add(origin);
                Airports.add(new Airport(origin, Airports.size()));
            }
            if (!AirportName.contains(dest)) {
                AirportName.add(dest);
                Airports.add(new Airport(dest, Airports.size()));
            }
            Flight newFlight = new Flight();
            newFlight.setPort(origin, dest, AirportName.indexOf(origin), AirportName.indexOf(dest));
            newFlight.setOriginalID(units[17]);
            newFlight.setDate(Integer.parseInt(units[1]), month, day);
            newFlight.setCRSdt(Integer.parseInt(units[4]));
            newFlight.setACTdt(Integer.parseInt(units[5]));
            newFlight.setCRSat(Integer.parseInt(units[8]));
            newFlight.setACTat(Integer.parseInt(units[9]));
            newFlight.setCarrier(units[12]);
            newFlight.setTailNum(units[13]);
            newFlight.setFlightNum(Integer.parseInt(units[14]));
            String preOID = null, aftOID = null;
            if (units[18].length() > 0) preOID = units[19];
            if (units[20].length() > 0) aftOID = units[21];
            newFlight.setConnection(preOID, aftOID);
            if (units[19].length() > 0) {
                newFlight.setTurnTime((int) Double.parseDouble(units[23]), (int) Double.parseDouble(units[22]),
                        (int) Double.parseDouble(units[24]), (int) Double.parseDouble(units[25]));
            }
            newFlight.setFlightTime((int) Double.parseDouble(units[28]), (int) Double.parseDouble(units[27]),
                    (int) Double.parseDouble(units[29]), (int) Double.parseDouble(units[30]));
            newFlight.setPrimaryDelay((int) Double.parseDouble(units[32]), (int) Double.parseDouble(units[33]));
            if (flightIDMap.containsKey(newFlight.preOriginalID) &&
                    (Flights.get(flightIDMap.get(newFlight.preOriginalID)).aftOriginalID == null ||
                            !Flights.get(flightIDMap.get(newFlight.preOriginalID)).aftOriginalID.equals(newFlight.orginalID)))
                continue;
            if (flightIDMap.containsKey(newFlight.aftOriginalID) &&
                    (Flights.get(flightIDMap.get(newFlight.aftOriginalID)).preOriginalID == null ||
                            !Flights.get(flightIDMap.get(newFlight.aftOriginalID)).preOriginalID.equals(newFlight.orginalID)))
                continue;
            int ID;
            if (flightIDMap.containsKey(newFlight.orginalID))
                ID = flightIDMap.get(newFlight.orginalID);
            else {
                ID = Flights.size();
                flightIDMap.put(newFlight.orginalID, ID);
            }
            Flights.add(newFlight);
        }
        //  connect Flight & modify the min
        for (Flight flight : Flights) {
            // connection
            if (flight.preOriginalID == null || flightIDMap.getOrDefault(flight.preOriginalID, -1) == -1) {
                flight.preFID = -1;
                flight.preF = null;
            } else {
                flight.preFID = flightIDMap.get(flight.preOriginalID);
                flight.preF = Flights.get(flight.preFID);
            }
            if (flight.aftOriginalID == null || flightIDMap.getOrDefault(flight.aftOriginalID, -1) == -1) {
                flight.aftFID = -1;
                flight.aftF = null;
            } else {
                flight.aftFID = flightIDMap.get(flight.aftOriginalID);
                flight.aftF = Flights.get(flight.aftFID);
            }

            if (flight.CRSdepartureTime > flight.CRSarrivalTime) {
                flight.CRSarrivalTime += 24 * 60;
            }
        }
        for (Flight flight : Flights) {
            // modify time
            if (flight.preFID != -1) {
                if (flight.aftFID == -1) {
                    numString++;
                }
                if (flight.preF.CRSarrivalTime - flight.CRSdepartureTime > 0 &&
                        flight.preF.CRSarrivalTime - flight.CRSdepartureTime > 12 * 60) {
                    // possible because the aft flight tranDay
                    //  (21:50-00:30)  (21:50-24:30)  ->  (0:50- 01:50)
                    flight.CRSdepartureTime += 24 * 60;
                    flight.CRSarrivalTime += 24 * 60;
                }
                if (flight.CRSdepartureTime > 21 * 60 && flight.preF.CRSarrivalTime < 3 * 60) {
                    // possible update route an earlier flight become the aft flight of later flight
                    //  (00:50-02:30)   ->  (23:50 - 01:50) (23:50 - 25:50) (-00:10 - 01:50)
                    flight.CRSdepartureTime -= 24 * 60;
                    flight.CRSarrivalTime -= 24 * 60;
                }
            }

            // set postpone advance
            int depPostpone = defaultPostpone, arrPostpone = defaultPostpone;
            int depAdvance = defaultAdvance, arrAdvance = defaultAdvance;
            int added = 0;
            if (flight.CRSdepartureTime - flight.ACTdepartureTime >= 18 * 60) {
                // default postpone range is not enough  && tran day
                //  ACT 00:30  CRS 23:50
                added = 24 * 60;
            }
            if (flight.CRSdepartureTime - flight.ACTdepartureTime <= -18 * 60) {
                // default postpone range is not enough  && tran day
                //  ACT 00:30  CRS 23:50
                added = -24 * 60;
            }
            if (flight.ACTdepartureTime - flight.CRSdepartureTime + added > defaultPostpone) {
                // default postpone range is not enough
                depPostpone = flight.ACTdepartureTime - flight.CRSdepartureTime + added;
            }
            if (flight.hasPreFlight() && flight.CRSdepartureTime + depPostpone <
                    flight.minimalTurnTime + flight.preF.CRSarrivalTime + flight.preF.permitArrAdvance) {
                depPostpone = flight.preF.CRSarrivalTime + flight.preF.permitArrAdvance
                        + flight.minimalTurnTime - flight.CRSdepartureTime + 20;
            }
            added = 0;
            if (flight.CRSarrivalTime - flight.ACTarrivalTime >= 16 * 60) {
                // default postpone range is not enough  && tran day
                //  ACT 00:30  CRS 23:50
                added = 24 * 60;
            }
            if (flight.ACTarrivalTime - flight.CRSarrivalTime + added > defaultPostpone) {
                // default postpone range is not enough
                arrPostpone = flight.ACTarrivalTime - flight.CRSarrivalTime + added;
            }
            if (flight.CRSdepartureTime + depPostpone + flight.minimalFlightTime <
                    flight.CRSarrivalTime + arrPostpone) {
                arrPostpone = flight.CRSdepartureTime + depPostpone +
                        flight.minimalFlightTime - flight.CRSarrivalTime;
            }
            depAdvance = depPostpone + depAdvance - defaultPostpone;
            arrAdvance = arrPostpone + arrAdvance - defaultPostpone;
            flight.setPostpone(depPostpone, arrPostpone);
            flight.setAdvance(depAdvance, arrAdvance);

            // index problem  force break the connection  TODO
            if (flight.hasPreFlight() && (flight.preF.ACTarrivalTime > flight.ACTdepartureTime
                    || flight.preF.ACTdepartureTime > flight.ACTdepartureTime)){
                if (flight.preF.index == 16) {
                    int a = 1;
                }
                flight.preF.aftFID = -1;
                flight.preF.aftF = null;
                flight.preF.aftOriginalID = null;
                flight.preF = null;
                flight.preFID = -1;
                flight.preOriginalID = null;
            }
        }
        read.close();
    }

    private static void readFlightsInfo(String filename) throws IOException {
        AirportName = new ArrayList<>();
        Flights = new ArrayList<>();
        Airports = new ArrayList<>();
        flightIDMap = new HashMap<>();
        // read from data
        String file = INPUT_DIR + filename;
        File tFile = new File(file);
        BufferedReader read = new BufferedReader(new FileReader(tFile));
        read.readLine();
        String thisLine = null;
        while ((thisLine = read.readLine()) != null) {
            String[] units = thisLine.split(",");
            int day = Integer.parseInt(units[3]);
            int month = Integer.parseInt(units[2]);
            if (day < startDay || day > endDay || month < startMonth || month > endMonth) continue;
            Flight newFlight = new Flight();
            newFlight.setOriginalID(units[17]);
            newFlight.setDate(Integer.parseInt(units[1]), month, day);
            newFlight.setCRSdt(Integer.parseInt(units[4]));
            newFlight.setACTdt(Integer.parseInt(units[5]));
            newFlight.setCRSat(Integer.parseInt(units[8]));
            newFlight.setACTat(Integer.parseInt(units[9]));
            newFlight.setCarrier(units[12]);
            newFlight.setTailNum(units[13]);
            newFlight.setFlightNum(Integer.parseInt(units[14]));
            String origin = units[15], dest = units[16];
            if (!AirportName.contains(origin)) {
                AirportName.add(origin);
                Airports.add(new Airport(origin, Airports.size()));
            }
            if (!AirportName.contains(dest)) {
                AirportName.add(dest);
                Airports.add(new Airport(dest, Airports.size()));
            }
            newFlight.setPort(origin, dest, AirportName.indexOf(origin), AirportName.indexOf(dest));
            String preOID = null, aftOID = null;
            if (units[18].length() > 0) preOID = units[19];
            if (units[20].length() > 0) aftOID = units[21];
            newFlight.setConnection(preOID, aftOID);
            if (units[19].length() > 0) {
                newFlight.setTurnTime((int) Double.parseDouble(units[23]), (int) Double.parseDouble(units[22]),
                        (int) Double.parseDouble(units[24]), (int) Double.parseDouble(units[25]));
            }
            newFlight.setFlightTime((int) Double.parseDouble(units[28]), (int) Double.parseDouble(units[27]),
                    (int) Double.parseDouble(units[29]), (int) Double.parseDouble(units[30]));
            newFlight.setPrimaryDelay((int) Double.parseDouble(units[32]), (int) Double.parseDouble(units[33]));
            int ID;
            if (flightIDMap.containsKey(newFlight.orginalID))
                ID = flightIDMap.get(newFlight.orginalID);
            else {
                ID = Flights.size();
                flightIDMap.put(newFlight.orginalID, ID);
            }
            newFlight.setID(ID);
            Flights.add(newFlight);
        }

        //  connect Flight & modify the min
        for (Flight flight : Flights) {
            // connection
            if (flight.preOriginalID == null || flightIDMap.getOrDefault(flight.preOriginalID, -1) == -1) {
                flight.preFID = -1;
                flight.preF = null;
            } else {
                flight.preFID = flightIDMap.get(flight.preOriginalID);
                flight.preF = Flights.get(flight.preFID);
            }
            if (flight.aftOriginalID == null || flightIDMap.getOrDefault(flight.aftOriginalID, -1) == -1) {
                flight.aftFID = -1;
                flight.aftF = null;
            } else {
                flight.aftFID = flightIDMap.get(flight.aftOriginalID);
                flight.aftF = Flights.get(flight.aftFID);
            }

            // modify time
            if (flight.preFID != -1) {

                if (flight.aftFID == -1) {
                    numString++;
                }
                if (flight.preF.CRSarrivalTime - flight.CRSdepartureTime > 0 &&
                        flight.preF.CRSarrivalTime - flight.CRSdepartureTime > 12 * 60) {
                    // possible because the aft flight tranDay
                    //  (21:50-00:30)  (21:50-24:30)  ->  (0:50- 01:50)
                    flight.CRSdepartureTime += 24 * 60;
                    flight.CRSarrivalTime += 24 * 60;
                }
                if (flight.CRSdepartureTime > 21 * 60 && flight.preF.CRSarrivalTime < 3 * 60) {
                    // possible update route an earlier flight become the aft flight of later flight
                    //  (00:50-02:30)   ->  (23:50 - 01:50) (23:50 - 25:50) (-00:10 - 01:50)
                    flight.CRSdepartureTime -= 24 * 60;
                    flight.CRSarrivalTime -= 24 * 60;
                }
            }
            if (flight.CRSdepartureTime > flight.CRSarrivalTime) {
                flight.CRSarrivalTime += 24 * 60;
            }

            // set postpone advance
            int depPostpone = defaultPostpone, arrPostpone = defaultPostpone;
            int depAdvance = defaultAdvance, arrAdvance = defaultAdvance;
            int added = 0;
            if (flight.CRSdepartureTime - flight.ACTdepartureTime >= 18 * 60) {
                // default postpone range is not enough  && tran day
                //  ACT 00:30  CRS 23:50
                added = 24 * 60;
            }
            if (flight.CRSdepartureTime - flight.ACTdepartureTime <= -18 * 60) {
                // default postpone range is not enough  && tran day
                //  ACT 00:30  CRS 23:50
                added = -24 * 60;
            }
            if (flight.ACTdepartureTime - flight.CRSdepartureTime + added > defaultPostpone) {
                // default postpone range is not enough
                depPostpone = flight.ACTdepartureTime - flight.CRSdepartureTime + added;
            }
            if (flight.hasPreFlight() && flight.CRSdepartureTime + depPostpone <
                    flight.minimalTurnTime + flight.preF.CRSarrivalTime + flight.preF.permitArrAdvance) {
                depPostpone = flight.preF.CRSarrivalTime + flight.preF.permitArrAdvance
                        + flight.minimalTurnTime - flight.CRSdepartureTime + 20;
            }
            added = 0;
            if (flight.CRSarrivalTime - flight.ACTarrivalTime >= 16 * 60) {
                // default postpone range is not enough  && tran day
                //  ACT 00:30  CRS 23:50
                added = 24 * 60;
            }
            if (flight.ACTarrivalTime - flight.CRSarrivalTime + added > defaultPostpone) {
                // default postpone range is not enough
                arrPostpone = flight.ACTarrivalTime - flight.CRSarrivalTime + added;
            }
            if (flight.CRSdepartureTime + depPostpone + flight.minimalFlightTime <
                    flight.CRSarrivalTime + arrPostpone) {
                arrPostpone = flight.CRSdepartureTime + depPostpone +
                        flight.minimalFlightTime - flight.CRSarrivalTime;
            }
            depAdvance = depPostpone + depAdvance - defaultPostpone;
            arrAdvance = arrPostpone + arrAdvance - defaultPostpone;
            flight.setPostpone(depPostpone, arrPostpone);
            flight.setAdvance(depAdvance, arrAdvance);

            // index problem  force break the connection  TODO
            if (flight.hasPreFlight() && (flight.preF.ACTarrivalTime > flight.ACTdepartureTime
                    || flight.preF.ACTdepartureTime > flight.ACTdepartureTime)){
                flight.preF.aftFID = -1;
                flight.preF.aftF = null;
                flight.preF.aftOriginalID = null;
                flight.preF = null;
                flight.preFID = -1;
                flight.preOriginalID = null;
            }
        }
        read.close();
    }

    private static void readStatInfo() throws IOException {
        for (Airport airport : Airports) {
            airport.initialAirportStatInfo();
            String file = INPUT_DIR + "stat/BeforeMonth8_" + airport.airportCode + "DepDelayStats.csv";
            readStatFromCsv(airport, file, true);
            file = INPUT_DIR + "stat/BeforeMonth8_" + airport.airportCode + "ArrDelayStats.csv";
            readStatFromCsv(airport, file, false);
            findTheMaximalPPG();
        }
    }

    public static void readStatFromCsv(Airport airport, String file, boolean DorA) throws IOException {
        File tFile = new File(file);
        BufferedReader read = new BufferedReader(new FileReader(tFile));
        String thisLine = read.readLine();
        // boolean[] flag = new boolean[numPeriods];
        String[] units = thisLine.split(",");
        int efficientBlock = 0;
        // find how many efficient blocks
        if (DorA) airport.depFlag = new ArrayList<>();
        else airport.arrFlag = new ArrayList<>();
        for (int i = 0; i < numPeriods; i++) {
            if (DorA) airport.depFlag.add(false);
            else airport.arrFlag.add(false);
        }
        for (String unit : units) {
            if (unit.length() > 0) {
                if (unit.substring(0, 3).equals("Cov")) {  //  the same  c_  cov
                    if (DorA) airport.depFlag.set(Integer.parseInt(unit.substring(4)), true);
                    else airport.arrFlag.set(Integer.parseInt(unit.substring(4)), true);
                    efficientBlock++;
                } else break;
            }
        }
        for (int i = 0; i < numPeriods; i++) {
            if ((DorA && airport.depFlag.get(i)) || (!DorA && airport.arrFlag.get(i))) {
                thisLine = read.readLine();
                units = thisLine.split(",");
                int k = 0;
                for (int j = efficientBlock + 1; j <= 2 * efficientBlock; j++) {
                    while (!((DorA && airport.depFlag.get(k)) || (!DorA && airport.arrFlag.get(k))) && k < numPeriods)
                        k++;
                    int hmKey = i * numPeriods + k;
                    double hmValue;
                    if (units[j].length() > 25)
                        hmValue = 0;
                    else
                        hmValue = Double.parseDouble(units[j]);
                    if (DorA)
                        airport.setDepLambda(hmKey, hmValue);
                    else
                        airport.setArrLambda(hmKey, hmValue);
                    k++;
                }
                if (DorA) {
                    double d1 = Double.parseDouble(units[2 * efficientBlock + 1]),
                            d2 = Double.parseDouble(units[2 * efficientBlock + 2]);
                    airport.setDepSigmaMu(d1, d2);
                    airport.setDepMAX(d1 + d2 * Math.max(Gamma,2));
                } else {
                    double d1 = Double.parseDouble(units[2 * efficientBlock + 1]),
                            d2 = Double.parseDouble(units[2 * efficientBlock + 2]);
                    airport.setArrSigmaMu(d1, d2);
                    airport.setArrMAX(d1 + d2 * Math.max(Gamma,2));
                }
            } else {
                if (DorA)
                    airport.setDepSigmaMu(0, 0);
                else
                    airport.setArrSigmaMu(0, 0);
            }

        }
        read.close();
    }

    private static void findTheMaximalPPG() {
        for (Flight flight : Flights) {
            int depP = flight.oriPort, arrP = flight.desPort;
            double maxPossibleDelay = Airports.get(depP).MAXDepDElayinAllPERIOD +
                    Airports.get(arrP).MAXArrDElayinAllPERIOD;
            if (flight.hasPreFlight()) {
                maxPossibleDelay += flight.preF.MAXPossiblePPG;
            }
            flight.setMAXPossiblePPG(maxPossibleDelay);

//            MAXPPG = Math.max(MAXPPG, maxPossibleDelay);
            TOTMAXPPG += maxPossibleDelay;
        }
    }

    public static void solve(String name) throws IOException, IloException {
        arrDiruptionToAdd = new ArrayList<>();
        depDiruptionToAdd = new ArrayList<>();

        String outputName = OUTPUT_DIR + name + "_s_time";
        File out = new File(outputName + ".csv");
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("Iteration," + "ROUsedTime," + "SPUsedTime," + "AllUsedTime," + "thisPsi," + "toAddPsi\n");

        int times = 1;
        System.out.println("The " + times + " times to solve");
        long startTime = System.currentTimeMillis(), currentTime, lastTime;
        lastTime = startTime;
        thisPsi = -1;
        EBTDSolver();
        if (thisPsi == -1) return;
        currentTime = System.currentTimeMillis();
        bw.write(times + "," + (currentTime - lastTime) / 1000.0 + ",");
        System.out.println("The " + times++ + " times to find maximum possible delay vector");

        lastTime = currentTime;
        SeparationSolver();
        currentTime = System.currentTimeMillis();
        bw.write((currentTime - lastTime) / 1000.0 + "," + (currentTime - startTime) / 1000.0 + ",");
        lastTime = currentTime;

        bw.write(thisPsi + "," + toAddPsi + "\n");
        bw.flush();
        while ((toAddPsi - thisPsi) / (thisPsi + 0.1) > HGap && (currentTime - startTime) / 1000.0 <= HTL &&
        toAddPsi - thisPsi >= 10) { // TODO
            System.out.println("The " + times + " times to solve");
            EBTDSolver();
            currentTime = System.currentTimeMillis();
            bw.write(times + "," + (currentTime - lastTime) / 1000.0 + ",");
            lastTime = currentTime;
            System.out.println("The " + times++ + " times to find maximum possible delay vector");
            SeparationSolver();
            currentTime = System.currentTimeMillis();
            bw.write((currentTime - lastTime) / 1000.0 + "," + (currentTime - startTime) / 1000.0 + ",");
            lastTime = currentTime;
            bw.write(thisPsi + "," + toAddPsi + "\n");
            bw.flush();
        }
        bw.close();
        fw.close();
    }

    public static void EBTDSolver() throws IloException, IOException {
        IloCplex EBTDModel = new IloCplex();

        EBTDModel.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, MGap);
        EBTDModel.setParam(IloCplex.Param.Threads, 8);
        EBTDModel.setParam(IloCplex.Param.TimeLimit, MTL);

        int numFlights = Flights.size();
        int numRobustConstraints = depDiruptionToAdd.size();

        int periodDimension = (defaultPostpone - defaultAdvance + (int) MAXPPG1) / PERIOD + 1;
        // TODO 20 min 一个 period, 24个小时

        IloIntVar[][] uDep = new IloIntVar[numFlights][periodDimension];
        IloIntVar[][] uArr = new IloIntVar[numFlights][periodDimension];
        IloIntVar[] xDep = new IloIntVar[numFlights];
        IloIntVar[] xArr = new IloIntVar[numFlights];
        IloNumVar[][] pDep = new IloNumVar[numFlights][numRobustConstraints];
        IloNumVar[][] pArr = new IloNumVar[numFlights][numRobustConstraints];

        IloNumVar psi = EBTDModel.numVar(0, TOTMAXPPG, "psi");

        int[] CRSdep = new int[numFlights], CRSarr = new int[numFlights];
        int[][] indUDep = new int[numFlights][3];
        int[][] indUArr = new int[numFlights][3];
        //  indur[][0] start point   indur[][1]  end point   indur[][2]  total Periods

        // Definition
        for (int i = 0; i < numFlights; i++) {
            CRSdep[i] = Flights.get(i).CRSdepartureTime;
            CRSarr[i] = Flights.get(i).CRSarrivalTime;

            indUDep[i][0] = Flights.get(i).depTimeBlockHead;
            indUDep[i][1] = Flights.get(i).depTimeBlockTail;
            indUDep[i][2] = indUDep[i][1] - indUDep[i][0] + 1;
            for (int r = 0; r < indUDep[i][2]; r++)
                uDep[i][r] = EBTDModel.boolVar("uDep" + i + "_" + r);

            indUArr[i][0] = Flights.get(i).arrTimeBlockHead;
            indUArr[i][1] = Flights.get(i).arrTimeBlockTail;
            indUArr[i][2] = indUArr[i][1] - indUArr[i][0] + 1;
            for (int r = 0; r < indUArr[i][2]; r++)
                uArr[i][r] = EBTDModel.boolVar("uArr" + i + "_" + r);

            xDep[i] = EBTDModel.intVar(CRSdep[i] + Flights.get(i).permitDepAdvance,
                    CRSdep[i] + Flights.get(i).permitDepPostpone, "xDep" + i);
            xArr[i] = EBTDModel.intVar(CRSarr[i] + Flights.get(i).permitArrAdvance,
                    CRSarr[i] + Flights.get(i).permitArrPostpone, "xArr" + i);

            for (int s = 0; s < numRobustConstraints; s++) {
                pDep[i][s] = EBTDModel.numVar(0, Flights.get(i).MAXPossiblePPG, "pDep" + i + "_" + s);
                pArr[i][s] = EBTDModel.numVar(0, Flights.get(i).MAXPossiblePPG, "pArr" + i + "_" + s);
            }
        }

        IloLinearNumExpr[] bufferTT = new IloLinearNumExpr[numFlights];
        IloLinearNumExpr[] bufferFT = new IloLinearNumExpr[numFlights];
        IloLinearNumExpr[] sumUDep = new IloLinearNumExpr[numFlights];
        IloLinearNumExpr[] sumUArr = new IloLinearNumExpr[numFlights];

//        IloLinearNumExpr[] buffer = new IloLinearNumExpr[numString];//  bufferLimit
//        for (int i =0; i < numString; i++){
//            buffer[i] = EBTDModel.linearNumExpr(-totalLast.get(i)-control);
//        }

        for (int i = 0; i < numFlights; i++) {

            //  bufferLimit
//            if (!Flights.get(i).hasPreFlight())
//                buffer[Flights.get(i).Stringindex].addTerm(-1, xDep[i]);
//            //bufferLimit
//            if (!Flights.get(i).hasAftFlight()) {
//                buffer[Flights.get(i).Stringindex].addTerm(1, xArr[i]);
//            }
            // FlightTime
            bufferFT[i] = EBTDModel.linearNumExpr(-Flights.get(i).minimalFlightTime);
            bufferFT[i].addTerm(1, xArr[i]);
            bufferFT[i].addTerm(-1, xDep[i]);
            EBTDModel.addGe(bufferFT[i], 0, "bft" + i);
            // TurnTime
            if (Flights.get(i).hasPreFlight()) {
                bufferTT[i] = EBTDModel.linearNumExpr(-Flights.get(i).minimalTurnTime);
                bufferTT[i].addTerm(1, xDep[i]);
                bufferTT[i].addTerm(-1, xArr[Flights.get(i).preFID]);
                EBTDModel.addGe(bufferTT[i], 0, "btt" + i);
            }
            sumUDep[i] = EBTDModel.linearNumExpr();
            for (int r = 0; r < indUDep[i][2]; r++) {
                sumUDep[i].addTerm(1, uDep[i][r]);
            }
            EBTDModel.addEq(sumUDep[i], 1, "_e");
            sumUArr[i] = EBTDModel.linearNumExpr();
            for (int r = 0; r < indUArr[i][2]; r++) {
                sumUArr[i].addTerm(1, uArr[i][r]);
            }
            EBTDModel.addEq(sumUArr[i], 1, "f");

            for (int r = 0; r < indUDep[i][2]; r++) {
                int period = r + indUDep[i][0];
                double tmpp = 200 + xDep[i].getUB();
                if (r > 0)
                    EBTDModel.addGe(xDep[i], EBTDModel.diff(period * PERIOD- 0.05,
                            EBTDModel.prod(tmpp * PERIOD, EBTDModel.diff(1, uDep[i][r]))), "g");
                if (r < indUDep[i][2] - 1)
                    EBTDModel.addLe(xDep[i], EBTDModel.sum(period * PERIOD + PERIOD - 0.05,
                            EBTDModel.prod(tmpp * PERIOD, EBTDModel.diff(1, uDep[i][r]))), "g");
            }

            for (int r = 0; r < indUArr[i][2]; r++) {
                int period = r + indUArr[i][0];
                double tmpp = 200 + xArr[i].getUB();
                if (r > 0)
                    EBTDModel.addGe(xArr[i], EBTDModel.diff(period * PERIOD - 0.05,
                            EBTDModel.prod(tmpp, EBTDModel.diff(1, uArr[i][r]))), "h" + i);
                if (r < indUArr[i][2] - 1)
                    EBTDModel.addLe(xArr[i], EBTDModel.sum(period * PERIOD + PERIOD - 0.05,
                            EBTDModel.prod(tmpp, EBTDModel.diff(1, uArr[i][r]))), "h" + i);
            }
        }
//bufferLimit
//        for (int i =0; i < numString; i++){
//            if (!Flights.get(i).hasPreFlight())
//                EBTDModel.addLe(buffer[Flights.get(i).Stringindex], totalBuffer.get(Flights.get(i).Stringindex));
//        }
        // Propagated Delay Cons
        IloLinearNumExpr[] pSum = new IloLinearNumExpr[numRobustConstraints];
        IloLinearNumExpr[][] disruptionsArr = new IloLinearNumExpr[numFlights][numRobustConstraints];
        IloLinearNumExpr[][] disruptionsDep = new IloLinearNumExpr[numFlights][numRobustConstraints];
//        IloLinearNumExpr[][] yDep = new IloLinearNumExpr[numFlights][numRobustConstraints];
//        IloLinearNumExpr[][] yArr = new IloLinearNumExpr[numFlights][numRobustConstraints];


        IloIntVar[][] zDep = new IloIntVar[numFlights][numRobustConstraints];
        IloIntVar[][] zArr = new IloIntVar[numFlights][numRobustConstraints];
        for (int s = 0; s < numRobustConstraints; s++) {
            pSum[s] = EBTDModel.linearNumExpr();
            for (int i = 0; i < numFlights; i++) {
                if (!Flights.get(i).hasPreFlight())
                    EBTDModel.addEq(pDep[i][s], 0, "pori");
                else {
                    int j = Flights.get(i).preFID;
                    disruptionsArr[j][s] = EBTDModel.linearNumExpr();
                    int lastDestPort = Flights.get(j).desPort;
                    for (int r = 0; r < indUArr[j][2]; r++) {
                        int period = properPeriod(r + indUArr[j][0]);
                        disruptionsArr[j][s].addTerm(arrDiruptionToAdd.get(s).get(lastDestPort).get(period),
                                uArr[j][r]);
                    }
                    zDep[i][s] = EBTDModel.boolVar("zd" + i + "_" + s);
                    EBTDModel.addGe(EBTDModel.diff(pDep[i][s], pArr[j][s]),
                            EBTDModel.diff(disruptionsArr[j][s], bufferTT[i]), "pdep1");
                    EBTDModel.addLe(EBTDModel.diff(pDep[i][s], pArr[j][s]),
                            EBTDModel.sum(EBTDModel.diff(disruptionsArr[j][s], bufferTT[i]),
                                    EBTDModel.prod(pDep[i][s].getUB() + 1,
                                            EBTDModel.diff(1, zDep[i][s]))), "pdep2");
                    EBTDModel.addLe(pDep[i][s], EBTDModel.prod(pDep[i][s].getUB() + 1, zDep[i][s]), "pdep3");
//                    EBTDModel.addLe(EBTDModel.sum(EBTDModel.diff(disruptionsArr[j][s], bufferTT[i]), pArr[j][s]),
//                            EBTDModel.diff(EBTDModel.prod(Flights.get(i).MAXPossiblePPG,
//                                    zDep[i][s]), MINI), "pdep4");
                }
                disruptionsDep[i][s] = EBTDModel.linearNumExpr();
                int OriPort = Flights.get(i).oriPort;
                for (int r = 0; r < indUDep[i][2]; r++) {
                    int period = properPeriod(r + indUDep[i][0]);
                    disruptionsDep[i][s].addTerm(depDiruptionToAdd.get(s).get(OriPort).get(period),
                            uDep[i][r]);
                }
                zArr[i][s] = EBTDModel.boolVar("za" + i + "_" + s);
                EBTDModel.addGe(EBTDModel.diff(pArr[i][s], pDep[i][s]),
                        EBTDModel.diff(disruptionsDep[i][s], bufferFT[i]), "parr1");
                EBTDModel.addLe(EBTDModel.diff(pArr[i][s], pDep[i][s]),
                        EBTDModel.sum(EBTDModel.diff(disruptionsDep[i][s], bufferFT[i]),
                                EBTDModel.prod(pArr[i][s].getUB() + 1,
                                        EBTDModel.diff(1, zArr[i][s]))), "parr2");
                EBTDModel.addLe(pArr[i][s], EBTDModel.prod(pArr[i][s].getUB() + 1, zArr[i][s]), "parr3");
//                EBTDModel.addLe(EBTDModel.sum(EBTDModel.diff(disruptionsDep[i][s], bufferFT[i]), pDep[i][s]),
//                        EBTDModel.diff(EBTDModel.prod(Flights.get(i).MAXPossiblePPG,
//                                zArr[i][s]), MINI), "parr4");
                pSum[s].addTerm(1, pDep[i][s]);

                // y & u
//                yDep[i][s] = EBTDModel.linearNumExpr();
//                yDep[i][s].addTerm(1, xDep[i]);
//                yDep[i][s].addTerm(1, pDep[i][s]);
//                for (int r = 0; r < indUDep[i][2]; r++) {
//                    int period = r + indUDep[i][0];
//                    double tmpp = pDep[i][s].getUB()+200 + xDep[i].getUB();
//                    if (r > 0)
//                        EBTDModel.addGe(yDep[i][s], EBTDModel.diff(period * PERIOD,
//                                EBTDModel.prod(tmpp * PERIOD, EBTDModel.diff(1, uDep[i][r]))), "g");
//                    if (r < indUDep[i][2] - 1)
//                        EBTDModel.addLe(yDep[i][s], EBTDModel.sum(period * PERIOD + PERIOD - 0.05,
//                                EBTDModel.prod(tmpp * PERIOD, EBTDModel.diff(1, uDep[i][r]))), "g");
//                }
//                yArr[i][s] = EBTDModel.linearNumExpr();
//                yArr[i][s].addTerm(1, xArr[i]);
//                yArr[i][s].addTerm(1, pArr[i][s]);
//                for (int r = 0; r < indUArr[i][2]; r++) {
//                    int period = r + indUArr[i][0];
//                    double tmpp = pArr[i][s].getUB() +200 + xArr[i].getUB();
//                    if (r > 0)
//                        EBTDModel.addGe(yArr[i][s], EBTDModel.diff(period * PERIOD,
//                                EBTDModel.prod(tmpp, EBTDModel.diff(1, uArr[i][r]))), "h" + i);
//                    if (r < indUArr[i][2] - 1)
//                        EBTDModel.addLe(yArr[i][s], EBTDModel.sum(period * PERIOD + PERIOD - 0.05,
//                                EBTDModel.prod(tmpp, EBTDModel.diff(1, uArr[i][r]))), "h" + i);
//                }
            }
            EBTDModel.addGe(psi, pSum[s], "psi");
        }

        IloLinearNumExpr objective = EBTDModel.linearNumExpr();
        objective.addTerm(1, psi);
        EBTDModel.addMinimize(objective);

        EBTDModel.setOut(new FileOutputStream(OUTPUT_DIR + "EBTDModel.log", true));
        EBTDModel.exportModel(OUTPUT_DIR + "EBTDModel.lp");
        if (EBTDModel.solve()) {
            // test checking changing
            String outputName = OUTPUT_DIR + "checkingEBTD";
            File out = new File(outputName + ".csv");
            FileWriter fw = new FileWriter(out, true);
            BufferedWriter bw = new BufferedWriter(fw);
            StringBuffer s1 = new StringBuffer(), s2 = new StringBuffer();
            StringBuffer s3 = new StringBuffer(), s4 = new StringBuffer();
            for (int i = 0; i < numFlights; i++) {
                int xdep = (int) Math.round(EBTDModel.getValue(xDep[i]) * 100) / 100;
                int xarr = (int) Math.round(EBTDModel.getValue(xArr[i]) * 100) / 100;
                Flights.get(i).setPlanTime(xdep, xarr);
                if (arrDiruptionToAdd.size() > 1) {
                    s1.append(EBTDModel.getValue(pDep[i][0])).append(",");
                    s2.append(EBTDModel.getValue(pDep[i][1])).append(",");
                    s3.append(EBTDModel.getValue(pArr[i][0])).append(",");
                    s4.append(EBTDModel.getValue(pArr[i][1])).append(",");
                }

            }
            bw.write(s1 + "\n" + s2 + "\n" + s3 + "\n" + s4 + "\n");
            bw.close();
            fw.close();
            System.out.println(EBTDModel.getObjValue());
            thisPsi = EBTDModel.getValue(psi);

            psirecord = new double[numRobustConstraints];
            for (int s = 0; s < numRobustConstraints; s++) {
                psirecord[s] = EBTDModel.getValue(pSum[s]);
            }
            updateBuffer();
            checkingIfPsi();
        }
        EBTDModel.endModel();
        EBTDModel.end();
    }

    static int properPeriod(int period) {
        while (period < 0) period += numPeriods;
        while (period >= numPeriods) period -= numPeriods;
        return period;
    }

    private static void updateBuffer() {
        for (Flight f : Flights) {
            if (f.hasPreFlight()) {
                f.OPRBufferTT = f.OPRdepartureTime - f.preF.OPRarrivalTime - f.minimalTurnTime;
            }
            f.OPRBufferFT = f.OPRarrivalTime - f.OPRdepartureTime - f.minimalFlightTime;
        }
    }

    public static void SeparationSolver() throws IloException, IOException {
        IloCplex SPModel = new IloCplex();

        SPModel.setParam(IloCplex.Param.Threads, 8);
        SPModel.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, SGap);
        SPModel.setParam(IloCplex.Param.TimeLimit, STL);

        int numFlights = Flights.size();
        int numAirports = Airports.size();

        IloNumVar[] pDep = new IloNumVar[numFlights];
        IloNumVar[] pArr = new IloNumVar[numFlights];
        IloNumVar[][] vDep = new IloNumVar[numAirports][numPeriods];
        IloNumVar[][] vArr = new IloNumVar[numAirports][numPeriods];
        IloIntVar[] IDep = new IloIntVar[numFlights];
        IloIntVar[] IArr = new IloIntVar[numFlights];

//        int periodDimension = (defaultPostpone - defaultAdvance + (int) MAXPPG1) / PERIOD + 1;
//        IloIntVar[][] uDep = new IloIntVar[numFlights][periodDimension];
//        IloIntVar[][] uArr = new IloIntVar[numFlights][periodDimension];
        int[] OPRdep = new int[numFlights], OPRarr = new int[numFlights];
//        int[][] indUDep = new int[numFlights][3];
//        int[][] indUArr = new int[numFlights][3];
        int[] uD = new int[numFlights], uA = new int[numFlights];

        IloNumVar[][] dDep = new IloNumVar[numAirports][numPeriods];
        IloNumVar[][] dArr = new IloNumVar[numAirports][numPeriods];

        IloNumVar[] DDep = new IloNumVar[numFlights];
        IloNumVar[] DArr = new IloNumVar[numFlights];

        IloLinearNumExpr objective = SPModel.linearNumExpr();
        // definitions
        for (int i = 0; i < numFlights; i++) {
            pDep[i] = SPModel.numVar(0, (int) Flights.get(i).MAXPossiblePPG, "pDep" + i);
            pArr[i] = SPModel.numVar(0, (int) Flights.get(i).MAXPossiblePPG, "pArr" + i);
            IDep[i] = SPModel.boolVar( "IDep" + i);
            IArr[i] = SPModel.boolVar( "IArr" + i);

            DDep[i] = SPModel.numVar((int) -Airports.get(Flights.get(i).oriPort).MAXDepDElayinAllPERIOD, (int) Airports.get(Flights.get(i).oriPort).MAXDepDElayinAllPERIOD,
                    "DDep" + i);
            DArr[i] = SPModel.numVar((int) -Airports.get(Flights.get(i).desPort).MAXArrDElayinAllPERIOD, (int) Airports.get(Flights.get(i).desPort).MAXArrDElayinAllPERIOD,
                    "DArr" + i);

            OPRdep[i] = Flights.get(i).OPRdepartureTime;
            OPRarr[i] = Flights.get(i).OPRarrivalTime;
            if (OPRdep[i] < 0) {
                OPRdep[i] += 24 * 60;
                OPRarr[i] += 24 * 60;
            }
            uD[i] = OPRdep[i] / PERIOD;
            if (uD[i] < Flights.get(i).depTimeBlockHead) uD[i] = Flights.get(i).depTimeBlockHead;
            if (uD[i] > Flights.get(i).depTimeBlockTail) uD[i] = Flights.get(i).depTimeBlockTail;
            uD[i] = properPeriod(uD[i]);
            uA[i] = properPeriod(OPRarr[i] / PERIOD);
            if (uA[i] < Flights.get(i).arrTimeBlockHead) uA[i] = Flights.get(i).arrTimeBlockHead;
            if (uA[i] > Flights.get(i).arrTimeBlockTail) uA[i] = Flights.get(i).arrTimeBlockTail;
            uA[i] = properPeriod(uA[i]);
        }

        for (int k = 0; k < numAirports; k++) {
            for (int r = 0; r < numPeriods; r++) {
                vDep[k][r] = SPModel.numVar(0, Math.sqrt(numPeriods) * gamma, "vDep" + k + "_" + r);
                vArr[k][r] = SPModel.numVar(0, Math.sqrt(numPeriods) * gamma, "vArr" + k + "_" + r);
                if (Airports.get(k).depFlag.get(r)) {
                    dDep[k][r] = SPModel.numVar(Airports.get(k).depMu.get(r) -
                                    Math.max(2,Gamma) * Airports.get(k).depSigma.get(r),
                            Airports.get(k).depMu.get(r) +
                                    Math.max(2,Gamma) * Airports.get(k).depSigma.get(r), "dDep" + k + "_" + r);
                    objective.addTerm(0.00000001, dDep[k][r]);
                }
                if (Airports.get(k).arrFlag.get(r)) {
                    dArr[k][r] = SPModel.numVar(Airports.get(k).arrMu.get(r) -
                                    Math.max(2,Gamma) * Airports.get(k).arrSigma.get(r),
                            Airports.get(k).arrMu.get(r) +
                                    Math.max(2,Gamma) * Airports.get(k).arrSigma.get(r), "dArr" + k + "_" + r);
                    objective.addTerm(0.00000001, dArr[k][r]);
                }
            }
        }

        for (int i = 0; i < numFlights; i++) {
            int oriPort = Flights.get(i).oriPort;
            int desPort = Flights.get(i).desPort;
            SPModel.addLe(DDep[i], dDep[oriPort][uD[i]], "f");
            SPModel.addLe(DArr[i], dArr[desPort][uA[i]], "g");
            if (!Flights.get(i).hasPreFlight())
                SPModel.addLe(pDep[i], 0);
            else {
                int j = Flights.get(i).preFID;
                SPModel.addLe(SPModel.diff(pDep[i], pArr[j]),
                        SPModel.sum(SPModel.diff(DArr[j], Flights.get(i).OPRBufferTT),
                                SPModel.prod(Flights.get(i).OPRBufferTT +
                                        Airports.get(Flights.get(i).desPort).MAXArrDElayinAllPERIOD, IDep[i])), "h" + i);
                SPModel.addLe(pDep[i], SPModel.prod(Flights.get(i).MAXPossiblePPG,
                        SPModel.diff(1, IDep[i])), "i");
            }
            SPModel.addLe(SPModel.diff(pArr[i], pDep[i]),
                    SPModel.sum(SPModel.diff(DDep[i], Flights.get(i).OPRBufferFT),
                            SPModel.prod(Flights.get(i).OPRBufferFT +
                                    Airports.get(Flights.get(i).oriPort).MAXDepDElayinAllPERIOD, IArr[i])), "j");
            SPModel.addLe(pArr[i], SPModel.prod(Flights.get(i).MAXPossiblePPG,
                    SPModel.diff(1, IArr[i])), "k");
            objective.addTerm(1, pDep[i]);

        }
        IloLinearNumExpr[] sumVDep = new IloLinearNumExpr[Airports.size()];
        IloLinearNumExpr[] sumVArr = new IloLinearNumExpr[Airports.size()];
        IloLinearNumExpr[][] coConDep = new IloLinearNumExpr[Airports.size()][numPeriods];
        IloLinearNumExpr[][] coConArr = new IloLinearNumExpr[Airports.size()][numPeriods];
        for (int k = 0; k < Airports.size(); k++) {
            sumVDep[k] = SPModel.linearNumExpr();
            sumVArr[k] = SPModel.linearNumExpr();

            int numDepV = 0, numArrV = 0;
            for (int r1 = 0; r1 < numPeriods; r1++) {
                coConDep[k][r1] = SPModel.linearNumExpr();
                coConArr[k][r1] = SPModel.linearNumExpr();
                int coCon1Dep = 0;
                int coCon1Arr = 0;
                for (int r2 = 0; r2 < numPeriods; r2++) {
                    if (Airports.get(k).depFlag.get(r1) && Airports.get(k).depFlag.get(r2)
                            && Airports.get(k).depLambda.containsKey(r1 * numPeriods + r2)) {
                        coConDep[k][r1].addTerm(Airports.get(k).depLambda.get(r1 * numPeriods + r2), dDep[k][r2]);
                        coCon1Dep += Airports.get(k).depLambda.get(r1 * numPeriods + r2) * Airports.get(k).depMu.get(r2);
                    }
                    if (Airports.get(k).arrFlag.get(r1) && Airports.get(k).arrFlag.get(r2)
                            && Airports.get(k).arrLambda.containsKey(r1 * numPeriods + r2)) {
                        coConArr[k][r1].addTerm(Airports.get(k).arrLambda.get(r1 * numPeriods + r2), dArr[k][r2]);
                        coCon1Arr += Airports.get(k).arrLambda.get(r1 * numPeriods + r2) * Airports.get(k).arrMu.get(r2);
                    }
                }

                if (Airports.get(k).depMu.get(r1) != 0) {
                    SPModel.addLe(SPModel.diff(coConDep[k][r1], coCon1Dep), vDep[k][r1], "co1" + k + "_" + r1);
                    SPModel.addLe(SPModel.diff(coCon1Dep, coConDep[k][r1]), vDep[k][r1], "co2" + k + "_" + r1);
                    numDepV++;
                    sumVDep[k].addTerm(1, vDep[k][r1]);
                }
                if (Airports.get(k).arrMu.get(r1) != 0) {
                    SPModel.addLe(SPModel.diff(coConArr[k][r1], coCon1Arr), vArr[k][r1], "co3" + k + "_" + r1);
                    SPModel.addLe(SPModel.diff(coCon1Arr, coConArr[k][r1]), vArr[k][r1], "co4" + k + "_" + r1);
                    numArrV++;
                    sumVArr[k].addTerm(1, vArr[k][r1]);
                }
            }
            SPModel.addLe(sumVDep[k], Math.sqrt(numDepV) * gamma, "co5" + k);
            SPModel.addLe(sumVArr[k], Math.sqrt(numArrV) * gamma, "co6" + k);
        }

        SPModel.addMaximize(objective);
        SPModel.setOut(new FileOutputStream(OUTPUT_DIR + "SPModel.log", true));
        SPModel.exportModel(OUTPUT_DIR + "SPModel.lp");
        if (SPModel.solve()) {
            double SPOBJ = SPModel.getObjValue();
            ArrayList<ArrayList<Double>> newArrDis = new ArrayList<>();
            ArrayList<ArrayList<Double>> newDepDis = new ArrayList<>();
            for (int k = 0; k < Airports.size(); k++) {
                ArrayList<Double> newArrDisForAPort = new ArrayList<>();
                ArrayList<Double> newDepDisForAPort = new ArrayList<>();
                for (int r = 0; r < numPeriods; r++) {
                    if (Airports.get(k).depFlag.get(r))
                        newDepDisForAPort.add((double) Math.round(SPModel.getValue(dDep[k][r]) * 100) / 100.0);
                    else
                        newDepDisForAPort.add(0.0);
                    if (Airports.get(k).arrFlag.get(r))
                        newArrDisForAPort.add((double) Math.round(SPModel.getValue(dArr[k][r]) * 100) / 100.0);
                    else
                        newArrDisForAPort.add(0.0);
                }
                newDepDis.add(newDepDisForAPort);
                newArrDis.add(newArrDisForAPort);
            }
            depDiruptionToAdd.add(newDepDis);
            arrDiruptionToAdd.add(newArrDis);
            toAddPsi = calculateTOAddPSI();
            System.out.println(toAddPsi);
        }
        SPModel.endModel();
        SPModel.end();
    }

    public static double calculateTOAddPSI() throws IOException {
        int sNum = depDiruptionToAdd.size();
        double AddPSI = 0;
        boolean[] visit = new boolean[Flights.size()];
        for (int i = 0; i < Flights.size(); i++) {
            Flight f = Flights.get(i);
            if (!visit[i] && !f.hasPreFlight()) {
                double proD = 0;
                do {
                    if (visit[f.index]) {
                        f = f.aftF;
                        if (proD > f.OPRBufferTT) proD = proD - f.OPRBufferTT;
                        else proD = 0;
                        AddPSI += proD;
                    }
                    visit[f.index] = true;
                    int oriPort = f.oriPort;
                    int period = f.OPRdepartureTime / PERIOD;
                    if (period < f.depTimeBlockHead) period = f.depTimeBlockHead;
                    if (period > f.depTimeBlockTail) period = f.depTimeBlockTail;
                    proD += depDiruptionToAdd.get(sNum-1).get(oriPort).get(properPeriod(period));
                    if (proD > f.OPRBufferFT) proD = proD - f.OPRBufferFT;
                    else proD = 0;

                    int desPort = f.desPort;
                    period = f.OPRarrivalTime / PERIOD;
                    if (period < f.arrTimeBlockHead) period = f.arrTimeBlockHead;
                    if (period > f.arrTimeBlockTail) period = f.arrTimeBlockTail;
                    proD += arrDiruptionToAdd.get(sNum-1).get(desPort).get(properPeriod(period));
                }
                while (f.hasAftFlight());
            }
        }
        return AddPSI;
    }

    public static void checkingIfPsi() throws IOException {
        int sNum = depDiruptionToAdd.size();
        double[] simulatePsi = new double[sNum];
        String outputName = OUTPUT_DIR + "checkingPSI";
        File out = new File(outputName + ".csv");
        FileWriter fw = new FileWriter(out, true);
        BufferedWriter bw = new BufferedWriter(fw);
        StringBuffer s1 = new StringBuffer(), s2 = new StringBuffer();
        for (int s = 0; s < sNum; s++) {
            boolean[] visit = new boolean[Flights.size()];
            for (int i = 0; i < Flights.size(); i++) {
                Flight f = Flights.get(i);
                if (!visit[i] && !f.hasPreFlight()) {
                    double proD = 0;
                    do {
                        if (visit[f.index]) {
                            f = f.aftF;
                            if (proD > f.OPRBufferTT) proD = proD - f.OPRBufferTT;
                            else proD = 0;
                            simulatePsi[s] += proD;
                        }
                        if (s == 0) s1.append(proD).append(",");
                        if (s == 1) s2.append(proD).append(",");
                        visit[f.index] = true;
                        int oriPort = f.oriPort;
//                        int period = (int) ((f.OPRdepartureTime + proD) / PERIOD);
                        int period = f.OPRdepartureTime / PERIOD;
                        if (period < f.depTimeBlockHead) period = f.depTimeBlockHead;
                        if (period > f.depTimeBlockTail) period = f.depTimeBlockTail;
                        proD += depDiruptionToAdd.get(s).get(oriPort).get(properPeriod(period));
                        if (proD > f.OPRBufferFT) proD = proD - f.OPRBufferFT;
                        else proD = 0;

                        int desPort = f.desPort;
//                        period = (int) ((f.OPRarrivalTime + proD) / PERIOD);
                        period = f.OPRarrivalTime / PERIOD;
                        if (period < f.arrTimeBlockHead) period = f.arrTimeBlockHead;
                        if (period > f.arrTimeBlockTail) period = f.arrTimeBlockTail;
                        proD += arrDiruptionToAdd.get(s).get(desPort).get(properPeriod(period));
                    }
                    while (f.hasAftFlight());
                }
            }
            bw.write(simulatePsi[s] + ",");
            bw.flush();
        }
        bw.write("\n");
        for (int i = 0; i < sNum; i++) {
            bw.write(psirecord[i] + ",");
        }
        bw.write("\n" + s1 + "\n" + s2 + "\n");
        bw.close();
        fw.close();
    }
}

