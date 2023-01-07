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

import static src.main.retime2.work_sos_hd.properPeriod;


public class evaulatePerformance {

    static ArrayList<ArrayList<Double>> depDis;
    static ArrayList<ArrayList<Double>> arrDis;

    static int defaultPostpone = 20;
    static int defaultAdvance = -20;
    static int PERIOD = 20;
    static int numPeriods = 24 * 60 / PERIOD;

    static ArrayList<Flight> Flights;
    static ArrayList<Airport> Airports;
    static HashMap<String, Integer> flightIDMap;
    static ArrayList<String> AirportName;

    static HashMap<String, Integer> SCNAirportNameMap;
    static ArrayList<HashMap<Integer, ArrayList<Double>>> SCNAirportDelay;
    static int numSCN = 1000;
    static int portIndex = 0;

    static String path = "../data/event/";
    static String INPUT_DIR = "../data/event/";
    //    static String ROOT_OUTPUT_DIR = "../data/event/out";
//    static String ROOT_OUTPUT_DIR = "../data/event/out_tdbl";
    static String ROOT_OUTPUT_DIR = "../data/event/out_gamma5";
    static String OUTPUT_DIR = null;

    static boolean switchflie = true;  //  true for out sample  false  for in sample
    static int whichPlan = 1;  //  1 for TDP   2 for CRS  3 for ACT  4 for TRA

    static int startDay, endDay;
    static int startMonth, endMonth;

    static int AirportLimit;

    static double SGap = 0;
    static int STL = 60 * 20;

    static int MAXPPG = 80;
    static double Gamma = 3;  // Gamma

    static double gamma = Gamma * 51;

    public static void main(String[] args) {

        readScen();
        for (int i = 21; i <= 31; i++) {
            startDay = i;
            endDay = i;
            startMonth = 7;
            endMonth = 7;
            scenarioGoer();
        }
        for (int i = 1; i < 21; i++) {
            startDay = i;
            endDay = i;
            startMonth = 8;
            endMonth = 8;
            scenarioGoer();
        }


//        for (int i = 21; i <= 31; i++) {
//            startDay = i;
//            endDay = i;
//            startMonth = 7;
//            endMonth = 7;
//            run();
//        }
//        for (int i = 1; i < 21; i++) {
//            startDay = i;
//            endDay = i;
//            startMonth = 8;
//            endMonth = 8;
//            run();
//        }
////
//        for (int i = 21; i <= 31; i++) {
//            startDay = i;
//            endDay = i;
//            startMonth = 7;
//            endMonth = 7;
//            average_run();
//        }
//        for (int i = 1; i < 21; i++) {
//            startDay = i;
//            endDay = i;
//            startMonth = 8;
//            endMonth = 8;
//            average_run();
//        }
    }

