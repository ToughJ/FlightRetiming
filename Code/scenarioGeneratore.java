package src.main.retime2;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;


public class scenarioGeneratore {

    static String path = "../data/event/";
    static String INPUT_DIR = "../data/event/";
    static String ROOT_OUTPUT_DIR = "../data/event/evaluate/";
    static String OUTPUT_DIR = null;

    static int numScenario = 1000;

    static int whichPlan = 2;  //  1 for TDP   2 for CRS  3 for ACT  4 for TRA

    static int startDay, endDay;
    static int startMonth, endMonth;

    public static void main(String[] args) {
        getFilesDatas(path + "stat_8/");
        return ;

    }


    public static void getFilesDatas(String filePath) {
        File file = new File(filePath); //需要获取的文件的路径
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
        //创建一个输入流对象
        String portName = path.getName().substring(7, 10);
        String AoD = path.getName().substring(10, 13);
        boolean isArrival = (AoD.equals("Arr") ? true : false);
        BufferedReader read = new BufferedReader(new FileReader(path));
        String thisLine = read.readLine();
        HashMap<Integer, ArrayList<Double>> hm = new HashMap<>();
        int block = 0;
        while ((thisLine = read.readLine()) != null) {
            String[] units = thisLine.split(",");
            int thisBlock = Integer.parseInt(units[0]);
            while (block < thisBlock) {
                ArrayList<Double> al = new ArrayList<>();
                for (int i = 0; i < numScenario; i++) {
                    al.add(0.0);
                }
                hm.put(block, al);
                block++;
            }
            double mu = Double.parseDouble(units[units.length - 2]);
            double sigma = Double.parseDouble(units[units.length - 1]);
            ArrayList<Double> al = new ArrayList<>();
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < numScenario; i++) {
                al.add(mu + sigma * random.nextGaussian());
            }
            hm.put(block, al);
            block++;
        }
        while (block < 72) {
            ArrayList<Double> al = new ArrayList<>();
            for (int i = 0; i < numScenario; i++) {
                al.add(0.0);
            }
            hm.put(block, al);
            block++;
        }
        read.close();
        String outputName = ROOT_OUTPUT_DIR + portName + AoD + ".csv";
        File out = new File(outputName);
        FileWriter fw = new FileWriter(out);
        BufferedWriter bw = new BufferedWriter(fw);
        for (int i = 0; i < 72; i++) {
            StringBuffer sb = new StringBuffer();
            sb.append(i).append(",");
            for (int j = 0; j < numScenario; j++) {
                sb.append(hm.get(i).get(j)).append(",");
            }
            bw.write(sb.toString() + "\n");
        }
        bw.close();
    }

}
