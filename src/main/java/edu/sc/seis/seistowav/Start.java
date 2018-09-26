package edu.sc.seis.seistowav;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;

import edu.iris.dmc.seedcodec.CodecException;
import edu.iris.dmc.seedcodec.UnsupportedCompressionType;
import edu.sc.seis.seisFile.mseed.DataRecord;
import edu.sc.seis.seisFile.mseed.SeedFormatException;
import edu.sc.seis.seisFile.mseed.SeedRecord;
import edu.sc.seis.seisFile.psn.PSNDataFile;
import edu.sc.seis.seisFile.psn.PSNEventRecord;

public class Start {

    static int maxRecords = -1;
    static int defaultRecordSize = 4096;
    
    public Start() {
        // TODO Auto-generated constructor stub
    }

    public static void main(String[] args) throws Exception {
        JSAP myJSAP = new JSAP();
        myJSAP.registerParameter( new FlaggedOption( "mseed", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                                     JSAP.NOT_REQUIRED,  JSAP.NO_SHORTFLAG, "mseed" ) );
        myJSAP.registerParameter( new FlaggedOption( "psn", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                                     JSAP.NOT_REQUIRED,  JSAP.NO_SHORTFLAG, "psn" ) );

        myJSAP.registerParameter( new FlaggedOption( "speedup", JSAP.INTEGER_PARSER, "100",
                                                     JSAP.NOT_REQUIRED, 's', "speedup" ) );
        JSAPResult result = myJSAP.parse(args);
        int speedup = result.getInt("speedup");
        if (result.contains("mseed")) {
            String mseedFilename = result.getString("mseed");
            processMiniseedFile(mseedFilename, speedup);
        } else if (result.contains("psn")) {
            String psnFilename = result.getString("psn");
            processPSNFile(psnFilename, speedup);
        } else {
            System.out.println(myJSAP.getHelp());
        }
    }

    public static void processPSNFile(String psnFilename, int speedup) throws FileNotFoundException, IOException, SeedFormatException, CodecException {

        if ( ! checkFileExistsAndWarnIfNot(psnFilename)) {
            return;
        }
        PSNDataFile psnData = new PSNDataFile(psnFilename);
        PSNEventRecord[] records = psnData.getEventRecords();
        int[] iData;
        if (records[0].isSampleDataInt()) {
            iData = records[0].getSampleDataInt();
        } else if (records[0].isSampleDataShort()) {
            short[] data = records[0].getSampleDataShort();
            iData = new int[data.length];
            for (int i = 0; i < iData.length; i++) {
                iData[i] = data[i];
            }
        } else {
            throw new CodecException("can only handle int and short format data");
        }
        double sampleRate = records[0].getFixedHeader().getSampleRate();

        File f = new File(psnFilename);
        String outfilename = f.getName()+".wav";
        if (f.getName().endsWith(".psn")) {
            outfilename = f.getName().substring(0, f.getName().length()-4)+".wav";
        }
        ConvertToWAV toWav = new ConvertToWAV(iData, sampleRate, speedup); 
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outfilename)));
        toWav.writeWAV(out);
        out.close();
    }
    
    public static void processMiniseedFile(String filename, int speedup) throws SeedFormatException, IOException, UnsupportedCompressionType, CodecException {
        InputStream inStream;
        if (filename.equals("stdin")) {
            inStream = System.in;
        } else {
            if ( ! checkFileExistsAndWarnIfNot(filename)) {
                return;
            }
            File f = new File(filename);
            if (f.exists() && f.isFile()) {
                inStream = new FileInputStream(filename);
            } else {
                // maybe a url?
                try {
                    URL url = new URL(filename);
                    inStream = url.openStream();
                } catch(MalformedURLException e) {
                    System.err.println("Cannot load '" + filename + "', as file or URL: exists=" + f.exists() + " isFile="
                            + f.isFile() + " " + e.getMessage());
                    return;
                } catch(FileNotFoundException e) {
                    System.err.println("Cannot load '" + filename + "', as file or URL: exists=" + f.exists() + " isFile="
                            + f.isFile() + " " + e.getMessage());
                    return;
                }
            }
        }
        DataInputStream dataInStream = new DataInputStream(new BufferedInputStream(inStream, 1024));
        int i = 0;
        String network = null, station=null, location=null, channel=null;
        List<DataRecord> drList = new ArrayList<DataRecord>();
        try {
            while (maxRecords == -1 || i < maxRecords) {
                SeedRecord sr = SeedRecord.read(dataInStream, defaultRecordSize);
                if (sr instanceof DataRecord) {
                    DataRecord dr = (DataRecord)sr;
                    if (i==0) {
                        network = dr.getHeader().getNetworkCode();
                        station = dr.getHeader().getStationIdentifier();
                        location = dr.getHeader().getLocationIdentifier();
                        channel = dr.getHeader().getChannelIdentifier();
                    }
                    if ((network == null || network.equals(dr.getHeader().getNetworkCode()))
                            && (station == null || station.equals(dr.getHeader().getStationIdentifier()))
                            && (location == null || location.equals(dr.getHeader().getLocationIdentifier()))
                            && (channel == null || channel.equals(dr.getHeader().getChannelIdentifier()))) {
                        drList.add(dr);
                    }
                }
                i++;
            }
        } catch(EOFException e) {
            // done I guess
        } finally {
            if (dataInStream != null) {
                dataInStream.close();
            }
        }
        double sampleRate = drList.get(0).getHeader().calcSampleRateFromMultipilerFactor();


        File f = new File(filename);
        String outfilename = f.getName()+".wav";
        if (f.getName().endsWith(".mseed")) {
            outfilename = f.getName().substring(0, f.getName().length()-6)+".wav";
        }
        ConvertToWAV toWav = new ConvertToWAV(decompress(drList), sampleRate, speedup);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outfilename)));
        toWav.writeWAV(out);
        
    }
    
    private static boolean checkFileExistsAndWarnIfNot(String filename) {
        File f = new File(filename);
        if (! f.exists() ) {
            System.err.println("File '"+filename+"' doesn't seem to exist. Full path I am trying is: "+f.getAbsolutePath());
            return false;
        }
        return true;
    }
    

    private static int getNumPoints(List<DataRecord> seis) {
        int npts = 0;
        for (DataRecord dataRecord : seis) {
            npts += dataRecord.getHeader().getNumSamples();
        }
        return npts;
    }
    
    private static int[] decompress(List<DataRecord> drList) throws SeedFormatException, UnsupportedCompressionType, CodecException {
        int[] out = new int[getNumPoints(drList)];
        int pos = 0;
        for (DataRecord dataRecord : drList) {
            int[]  dd = dataRecord.decompress().getAsInt();
            System.arraycopy(dd, 0, out, pos, dd.length);
            pos += dd.length;
        }
        return out;
    }

}
