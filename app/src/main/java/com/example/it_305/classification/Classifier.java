package com.example.it_305.classification;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;


public class Classifier {

    private final List<String> labelList;
    private Interpreter interpreter;
    private int INPUT_SIZE ;
    private int PIXEL_SIZE = 3;
    private int IMAGE_MEAN = 0;
    private float IMAGE_STD = 255.0f;
    private float MAX_RESULTS = 3;
    private float THRESHOLD = 0.1f;
    public Classifier(AssetManager assetManager , String modelPath , String labelPath , int inputSize){

        INPUT_SIZE = inputSize;
        Interpreter.Options options= new Interpreter.Options();
        options.setNumThreads(5);
        options.setUseNNAPI(true);
        Log.i("ModelPath",modelPath);
        Log.i("labelPath",labelPath);

        try {
            interpreter = new Interpreter( loadModelFile(assetManager , modelPath), options);
        } catch (IOException e) {
            e.printStackTrace();
            Log.i("Erorr" , e.toString());
        }
        catch (IllegalArgumentException e){
            Log.i("Erorr" , e.toString());
        }


        labelList = loadLabelFile(assetManager , labelPath);

    }

    private List<String> loadLabelFile(AssetManager assetManager, String labelPath) {
        try {
            List<String> labelList = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
            reader.close();
            return labelList;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String MODEL_FILE) throws  IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

    }
    public class Recognition{
        String id= "";
        String title= "";
        float confidence= 0F;

        public Recognition(String i, String s, float confidence) {
            id= i;
            title = s;//Name of the image
            this.confidence = confidence;//Value Between 0 and 1
        }

        @NonNull
        @Override
        public String toString() {
            return title+" : "+confidence;
        }
    }


    public List<Recognition> recognizeImage(Bitmap bitmap)
    {
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaledBitmap);
        float [][]result = new float[1][labelList.size()];
        interpreter.run(byteBuffer, result);

        return getSortedResultFloat(result);
    }
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;
        byteBuffer = ByteBuffer.allocateDirect(4  * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                byteBuffer.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                byteBuffer.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
            }
        }
        return byteBuffer;
    }

    private List<Recognition> getSortedResultFloat(float[][] labelProbArray) {
        //List depending on the pirority of the item
        PriorityQueue<Recognition> pq =
                new PriorityQueue<>(
                        (int) MAX_RESULTS,//Initial Capcity
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                return Float.compare(rhs.confidence, lhs.confidence);
                            }//To Compare between the items based on the confidence of the item
                        });

        for(int i = 0; i < labelList.size(); ++i) {
            float confidence = labelProbArray[0][i];
            Log.d("tryCon",confidence+"");
            if (confidence > THRESHOLD) {
                pq.add(new Recognition(""+ i,
                        labelList.size() > i ? labelList.get(i) : "unknown",
                        confidence));
            }
        }
        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = (int) Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }
}
/**/