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


//  Flight index   read function
public class work_fb {

    static ArrayList<String> AirportName;
    static ArrayList<Flight> Flights;
    static ArrayList<Airport> Airports;
    static HashMap<String, Integer> flightIDMap;

    static ArrayList<ArrayList<Double>> flightDiruptionToAdd;

    static String path = "../data/event/";
    static String INPUT_DIR = "../data/event/";
    static String ROOT_OUTPUT_DIR = "../data/event/out_gamma5_fb/";
    static String OUTPUT_DIR = null;

    static int startDay, endDay;
    static int startMonth, endMonth;

    static int defaultPostpone = 20;
    static int defaultAdvance = -20;

    static int MAXPPG = 100;


    static int numString;
    static int AirportLimit;

    static double thisPsi;
    static double toAddPsi;

    // para  TODO  参数修改
    static double MGap = 0, SGap = 0, HGap = 0.1;
    static int STL = 60 * 20, HTL = 60 * 60;
    static double MINI = 0.00000000001;
    static double Gamma = 3 ;


    static int ifNotUpdated = 0;
    static double gamma = Gamma * 70;

    // bufferLimit TODO
    static ArrayList<Double> totalBuffer;
    static ArrayList<Double> totalLast;
    static int control = 0;

    public static void main(String[] args) throws IloException, IOException {

        for (int i = 21; i <= 31; i++){
            startDay = i;
            endDay = i;
            startMonth = 7;
            endMonth = 7;
            run();
        }
        for (int i = 1; i < 21; i++){
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
            for (String p : new String[]{"checkingEBTD.csv", "checkingPSI.csv", "FBTDModel.log", "SPModel.log"}) {
                init = new FileWriter(OUTPUT_DIR + p);
                init.close();
            }
//             readFlightsInfo(filename);
            AirportLimit = 15;
            readReducedFlightsInfo(filename, AirportLimit);
            chooseSubData(startMonth, startDay);
            readStatInfo();
            setBufferLimit();
            solve(filename);
            outputFinalOPRPlan();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void chooseSubData(int m, int d) throws IOException {
        String file = INPUT_DIR + "out_gamma1/M" + m + "D" + d + "_M" + m + "D" + d + "/" + m + "_" + d + ".csv";
        File tFile = new File(file);
        BufferedReader read = new BufferedReader(new FileReader(tFile));
        ArrayList<Flight> tmpF = new ArrayList<>(Flights);
        HashMap<String, Integer> tmpflightIDMap = new HashMap<>(flightIDMap);
        Flights = new ArrayList<>();
        flightIDMap = new HashMap<>();
        String line = null;
        while ((line = read.readLine()) != null) {
            String[] grids = line.split(",");
            int thisF = tmpflightIDMap.get(grids[1]);
            Flights.add(tmpF.get(thisF));
            flightIDMap.put(tmpF.get(thisF).orginalID, Flights.size() - 1);
        }

        for (Flight f : Flights) {
            f.index = flightIDMap.get(f.orginalID);
            if (f.hasPreFlight() && flightIDMap.containsKey(f.preOriginalID))
                f.preFID = flightIDMap.get(f.preOriginalID);
            else if (f.hasPreFlight()) {
                f.preFID = -1;
                f.preF = null;
            }
            if (f.hasAftFlight() && flightIDMap.containsKey(f.aftOriginalID))
                f.aftFID = flightIDMap.get(f.aftOriginalID);
            else if (f.hasAftFlight()) {
                f.aftFID = -1;
                f.aftF = null;
            }
        }
        read.close();
        return;
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
                    || flight.preF.ACTdepartureTime > flight.ACTdepartureTime)) {
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

    static void readReducedFlightsInfo(String filename, int airportLimit) throws IOException {
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
                    || flight.preF.ACTdepartureTime > flight.ACTdepartureTime)) {
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
        String filename = INPUT_DIR + "BeforeMonth8_FlightBaseDelayStats.csv";
        File tFile = new File(filename);
        BufferedReader read = new BufferedReader(new FileReader(tFile));
        String thisLine = read.readLine();
        String[] units = thisLine.split(",");

        int[] position = new int[Flights.size()];
        int[] FlightID = new int[Flights.size()];
        int cnt = 0;
        for (int i = 1; i < units.length - 2; i++) {
            if (units[i].charAt(1) != '_') continue;
            String legID = units[i].substring(2);
            if (flightIDMap.containsKey(legID)) {
                FlightID[cnt] = flightIDMap.get(legID);
                position[cnt++] = i;
            }
        }
        while ((thisLine = read.readLine()) != null) {
            units = thisLine.split(",");
            String legID = units[0];
            if (!flightIDMap.containsKey(legID)) continue;
            int IDcur = flightIDMap.get(legID);
            double mu = Double.parseDouble(units[units.length - 2]), sigma = Double.parseDouble(units[units.length - 1]);
            if (mu < 0) mu = 1 / (1 - mu);
            Flights.get(IDcur).setMuSigma(mu, sigma);
            Flights.get(IDcur).initialFlightSet();
            for (int i = 0; i < Flights.size(); i++) {
                Flights.get(IDcur).setFlightLambda(FlightID[i], Double.parseDouble(units[position[i]]));
            }
        }
        read.close();
    }

    private static void outputFinalOPRPlan() throws IOException {
        String outputName = OUTPUT_DIR + "final_plan" + startMonth + "_" + startDay;
        File out = new File(outputName + ".csv");
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        for (Flight f : Flights) {
            bw.write(f.index + "," + f.oriPortCode + "," + f.desPortCode + "," + f.CRSdt + "," + f.CRSat
                    + "," + f.ACTdt + "," + f.ACTat + "," + f.OPRdt + "," + f.OPRat + "\n");
        }
        bw.close();
        fw.close();
    }

    public static void solve(String name) throws IOException, IloException {
        flightDiruptionToAdd = new ArrayList<>();

        String outputName = OUTPUT_DIR + name + "_f_time";
        File out = new File(outputName + ".csv");
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("Iteration," + "ROUsedTime," + "SPUsedTime," + "AllUsedTime," + "thisPsi," + "toAddPsi\n");

        int times = 1;
        System.out.println("The " + times + " times to solve");
        long startTime = System.currentTimeMillis(), currentTime, lastTime;
        lastTime = startTime;
        thisPsi = -1;
        FBTDSolver();
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
        while ((toAddPsi - thisPsi) / (thisPsi + 0.1) > HGap && (currentTime - startTime) / 1000.0 <= HTL && ifNotUpdated <= 5) {
            System.out.println("The " + times + " times to solve");
            FBTDSolver();
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

    private static void setBufferLimit() {
        totalBuffer = new ArrayList<>();
        totalLast = new ArrayList<>();
        boolean[] visit = new boolean[Flights.size()];
        for (Flight f : Flights) {
            if (visit[f.index]) continue;
            double tot = 0;
            double last = 0;
            do {
                if (visit[f.index]) f = f.aftF;
                visit[f.index] = true;
                f.Stringindex = totalBuffer.size();
                tot += Math.max(0, f.CRSBufferFT) + Math.max(0, f.CRSBufferTT);
                last += f.minimalFlightTime;
                if (f.hasPreFlight()) last += f.minimalTurnTime;
            }
            while (f.hasAftFlight());
            if (tot < 0) tot = 0;
            totalBuffer.add(tot);
            totalLast.add(last);
        }
        numString = totalBuffer.size();
    }

    public static void FBTDSolver() throws IloException, IOException {
        IloCplex FBTDModel = new IloCplex();

        FBTDModel.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, MGap);
        FBTDModel.setParam(IloCplex.Param.Threads, 8);

        int numFlights = Flights.size();
        int numRobustConstraints = flightDiruptionToAdd.size();

        IloNumVar[] xDep = new IloNumVar[numFlights];
        IloNumVar[] xArr = new IloNumVar[numFlights];
        IloNumVar[][] p = new IloNumVar[numFlights][numRobustConstraints];

        IloNumVar psi = FBTDModel.numVar(0, MAXPPG * numFlights, "psi");

        int[] CRSdep = new int[numFlights], CRSarr = new int[numFlights];
        //  indur[][0] start point   indur[][1]  end point   indur[][2]  total Periods

        // Definition
        for (int i = 0; i < numFlights; i++) {
            CRSdep[i] = Flights.get(i).CRSdepartureTime;
            CRSarr[i] = Flights.get(i).CRSarrivalTime;
            if (i == 87){
                int a = 1;
            }
            xDep[i] = FBTDModel.numVar(CRSdep[i] + Flights.get(i).permitDepAdvance,
                    CRSdep[i] + Flights.get(i).permitDepPostpone, "xDep" + i);
            xArr[i] = FBTDModel.numVar(CRSarr[i] + Flights.get(i).permitArrAdvance,
                    CRSarr[i] + Flights.get(i).permitArrPostpone, "xArr" + i);

            for (int s = 0; s < numRobustConstraints; s++) {
                p[i][s] = FBTDModel.numVar(0, MAXPPG, "p" + i + "_" + s);
            }
        }

        IloLinearNumExpr[] bufferTT = new IloLinearNumExpr[numFlights];
        IloLinearNumExpr[] bufferFT = new IloLinearNumExpr[numFlights];
//        IloLinearNumExpr[] buffer = new IloLinearNumExpr[numString];//  bufferLimit
//        for (int i = 0; i < numString; i++) {
//            buffer[i] = FBTDModel.linearNumExpr(-totalLast.get(i) - control);
//        }

        for (int i = 0; i < numFlights; i++) {
//             FlightTime
            bufferFT[i] = FBTDModel.linearNumExpr(-Flights.get(i).minimalFlightTime);
            bufferFT[i].addTerm(1, xArr[i]);
            bufferFT[i].addTerm(-1, xDep[i]);
            FBTDModel.addGe(bufferFT[i], 0, "bft" + i);
            // TurnTime
            if (Flights.get(i).hasPreFlight()) {
                bufferTT[i] = FBTDModel.linearNumExpr(-Flights.get(i).minimalTurnTime);
                bufferTT[i].addTerm(1, xDep[i]);
                bufferTT[i].addTerm(-1, xArr[Flights.get(i).preFID]);
                FBTDModel.addGe(bufferTT[i], 0, "btt" + i);
            }
//            if (!Flights.get(i).hasPreFlight())
//                buffer[Flights.get(i).Stringindex].addTerm(-1, xDep[i]);
//            //bufferLimit
//            if (!Flights.get(i).hasAftFlight()) {
//                buffer[Flights.get(i).Stringindex].addTerm(1, xArr[i]);
//            }
        }

//        for (int i = 0; i < numString; i++) {
//            if (!Flights.get(i).hasPreFlight())
//                FBTDModel.addLe(buffer[Flights.get(i).Stringindex], totalBuffer.get(Flights.get(i).Stringindex));
//        }
        // Propagated Delay Cons
        for (int s = 0; s < numRobustConstraints; s++) {
            IloLinearNumExpr pSum = FBTDModel.linearNumExpr();
            for (int i = 0; i < numFlights; i++) {
                if (!Flights.get(i).hasPreFlight())
                    FBTDModel.addEq(p[i][s], 0, "pori");
                else {
                    int j = Flights.get(i).preFID;
                    FBTDModel.addGe(FBTDModel.diff(p[i][s], p[j][s]),
                            FBTDModel.diff(flightDiruptionToAdd.get(s).get(j), bufferTT[i]), "pdep");
                }
                pSum.addTerm(1, p[i][s]);
            }
            FBTDModel.addGe(psi, pSum, "psi");
        }

        IloLinearNumExpr objective = FBTDModel.linearNumExpr();
        objective.addTerm(1, psi);
        FBTDModel.addMinimize(objective);

        FBTDModel.setOut(new FileOutputStream(OUTPUT_DIR + "FBTDModel.log", true));
        FBTDModel.exportModel(OUTPUT_DIR + "FBTDModel.lp");
        if (FBTDModel.solve()) {
            for (int i = 0; i < numFlights; i++) {
                int xdep = (int) Math.round(FBTDModel.getValue(xDep[i]));
                int xarr = (int) Math.round(FBTDModel.getValue(xArr[i]));
                Flights.get(i).setPlanTime(xdep, xarr);
            }
            System.out.println(FBTDModel.getObjValue());
            thisPsi = FBTDModel.getValue(psi);
            updateBuffer();
        }
        FBTDModel.endModel();
        FBTDModel.end();
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
//        int numAirports = Airports.size();

        IloNumVar[] p = new IloNumVar[numFlights];
        IloIntVar[] I = new IloIntVar[numFlights];

        int[] OPRdep = new int[numFlights], OPRarr = new int[numFlights];

        IloNumVar[] d = new IloNumVar[numFlights];
        IloNumVar[] v = new IloNumVar[numFlights];

        // definitions
        for (int i = 0; i < numFlights; i++) {
            p[i] = SPModel.numVar(0, MAXPPG + 200, "p" + i);
            I[i] = SPModel.intVar(0, 1, "I" + i);
            d[i] = SPModel.numVar(-(Flights.get(i).flightMu + Flights.get(i).flightSigma * Gamma),
                    Flights.get(i).flightMu + Flights.get(i).flightSigma * Gamma, "d" + i);
            v[i] = SPModel.numVar(0, Math.sqrt(numFlights) * gamma, "v" + i);
//            v[i] = SPModel.numVar(0, numFlights * gamma, "v" + i);

            OPRdep[i] = Flights.get(i).OPRdepartureTime;
            OPRarr[i] = Flights.get(i).OPRarrivalTime;
            if (OPRdep[i] < 0) {
                OPRdep[i] += 24 * 60;
                OPRarr[i] += 24 * 60;
            }
        }
        IloLinearNumExpr sumV = SPModel.linearNumExpr();
        IloLinearNumExpr objective = SPModel.linearNumExpr();
        for (int i = 0; i < numFlights; i++) {
            sumV.addTerm(1, v[i]);
            if (!Flights.get(i).hasPreFlight())
                SPModel.addLe(p[i], 0);
            else {
                int j = Flights.get(i).preFID;
                SPModel.addLe(SPModel.diff(p[i], p[j]),
                        SPModel.sum(SPModel.diff(d[j], Flights.get(i).OPRBufferTT),
                                SPModel.prod(MAXPPG * numFlights, I[i])), "h");
                SPModel.addLe(p[i], SPModel.prod(MAXPPG * numFlights,
                        SPModel.diff(1, I[i])), "i");
            }
            objective.addTerm(1, p[i]);
            objective.addTerm(0.00001, d[i]);
        }
        SPModel.addLe(sumV, Math.sqrt(numFlights) * gamma);
//        SPModel.addLe(sumV, numFlights * gamma);
        for (int i = 0; i < numFlights; i++) {
            IloLinearNumExpr coCon = SPModel.linearNumExpr();
            int coCon1 = 0;
            for (int j = 0; j < numFlights; j++) {
                coCon.addTerm(Flights.get(i).flightLambda.get(j), d[j]);
                coCon1 += Flights.get(i).flightLambda.get(j) * Flights.get(j).flightMu;
            }
            SPModel.addLe(SPModel.diff(coCon, coCon1), v[i], "vl" + i);
            SPModel.addLe(SPModel.diff(coCon1, coCon), v[i], "vr" + i);
        }
        SPModel.addMaximize(objective);
        SPModel.setOut(new FileOutputStream(OUTPUT_DIR + "SPModel.log", true));
        SPModel.exportModel(OUTPUT_DIR + "SPModel.lp");
        if (SPModel.solve()) {
            double SPOBJ = SPModel.getObjValue();
            ArrayList<Double> newDis = new ArrayList<>();
            for (int i = 0; i < Flights.size(); i++) {
                double disrp = Math.round(SPModel.getValue(d[i]) * 100) / 100.0;
                if (disrp > 80) disrp = 80;
                newDis.add(disrp);
            }
            flightDiruptionToAdd.add(newDis);
            double tmp = toAddPsi;
            toAddPsi = calculateTOAddPSI();
            System.out.println(toAddPsi);
            if (tmp == toAddPsi) ifNotUpdated++;
            else ifNotUpdated = 0;
        }
        SPModel.endModel();
        SPModel.end();
    }

    public static double calculateTOAddPSI() throws IOException {
        int sNum = flightDiruptionToAdd.size();
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
                    proD += flightDiruptionToAdd.get(sNum - 1).get(f.index);
                    if (proD > f.OPRBufferFT) proD = proD - f.OPRBufferFT;
                    else proD = 0;
                }
                while (f.hasAftFlight());
            }
        }
        return AddPSI;
    }
}
