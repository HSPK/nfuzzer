package nfuzzer.buildpit;

import java.io.*;

public class BuildPit {
    public static void main(String[] args) throws Exception {
        char st_body = '1';
        int length = 0;
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[0])));

        String line = "";

        File outputFile = new File("pit.xml");
        outputFile.createNewFile();
        BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
        String data = null;

        line = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "\n"
                + "<Peach xmlns=\"http://peachfuzzer.com/2012/Peach\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "\n\t"
                + "xsi:schemaLocation=\"http://peachfuzzer.com/2012/Peach ../peach.xsd\">"
                + "\n\t"
                + "<DataModel name=\"nacos_data\">" + "\r\n";

        System.out.print(line);
        out.write(line);

        while ((data = br.readLine()) != null) {
            //out.flush(); 
            line = "";
            String value = "";
            length = data.length();
            for (int i = 0; i < length; i++) {
                if (data.charAt(i) == '{' || data.charAt(i) == '}' ||
                        data.charAt(i) == ' ' || data.charAt(i) == ',' ||
                        data.charAt(i) == ':' || data.charAt(i) == ' ' ||
                        data.charAt(i) == '?' || data.charAt(i) == ';' ||
                        data.charAt(i) == '(' || data.charAt(i) == ')') {
                    line = "\t\t" + "<String value=" + "\"" +
                            data.charAt(i) + "\"" +
                            " mutable=\"false\" token=\"true\" />" +
                            "\r\n";
                    System.out.print(line);
                    out.write(line);
                    //out.flush(); 
                    line = "";
                    continue;
                }

                if (data.charAt(i) == '"') {

                    if (!value.equals("")) {
                        line = "\t\t" + "<String value=" + "\"" + value + "\"" + " length=\"" + value.length() + "\"" + " mutable=\"false\" />" + "\r\n";
                        System.out.print(line);
                        out.write(line);
                        value = "";
                    }
                    line = "\t\t" + "<String value=" + "\"" + "&quot;" + "\"" + " mutable=\"false\" token=\"true\" />" + "\r\n";
                    System.out.print(line);
                    out.write(line);
                    //out.flush(); 
                    line = "";
                    continue;
                }

                if (data.charAt(i) == '=') {
                    // key-value's key
                    line = "\t\t" + "<String value=" + "\"" + value + "\"" + " length=\"" + value.length() + "\"" + " mutable=\"false\" />" + "\r\n";
                    System.out.print(line);
                    out.write(line);
                    value = "";
                    line = "\t\t" + "<String value=" + "\"" + "=" + "\"" + " mutable=\"false\" token=\"true\" />" + "\r\n";
                    System.out.print(line);
                    out.write(line);
                    //out.flush();
                    line = "";
                    continue;
                }

                if (data.charAt(i) == '&') {
                    // key-value's value
                    line = "\t\t" + "<String value=" + "\"" + value + "\"" + " length=\"" + value.length() + "\"" + " mutable=\"true\" />" + "\r\n";
                    System.out.print(line);
                    out.write(line);
                    value = "";
                    line = "\t\t" + "<String value=" + "\"" + "&amp;" + "\"" + " mutable=\"false\" token=\"true\" />" + "\r\n";
                    System.out.print(line);
                    out.write(line);
                    // out.flush();
                    line = "";
                    continue;
                }

                if (data.charAt(i) == '\t' || data.charAt(i) == '\n') continue;

                value = value + data.charAt(i);

                //out.flush(); 
                line = "";
            }
            line = "\t\t" + "<String value=" + "\"" + value + "\"" + " mutable=\"true\" />" + "\r\n";
            System.out.print(line);
            out.write(line);
            line = "";
//            out.write("\t\t" + "<String value=\"\\r\\n\" mutable=\"false\" token=\"true\" />" + "\n");
            //out.flush(); 
            out.write("\t" + "</DataModel>\n");

            line = line + "\n\t<StateModel name=\"TheState\" initialState=\"Initial\">\n" + "\t\t<State name=\"Initial\">\n" + "\t\t\t<Action type=\"output\">\n" + "\t\t\t\t<DataModel ref=\"nacos_data\" />\n" + "\t\t\t\t<Data fileName=\"/dev/null\"/>\n" + "\t\t\t</Action>\n" + "\t\t</State>\n" + "\t</StateModel>\n\n\n";
            line = line + "\t<Test name=\"Default\">\n" + "\t\t<StateModel ref=\"TheState\"/>\n";
            line = line + "\t\t<Publisher name=\"writer\" class=\"File\">\n" + "\t\t\t<Param name=\"FileName\" value=\"repaired\" /> \n" + "\t\t</Publisher>\n";
            line = line + "\t\t<Logger class=\"Filesystem\">\n" + "\t\t\t<Param name=\"Path\" value=\"logs\" />\n" + "\t\t</Logger>\n";
            line = line + "\n\t</Test>\n";
            line = line + "\n</Peach>";
            System.out.print(line);
            out.write(line);
            //out.flush();
        }
        out.flush();
        out.close();
    }
}
