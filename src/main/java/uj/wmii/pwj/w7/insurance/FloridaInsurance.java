package uj.wmii.pwj.w7.insurance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.*;

public class FloridaInsurance {
    record InsuranceEntry(
            String county,
            double tiv2011,
            double tiv2012
    ){}

    static int findIndex(String[] columns, String name) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(name)) return i;
        }
        throw new IllegalArgumentException("Column not found: " + name);
    }

    static InsuranceEntry parseLine (String line, int idCounty, int id2011, int id2012) {
        String[] parts = line.split(",");
        String county = parts[idCounty];
        double tiv2011 = Double.parseDouble(parts[id2011]);
        double tiv2012 = Double.parseDouble(parts[id2012]);
        return new InsuranceEntry(county, tiv2011, tiv2012);
    }

    static List<InsuranceEntry> loadInsuranceData (String zipPath) throws IOException {
        List<InsuranceEntry> list = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipPath)) {
            ZipEntry entry = zip.stream()
                    .filter(e -> e.getName().endsWith(".csv"))
                    .findFirst()
                    .orElseThrow();

            try (InputStream in = zip.getInputStream(entry);
                 BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String header = br.readLine();
                String[] columns = header.split(",");
                int idCounty = findIndex(columns, "county");
                int id2011   = findIndex(columns, "tiv_2011");
                int id2012   = findIndex(columns, "tiv_2012");

                String line;
                while ((line=br.readLine())!=null) {
                    InsuranceEntry insuranceEntry = parseLine(line, idCounty, id2011, id2012);
                    list.add(insuranceEntry);
                }
            }
        }
        return list;
    }

    static void generateCountFile(List<InsuranceEntry> entries) throws IOException {
        long countyCount = entries.stream()
                .map(InsuranceEntry::county)
                .distinct()
                .count();
        String fileContent = Long.toString(countyCount);
        Files.writeString(Paths.get("count.txt"), fileContent);
    }

    static void generateTiv2012File(List<InsuranceEntry> entries) throws IOException {
        double totalInsuranceValue = entries.stream()
                .mapToDouble(InsuranceEntry::tiv2012)
                .sum();

        String fileContent = Double.toString(totalInsuranceValue);
        Files.writeString(Paths.get("tiv2012.txt"), fileContent);
    }

    static void generateMostValuableFile(List<InsuranceEntry> entries) throws IOException {
        List<Map.Entry<String, Double>> top10 = entries.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                InsuranceEntry::county,
                                Collectors.summingDouble(e -> e.tiv2012()-e.tiv2011())
                        ),
                        map -> map.entrySet().stream()
                                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                                .limit(10)
                                .toList()
                ));

        StringBuilder sb = new StringBuilder();
        sb.append("country,value\n");
        for (Map.Entry<String, Double> entry : top10) {
            sb.append(entry.getKey())
                    .append(",")
                    .append(String.format(Locale.US, "%.2f", entry.getValue()))
                    .append("\n");
        }
        Files.writeString(Paths.get("most_valuable.txt"), sb.toString());
    }

    public static void main(String[] args) {
        String zipPath = "FL_insurance.csv.zip";
        List<InsuranceEntry> insuranceEntries;

        try {
            insuranceEntries = loadInsuranceData(zipPath);
        } catch (Exception e) {
            System.out.println("ERROR: loading insurance data");
            return;
        }

        try {
            generateCountFile(insuranceEntries);
        } catch (Exception e) {
            System.out.println("ERROR: counting counties");
        }

        try {
            generateTiv2012File(insuranceEntries);
        } catch (Exception e) {
            System.out.println("ERROR: counting total insurance value in 2012");
        }

        try {
            generateMostValuableFile(insuranceEntries);
        } catch (Exception e) {
            System.out.println("ERROR: generating most_valuable.txt");
        }
    }
}