    private static void readScen() {
        SCNAirportNameMap = new HashMap<>();
        SCNAirportDelay = new ArrayList<>();
        String RootSCNname = "../data/event/evaluate/";
        File file = new File(RootSCNname); //需要获取的文件的路径
        String[] fileNameLists = file.list(); //存储文件名的String数组
        File[] filePathLists = file.listFiles(); //存储文件路径的String数组
        for (int i = 0; i < filePathLists.length; i++) {
            if (filePathLists[i].isFile()) {
                try {//读取指定文件路径下的文件内容
                    readFile(filePathLists[i]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void readFile(File path) throws IOException {
        String portName = path.getName().substring(0, 3);
        String AoD = path.getName().substring(3, 6);
        boolean isArrival = (AoD.equals("Arr") ? true : false);
        BufferedReader read = new BufferedReader(new FileReader(path));
        HashMap<Integer, ArrayList<Double>> thisPort = new HashMap<>();
        for (int i = 0; i < 72; i++) {
            String[] units = read.readLine().split(",");
            ArrayList<Double> thisBlock = new ArrayList<>();
            for (int j = 0; j < numSCN; j++) {
                thisBlock.add(Double.parseDouble(units[j + 1]));
            }
            thisPort.put(i, thisBlock);
        }
        SCNAirportDelay.add(thisPort);
        read.close();
        SCNAirportNameMap.put(portName, portIndex);
        if (!isArrival) portIndex++;
    }

    private static void scenarioGoer() {
        try {
            String filename = "Month8_Flights.csv";
            if (whichPlan == 4)
                OUTPUT_DIR = ROOT_OUTPUT_DIR + "_fb" + String.format("/M%dD%d_M%dD%d/", startMonth, startDay, endMonth, endDay);
            else
                OUTPUT_DIR = ROOT_OUTPUT_DIR + String.format("/M%dD%d_M%dD%d/", startMonth, startDay, endMonth, endDay);

            // Create the output directory.
            new File(OUTPUT_DIR).mkdirs();
            AirportLimit = 15;
            readReducedFlightsInfo(filename, AirportLimit);
            chooseSubData(startMonth, startDay);
            readStatInfo();
            setTimeBlock();
            setPlan(whichPlan);
            goScenario();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void goScenario() throws IOException {
        double SimPSI = 0;
        boolean[] visit = new boolean[Flights.size()];
        String name = "SCNrecord";
        if (whichPlan == 1) name += "TDP";
        if (whichPlan == 2) name += "CRS";
        if (whichPlan == 3) name += "ACT";
        if (whichPlan == 4) name += "TRA";
        int cntString = 0;
        int NumSim = numSCN;
        double[] PSI = new double[NumSim];
        double[][] SCNproDelay = new double[2 * Flights.size()][NumSim];
        double[][] SCNdelay = new double[2 * Flights.size()][NumSim];

        int MaxSCNrecord = 0;

        //  随着模拟可以先输出 avg
        File out = new File(OUTPUT_DIR + name + "AVG" + startMonth + "_" + startDay + ".csv");
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        for (int i = 0; i < Flights.size(); i++) {
            Flight f = Flights.get(i);
            if (!visit[i] && !f.hasPreFlight()) {
                StringBuffer[] sb = new StringBuffer[5];
                for (int j = 0; j <= 4; j++) {
                    sb[j] = new StringBuffer();
                    sb[j].append(cntString).append(",");
                }
                double[] proD = new double[NumSim];
                do {
                    double prd = 0, dly = 0;
                    if (visit[f.index]) {
                        f = f.aftF;
                        prd = 0;
                        for (int st = 0; st < NumSim; st++) {
                            if (proD[st] > f.OPRBufferTT) proD[st] = proD[st] - f.OPRBufferTT;
                            else proD[st] = 0;
                            prd += proD[st];
                            SCNproDelay[2 * f.index + 1][st] = proD[st];
                        }
                        sb[4].append(prd / NumSim).append(",");
                    } else {
                        sb[4].append(0).append(",");
                    }
                    sb[0].append(f.oriPortCode).append(",").append(f.desPortCode).append(",");
                    sb[1].append(f.OPRdepartureTime).append(",").append(f.OPRarrivalTime).append(",");
                    sb[2].append(f.OPRBufferTT).append(",").append(f.OPRBufferFT).append(",");
                    visit[f.index] = true;
                    int oriPort = f.oriPort;
                    int period = f.OPRdepartureTime / PERIOD;
                    if (period < f.depTimeBlockHead) period = f.depTimeBlockHead;
                    if (period > f.depTimeBlockTail) period = f.depTimeBlockTail;

                    prd = 0;
                    dly = 0;
                    for (int st = 0; st < NumSim; st++) {
                        int Pindex = SCNAirportNameMap.get(AirportName.get(oriPort));
                        int proPeriod = properPeriod(period);
                        // read dep delay
                        double delay = SCNAirportDelay.get(2 * Pindex + 1).get(proPeriod).get(st);

                        proD[st] += delay;
                        if (proD[st] > f.OPRBufferFT) proD[st] = proD[st] - f.OPRBufferFT;
                        else proD[st] = 0;
                        SCNdelay[2 * f.index][st] = delay;
                        SCNproDelay[2 * f.index][st] = proD[st];
                        dly += delay;
                        prd += proD[st];
                        PSI[st] += proD[st];
                    }
                    SimPSI += prd / NumSim;
                    // 输出 dep event  的 delay  及 输出到后续的 pro delay
                    sb[3].append(dly / NumSim).append(",");
                    sb[4].append(prd / NumSim).append(",");

                    int desPort = f.desPort;
                    period = f.OPRarrivalTime / PERIOD;
                    if (period < f.arrTimeBlockHead) period = f.arrTimeBlockHead;
                    if (period > f.arrTimeBlockTail) period = f.arrTimeBlockTail;

                    for (int st = 0; st < NumSim; st++) {
                        int Pindex = SCNAirportNameMap.get(AirportName.get(desPort));
                        int proPeriod = properPeriod(period);
                        double delay = SCNAirportDelay.get(2 * Pindex).get(proPeriod).get(st);
                        proD[st] += delay;
                        dly += delay;
                        // 记录 arr 输出到后续的delay
                        SCNdelay[2 * f.index + 1][st] = delay;
                    }
                    // 输出 arr event 的 delay 及 输出到后续的 pro delay
                    sb[3].append(dly / NumSim).append(",");
                }
                while (f.hasAftFlight());
                for (int j = 0; j <= 4; j++) {
                    bw.write(sb[j].toString() + "\n");
                }
                cntString++;
            }
        }
        bw.close();
        fw.close();

        // 输出max_
        out = new File(OUTPUT_DIR + name + "MAX" + startMonth + "_" + startDay + ".csv");
        fw = new FileWriter(out);
        bw = new BufferedWriter(fw);
        visit = new boolean[Flights.size()];
        double MAX_v_pd = PSI[0];
        for (int i = 1; i < numSCN; i++) {
            if (PSI[i] > MAX_v_pd) {
                MAX_v_pd = PSI[i];
                MaxSCNrecord = i;
            }
        }
        for (int i = 0; i < Flights.size(); i++) {
            Flight f = Flights.get(i);
            if (!visit[i] && !f.hasPreFlight()) {
                StringBuffer[] sb = new StringBuffer[5];
                for (int j = 0; j <= 4; j++) {
                    sb[j] = new StringBuffer();
                    sb[j].append(cntString).append(",");
                }
                do {
                    if (visit[f.index]) {
                        f = f.aftF;
                        sb[4].append(SCNproDelay[2 * f.index + 1][MaxSCNrecord]).append(",");
                    } else {
                        sb[4].append(0).append(",");
                    }
                    sb[0].append(f.oriPortCode).append(",").append(f.desPortCode).append(",");
                    sb[1].append(f.OPRdepartureTime).append(",").append(f.OPRarrivalTime).append(",");
                    sb[2].append(f.OPRBufferTT).append(",").append(f.OPRBufferFT).append(",");
                    visit[f.index] = true;

                    sb[3].append(SCNdelay[2 * f.index][MaxSCNrecord]).append(",");
                    sb[4].append(SCNproDelay[2 * f.index][MaxSCNrecord]).append(",");

                    sb[3].append(SCNdelay[2 * f.index + 1][MaxSCNrecord]).append(",");
                }
                while (f.hasAftFlight());
                for (int j = 0; j <= 4; j++) {
                    bw.write(sb[j].toString() + "\n");
                }
                cntString++;
            }
        }
        bw.close();
        fw.close();

        System.out.println(PSI[MaxSCNrecord] + "," +  SimPSI);
    }


    private static void average_run() {
        try {
            String filename = "Month8_Flights.csv";
            if (whichPlan == 4)
                OUTPUT_DIR = ROOT_OUTPUT_DIR + "_fb" + String.format("/M%dD%d_M%dD%d/", startMonth, startDay, endMonth, endDay);
            else
                OUTPUT_DIR = ROOT_OUTPUT_DIR + String.format("/M%dD%d_M%dD%d/", startMonth, startDay, endMonth, endDay);

            // Create the output directory.
            new File(OUTPUT_DIR).mkdirs();

//             readFlightsInfo(filename);
            AirportLimit = 15;
            readReducedFlightsInfo(filename, AirportLimit);
            chooseSubData(startMonth, startDay);
            readStatInfo();
            setTimeBlock();
            setPlan(whichPlan);
            Simulator();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setPlan(int whichPlan) throws IOException {
        if (whichPlan == 1 || whichPlan == 4) {
            readPlan();
        }

        if (whichPlan == 2) {
            for (Flight f : Flights) {
                f.OPRdepartureTime = f.CRSdepartureTime + (f.permitDepAdvance + f.permitDepPostpone) / 2;
                f.OPRarrivalTime = f.CRSarrivalTime + (f.permitArrAdvance + f.permitArrPostpone) / 2;
            }
        }
        if (whichPlan == 3) {
            for (Flight f : Flights) {
                f.OPRdepartureTime = f.ACTdepartureTime;
                f.OPRarrivalTime = f.ACTarrivalTime;
            }
        }

        // cal buffer
        for (Flight f : Flights) {
            if (f.hasPreFlight()) {
                f.OPRBufferTT = f.OPRdepartureTime - f.preF.OPRarrivalTime - f.minimalTurnTime;
                if (f.OPRdepartureTime - f.preF.OPRarrivalTime < 0) f.OPRBufferTT += 24 * 60;
            }
            f.OPRBufferFT = f.OPRarrivalTime - f.OPRdepartureTime - f.minimalFlightTime;
            if (f.OPRarrivalTime - f.OPRdepartureTime < 0) f.OPRBufferFT += 24 * 60;
        }
    }

    private static void Simulator() throws IOException {
        double SimPSI = 0;
        boolean[] visit = new boolean[Flights.size()];
        String name = "SIMrecord";
        if (whichPlan == 1) name += "TDP";
        if (whichPlan == 2) name += "CRS";
        if (whichPlan == 3) name += "ACT";
        if (whichPlan == 4) name += "TRA";
        File out = new File(OUTPUT_DIR + name + startMonth + "_" + startDay + ".csv");
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        int cntString = 0;
        int NumSim = 1000;
        for (int i = 0; i < Flights.size(); i++) {
            Flight f = Flights.get(i);
            if (!visit[i] && !f.hasPreFlight()) {
                StringBuffer[] sb = new StringBuffer[5];
                for (int j = 0; j <= 4; j++) {
                    sb[j] = new StringBuffer();
                    sb[j].append(cntString).append(",");
                }
                double[] proD = new double[NumSim];
                do {
                    double prd = 0, dly = 0;
                    if (visit[f.index]) {
                        f = f.aftF;
                        prd = 0;
                        for (int st = 0; st < NumSim; st++) {
                            if (proD[st] > f.OPRBufferTT) proD[st] = proD[st] - f.OPRBufferTT;
                            else proD[st] = 0;
                            prd += proD[st];
                        }
                        sb[4].append(prd / NumSim).append(",");
                    } else {
                        sb[4].append(0).append(",");
                    }
                    sb[0].append(f.oriPortCode).append(",").append(f.desPortCode).append(",");
                    sb[1].append(f.OPRdepartureTime).append(",").append(f.OPRarrivalTime).append(",");
                    sb[2].append(f.OPRBufferTT).append(",").append(f.OPRBufferFT).append(",");
                    visit[f.index] = true;
                    int oriPort = f.oriPort;
                    int period = f.OPRdepartureTime / PERIOD;
                    if (period < f.depTimeBlockHead) period = f.depTimeBlockHead;
                    if (period > f.depTimeBlockTail) period = f.depTimeBlockTail;

                    prd = 0;
                    dly = 0;
                    for (int st = 0; st < NumSim; st++) {
                        double delay = Math.random() * Airports.get(oriPort).depMu.get(properPeriod(period)) +
                                Math.random() * Gamma * Airports.get(oriPort).depSigma.get(properPeriod(period));
                        if (delay < 0) delay = 0;
                        proD[st] += delay;
                        if (proD[st] > f.OPRBufferFT) proD[st] = proD[st] - f.OPRBufferFT;
                        else proD[st] = 0;
                        dly += delay;
                        prd += proD[st];
                    }
                    sb[3].append(dly / NumSim).append(",");
                    sb[4].append(prd / NumSim).append(",");
                    SimPSI += prd / NumSim;

                    int desPort = f.desPort;
                    period = f.OPRarrivalTime / PERIOD;
                    if (period < f.arrTimeBlockHead) period = f.arrTimeBlockHead;
                    if (period > f.arrTimeBlockTail) period = f.arrTimeBlockTail;

                    for (int st = 0; st < NumSim; st++) {
                        double delay = Math.random() * Airports.get(desPort).arrMu.get(properPeriod(period)) +
                                Math.random() * Gamma * Airports.get(desPort).arrSigma.get(properPeriod(period));
                        if (delay < 0) delay = 0;
                        proD[st] += delay;
                        dly += delay;
                    }
                    sb[3].append(dly / NumSim).append(",");
                }
                while (f.hasAftFlight());
                for (int j = 0; j <= 4; j++) {
                    bw.write(sb[j].toString() + "\n");
                }
                cntString++;
            }
        }
        bw.close();
        fw.close();
        System.out.println(SimPSI);
    }

    private static void run() {
        try {
            String filename = "Month8_Flights.csv";
            if (whichPlan == 4)
                OUTPUT_DIR = ROOT_OUTPUT_DIR + "_fb" + String.format("/M%dD%d_M%dD%d/", startMonth, startDay, endMonth, endDay);
            else
                OUTPUT_DIR = ROOT_OUTPUT_DIR + String.format("/M%dD%d_M%dD%d/", startMonth, startDay, endMonth, endDay);

            // Create the output directory.
            new File(OUTPUT_DIR).mkdirs();

//             readFlightsInfo(filename);
            AirportLimit = 15;
            readReducedFlightsInfo(filename, AirportLimit);
            chooseSubData(startMonth, startDay);
            readStatInfo();
            setTimeBlock();
            setPlan(whichPlan);
            SeparationSolver();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void chooseSubData(int m, int d) throws IOException {
        String file = ROOT_OUTPUT_DIR + "/M" + m + "D" + d + "_M" + m + "D" + d + "/" + m + "_" + d + ".csv";
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

    private static void setTimeBlock() {
        for (Flight f : Flights) {
            int r1 = (f.CRSdepartureTime + f.permitDepAdvance) / PERIOD;
            int r2 = (f.CRSdepartureTime + f.permitDepAdvance + (int) MAXPPG) / PERIOD;
            int depPort = f.oriPort;
            while (!Airports.get(depPort).depFlag.get(properPeriod(r1)) && r1 < r2) {
                r1++;
            }
            f.depTimeBlockHead = r1;
            while (Airports.get(depPort).depFlag.get(properPeriod(r1 + 1)) && r1 < r2) r1++;
            f.depTimeBlockTail = r1;

            r1 = (f.CRSarrivalTime + f.permitArrAdvance) / PERIOD;
            r2 = (f.CRSarrivalTime + f.permitArrPostpone + (int) MAXPPG) / PERIOD;
            int arrPort = f.desPort;
            while (!Airports.get(arrPort).arrFlag.get(properPeriod(r1)) && r1 < r2) {
                r1++;
            }
            f.arrTimeBlockHead = r1;
            while (Airports.get(arrPort).arrFlag.get(properPeriod(r1 + 1)) && r1 < r2) r1++;
            f.arrTimeBlockTail = r1;
        }
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

//                if (flight.aftFID == -1) {
//
//                }
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

    private static void readPlan() throws IOException {
        String filename = OUTPUT_DIR + "final_plan" + startMonth + "_" + startDay + ".csv";
        File rFile = new File(filename);
        BufferedReader read = new BufferedReader(new FileReader(rFile));
        for (int i = 0; i < Flights.size(); i++) {
            String thisLine = read.readLine();
            String[] units = thisLine.split(",");
//            if (!Flights.get(i).desPortCode.equals(units[2]) || !Flights.get(i).oriPortCode.equals(units[1])) {
//                System.err.println("something Wrong!");
//            }
            Flights.get(i).setOPRdt(Integer.parseInt(units[7]));
            Flights.get(i).setOPRat(Integer.parseInt(units[8]));
        }
        read.close();
    }

    private static void readStatInfo() throws IOException {
        for (Airport airport : Airports) {
            airport.initialAirportStatInfo();
            String file;
            if (switchflie)
                file = INPUT_DIR + "stat_8/Month8_" + airport.airportCode + "DepDelayStats.csv";
            else
                file = INPUT_DIR + "stat/BeforeMonth8_" + airport.airportCode + "DepDelayStats.csv";
            readStatFromCsv(airport, file, true);
            if (switchflie)
                file = INPUT_DIR + "stat_8/Month8_" + airport.airportCode + "ArrDelayStats.csv";
            else
                file = INPUT_DIR + "stat/BeforeMonth8_" + airport.airportCode + "ArrDelayStats.csv";
            readStatFromCsv(airport, file, false);
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
                    airport.setDepMAX(d1 + d2 * Gamma);
                } else {
                    double d1 = Double.parseDouble(units[2 * efficientBlock + 1]),
                            d2 = Double.parseDouble(units[2 * efficientBlock + 2]);
                    airport.setArrSigmaMu(d1, d2);
                    airport.setArrMAX(d1 + d2 * Gamma);
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

        int[] OPRdep = new int[numFlights], OPRarr = new int[numFlights];
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
            IDep[i] = SPModel.boolVar("IDep" + i);
            IArr[i] = SPModel.boolVar("IArr" + i);

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
                                    Math.max(2, Gamma) * Airports.get(k).depSigma.get(r),
                            Airports.get(k).depMu.get(r) +
                                    Math.max(2, Gamma) * Airports.get(k).depSigma.get(r), "dDep" + k + "_" + r);
                    objective.addTerm(0.00000001, dDep[k][r]);
                }
                if (Airports.get(k).arrFlag.get(r)) {
                    dArr[k][r] = SPModel.numVar(Airports.get(k).arrMu.get(r) -
                                    Math.max(2, Gamma) * Airports.get(k).arrSigma.get(r),
                            Airports.get(k).arrMu.get(r) +
                                    Math.max(2, Gamma) * Airports.get(k).arrSigma.get(r), "dArr" + k + "_" + r);
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
            depDis = new ArrayList<>();
            arrDis = new ArrayList<>();
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
                depDis.add(newDepDisForAPort);
                arrDis.add(newArrDisForAPort);
            }
            double OBJ = calculateTOAddPSI();
            System.out.println(OBJ);
            File out = new File(OUTPUT_DIR + "eva.log");
            FileWriter fw = new FileWriter(out);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(OBJ + " " + SPOBJ);
            bw.close();
            fw.close();
        }
        SPModel.endModel();
        SPModel.end();
    }

    public static double calculateTOAddPSI() throws IOException {
        double AddPSI = 0;
        boolean[] visit = new boolean[Flights.size()];
        String name = "EVArecord";
        if (whichPlan == 1) name += "TDP";
        if (whichPlan == 2) name += "CRS";
        if (whichPlan == 3) name += "ACT";
        if (whichPlan == 4) name += "TRA";
        File out = new File(OUTPUT_DIR + name + startMonth + "_" + startDay + ".csv");
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        int cntString = 0;
        for (int i = 0; i < Flights.size(); i++) {
            Flight f = Flights.get(i);
            if (!visit[i] && !f.hasPreFlight()) {
                StringBuffer[] sb = new StringBuffer[5];
                for (int j = 0; j <= 4; j++) {
                    sb[j] = new StringBuffer();
                    sb[j].append(cntString).append(",");
                }
                double proD = 0;
                do {
                    if (visit[f.index]) {
                        f = f.aftF;
                        if (proD > f.OPRBufferTT) {
                            proD = proD - f.OPRBufferTT;
                        } else proD = 0;
                        sb[4].append(proD).append(",");
                    } else {
                        sb[4].append(0).append(",");
                    }
                    sb[0].append(f.oriPortCode).append(",").append(f.desPortCode).append(",");
                    sb[1].append(f.OPRdepartureTime).append(",").append(f.OPRarrivalTime).append(",");
                    sb[2].append(f.OPRBufferTT).append(",").append(f.OPRBufferFT).append(",");
                    visit[f.index] = true;
                    int oriPort = f.oriPort;
                    int period = f.OPRdepartureTime / PERIOD;
                    if (period < f.depTimeBlockHead) period = f.depTimeBlockHead;
                    if (period > f.depTimeBlockTail) period = f.depTimeBlockTail;
                    double delay = depDis.get(oriPort).get(properPeriod(period));
                    if (delay < 0) delay = 0;
                    proD += delay;
                    sb[3].append(delay).append(",");
                    if (proD > f.OPRBufferFT) proD = proD - f.OPRBufferFT;
                    else proD = 0;
                    sb[4].append(proD).append(",");
                    AddPSI += proD;

                    int desPort = f.desPort;
                    period = f.OPRarrivalTime / PERIOD;
                    if (period < f.arrTimeBlockHead) period = f.arrTimeBlockHead;
                    if (period > f.arrTimeBlockTail) period = f.arrTimeBlockTail;
                    delay = arrDis.get(desPort).get(properPeriod(period));
                    if (delay < 0) delay = 0;
                    proD += delay;
                    sb[3].append(delay).append(",");
                }
                while (f.hasAftFlight());
                for (int j = 0; j <= 4; j++) {
                    bw.write(sb[j].toString() + "\n");
                }
                cntString++;
            }
        }
        bw.close();
        fw.close();
        return AddPSI;
    }

}
