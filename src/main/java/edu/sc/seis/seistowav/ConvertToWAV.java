package edu.sc.seis.seistowav;

import java.io.DataOutput;
import java.io.IOException;

import javax.sound.sampled.Clip;


import edu.iris.dmc.seedcodec.CodecException;
import edu.sc.seis.seisFile.mseed.SeedFormatException;
import edu.sc.seis.seisFile.mseed.Utility;

/**
 * FissuresToWAV.java
 * @see http://ccrma-www.stanford.edu/CCRMA/Courses/422/projects/WaveFormat/
 *
 *
 * Created: Wed Feb 19 15:35:06 2003
 *
 * @author <a href="mailto:crotwell@seis.sc.edu">Philip Crotwell</a>
 * @version 1.0
 */
public class ConvertToWAV {

    private int chunkSize, numChannels, sampleRate, speedUp, bitsPerSample,
        blockAlign, byteRate, subchunk2Size;
    private Clip clip;
    private int npts = 0;
    private int[] data;
    private double origSampleRate;

    public ConvertToWAV(int[] data, double origSampleRate, int speedUp) {
        this.data = data;
        this.origSampleRate = origSampleRate;
        this.speedUp = speedUp;
        numChannels = 1;
        bitsPerSample = 16;
        blockAlign = numChannels * (bitsPerSample/8);
    }

    public void writeWAV(DataOutput out) throws IOException, CodecException, SeedFormatException  {
        updateInfo();
        writeChunkData(out);
        writeWAVData(out);
    }

    private void updateInfo(){
        npts = data.length;
        chunkSize = 36 + 2*npts;
        subchunk2Size = npts * blockAlign;
        sampleRate = calculateSampleRate((float)origSampleRate);
        byteRate = sampleRate * blockAlign;
    }
    
    public void setSpeedUp(int newSpeed){
        speedUp = newSpeed;
        updateInfo();
    }

    private void writeChunkData(DataOutput out) throws IOException{
        out.writeBytes("RIFF"); //ChunkID

        //ChunkSize
        writeLittleEndian(out, chunkSize);

        out.writeBytes("WAVE"); //Format

        // write fmt subchunk
        out.writeBytes("fmt "); //Subchunk1ID
        writeLittleEndian(out, 16); //Subchunk1Size
        writeLittleEndian(out, (short)1); // Audioformat = linear quantization, PCM
        writeLittleEndian(out, (short)numChannels); // NumChannels
        writeLittleEndian(out, sampleRate); // SampleRate
        writeLittleEndian(out, byteRate); // byte rate
        writeLittleEndian(out, (short)blockAlign); // block align
        writeLittleEndian(out, (short)bitsPerSample); // bits per sample

        // write data subchunk
        out.writeBytes("data");
        writeLittleEndian(out, subchunk2Size); // subchunk2 size
    }

    private void writeWAVData(DataOutput out) throws IOException, CodecException, SeedFormatException {
        
        //calculate maximum amplification factor to avoid either
        //clipping or dead quiet
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        double mean = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] > max) { max = data[i];}
            if (data[i] < min) { min = data[i];}
        }
        mean = (max-min)/2;
        double amplification = (32000.0/(max-mean));

        for (int i = 0; i < data.length; i++) {
            writeLittleEndian(out, (short)(amplification * (data[i]-mean)));
        }
        
    }

    public int calculateSampleRate(float freq){
        int sampleRate = (int)(freq * speedUp);
        while (sampleRate > 48000){
            setSpeedUp(speedUp/2);
            sampleRate = (int)(freq * speedUp);
        }
        return sampleRate;
    }

    protected static void writeLittleEndian(DataOutput out, int value)
        throws IOException {
        byte[] tmpBytes;
        tmpBytes = Utility.intToByteArray(value);
        out.write(tmpBytes[3]);
        out.write(tmpBytes[2]);
        out.write(tmpBytes[1]);
        out.write(tmpBytes[0]);
    }

    protected static void writeLittleEndian(DataOutput out, short value)
        throws IOException {
        byte[] tmpBytes;
        tmpBytes = Utility.intToByteArray((int)value);
        out.write(tmpBytes[3]);
        out.write(tmpBytes[2]);
    }


} 

