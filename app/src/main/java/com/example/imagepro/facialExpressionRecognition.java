package com.example.imagepro;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

public class facialExpressionRecognition {
    // define interpreter
    // Before this implement tensorflow to build.gradle file
    private final Interpreter interpreter;
    // define input size
    private final int INPUT_SIZE;
    // define height and width of original frame
    // private int height=0;
    // private int width=0;
    // now define Gpudelegate
    // it is use to implement gpu in interpreter
    private GpuDelegate gpuDelegate=null;

    TextToSpeech textToSpeech;

    private final static int time = 20000;
//    private CountDownTimer timerCount;

    String arr="";

    // now define cascadeClassifier for face detection
    private CascadeClassifier cascadeClassifier;
    // now call this in CameraActivity
    facialExpressionRecognition(AssetManager assetManager, Context context, String modelPath,int inputSize) throws IOException {

        // Initialising the textToSpeech:
        textToSpeech = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {

                // if No error is found then only it will run
                if(i!=TextToSpeech.ERROR){
                    // To Choose language of speech
                    textToSpeech.setLanguage(Locale.UK);
                }
            }
        });

        // Starting the countDown....
        countDown();

        INPUT_SIZE=inputSize;
        // set GPU for the interpreter
        Interpreter.Options options=new Interpreter.Options();
        gpuDelegate=new GpuDelegate();
        // add gpuDelegate to option
        options.addDelegate(gpuDelegate);
        // now set number of threads to options
        options.setNumThreads(4); // set this according to your phone
        // this will load model weight to interpreter
        interpreter=new Interpreter(loadModelFile(assetManager,modelPath),options);
        // if model is load print
        Log.d("facial_Expression","Model is loaded");

        // now we will load haarcascade classifier
        try {
            // define input stream to read classifier
            InputStream is=context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            // create a folder
            File cascadeDir=context.getDir("cascade",Context.MODE_PRIVATE);
            // now create a new file in that folder
            File mCascadeFile=new File(cascadeDir,"haarcascade_frontalface_alt");
            // now define output stream to transfer data to file we created
            FileOutputStream os=new FileOutputStream(mCascadeFile);
            // now create buffer to store byte
            byte[] buffer=new byte[4096];
            int byteRead;
            // read byte in while loop
            // when it read -1 that means no data to read
            while ((byteRead=is.read(buffer)) !=-1){
                // writing on mCascade file
                os.write(buffer,0,byteRead);

            }
            // close input and output stream
            is.close();
            os.close();
            cascadeClassifier=new CascadeClassifier(mCascadeFile.getAbsolutePath());
            // if cascade file is loaded print
            Log.d("facial_Expression","Classifier is loaded");
            // check your code one more time
            // select device and run
            //I/MainActivity: OpenCv Is loaded
            //D/facial_Expression: Model is loaded
            //D/facial_Expression: Classifier is loaded
            // Next video we will predict face in frame.
            // cropped frame is then pass through interpreter which will return facial expression/emotion

        }
        catch (IOException e){
            e.printStackTrace();
        }

    }

    public void countDown(){

        CountDownTimer timerCount = new CountDownTimer(time, 1000) {

            public void onTick(long millisUntilFinished) {
                long tmp = millisUntilFinished / 1000;
                Log.d("Ankit", String.valueOf(tmp));
            }

            public void onFinish() {
                // Do your stuff
                String ans=avgEmotion();
                textToSpeech.speak(ans,TextToSpeech.QUEUE_FLUSH,null);
                arr="";
                Log.d("Ankit", "OKAY : "+ans);

                // Restart the countDown..... 
                countDown();

            }
        }.start();
    }

    public String avgEmotion(){
        String[] temp = arr.split(" ");
        String[] emotion ={"Anger","Fear","Happy","Disgust","Surprise","Sad","Neutral"};
        String result="";
        int ans=0;
        for (int i = 0; i < 7; i++) {
            int ct=0;
            String word;
            word = emotion[i];
            for (String s : temp) {
                if (word.equals(s))
                    ct = ct + 1;
            }
            if(ans<ct){
                ans=ct;
                result=word;
            }
        }
        Log.d("Ankit",result);
        return result;
    }

    public Mat recognizeImage(Mat mat_image){
        // before predicting
        // our image is not properly align
        // we have to rotate it by 90 degree for proper prediction
        Core.flip(mat_image.t(),mat_image,1);// rotate mat_image by 90 degree
        // start with our process
        // convert mat_image to gray scale image
        Mat grayscaleImage=new Mat();
        Imgproc.cvtColor(mat_image,grayscaleImage,Imgproc.COLOR_RGBA2GRAY);
        // set height and width
        int height=grayscaleImage.height();
        int width=grayscaleImage.width();

        // define minimum height of face in original image
        // below this size no face in original image will show
        int absoluteFaceSize=(int)(height*0.1);
        // now create MatofRect to store face
        MatOfRect faces=new MatOfRect();
        // check if cascadeClassifier is loaded or not
        if(cascadeClassifier !=null){
            // detect face in frame
            //                                  input         output
            cascadeClassifier.detectMultiScale(grayscaleImage,faces,1.1,2,2,
                    new Size(absoluteFaceSize,absoluteFaceSize),new Size());
                    // minimum size
        }

        // now convert it to array
        Rect[] faceArray=faces.toArray();
        if(faceArray.length>0){
            int i=0;
            double a=faceArray[i].height*faceArray[i].width;
            for(int j=1;j<faceArray.length;j++){
                if (a<(faceArray[j].width*faceArray[j].height)){
                    a=faceArray[j].width*faceArray[j].height;
                    i=j;
                }
            }
            // if you want to draw rectangle around face
            //                input/output starting point ending point        color   R  G  B  alpha    thickness
            Imgproc.rectangle(mat_image,faceArray[i].tl(),faceArray[i].br(),new Scalar(0,255,0,255),2);
            // now crop face from original frame and grayscaleImage
                        // starting x coordinate       starting y coordinate
            Rect roi=new Rect((int)faceArray[i].tl().x,(int)faceArray[i].tl().y,
                    ((int)faceArray[i].br().x)-(int)(faceArray[i].tl().x),
                    ((int)faceArray[i].br().y)-(int)(faceArray[i].tl().y));
            // it's very important check one more time
            Mat cropped_rgba=new Mat(mat_image,roi);//
            // now convert cropped_rgba to bitmap
            Bitmap bitmap=null;
            bitmap=Bitmap.createBitmap(cropped_rgba.cols(),cropped_rgba.rows(),Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cropped_rgba,bitmap);
            // resize bitmap to (48,48)
            Bitmap scaledBitmap=Bitmap.createScaledBitmap(bitmap,48,48,false);
            // now convert scaledBitmap to byteBuffer
            ByteBuffer byteBuffer=convertBitmapToByteBuffer(scaledBitmap);
            // now create an object to hold output
            float[][] emotion=new float[1][1];
            //now predict with bytebuffer as an input and emotion as an output
            interpreter.run(byteBuffer,emotion);
            // if emotion is recognize print value of it

            // define float value of emotion
            float emotion_v=(float)Array.get(Array.get(emotion,0),0);
            Log.d("facial_expression","Output:  "+ emotion_v);
            // create a function that return text emotion
            String emotion_s=get_emotion_text(emotion_v);
            arr=arr+emotion_s+" ";
            // now put text on original frame(mat_image)
            //             input/output    text: Angry (2.934234)
            Imgproc.putText(mat_image,emotion_s,
                    new Point((int)faceArray[i].tl().x+10,(int)faceArray[i].tl().y+20),
                    1,1.5,new Scalar(0,0,255,150),2);
            //      use to scale text      color     R G  B  alpha    thickness

        }



        // after prediction
        // rotate mat_image -90 degree
        Core.flip(mat_image.t(),mat_image,0);
        return mat_image;
    }

    private String get_emotion_text(float emotion_v) {
        // create an empty string
        String val="";
        // use if statement to determine val
        // You can change starting value and ending value to get better result
        // Like

        if(emotion_v>=0 & emotion_v<0.5){
            val="Surprise";
        }
        else if(emotion_v>=0.5 & emotion_v <1.5){
            val="Fear";
        }
        else if(emotion_v>=1.5 & emotion_v <2.5){
            val="Angry";
        }
        else if(emotion_v>=2.5 & emotion_v <3.5){
            val="Neutral";
        }
        else if(emotion_v>=3.5 & emotion_v <4.5){
            val="Sad";
        }
        else if(emotion_v>=4.5 & emotion_v <5.5){
            val="Disgust";
        }
        else {
            val="Happy";
        }
        return val;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap scaledBitmap) {
        ByteBuffer byteBuffer;
        int size_image=INPUT_SIZE;//48

        byteBuffer=ByteBuffer.allocateDirect(4*1*size_image*size_image*3);
        // 4 is multiplied for float input
        // 3 is multiplied for rgb
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[size_image*size_image];
        scaledBitmap.getPixels(intValues,0,scaledBitmap.getWidth(),0,0,scaledBitmap.getWidth(),scaledBitmap.getHeight());
        int pixel=0;
        for(int i =0;i<size_image;++i){
            for(int j=0;j<size_image;++j){
                final int val=intValues[pixel++];
                // now put float value to bytebuffer
                // scale image to convert image from 0-255 to 0-1
                byteBuffer.putFloat((((val>>16)&0xFF))/255.0f);
                byteBuffer.putFloat((((val>>8)&0xFF))/255.0f);
                byteBuffer.putFloat(((val & 0xFF))/255.0f);

            }
        }
        return byteBuffer;
        // check one more time it is important else you will get error
    }


    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException{
        // this will give description of file
        AssetFileDescriptor assetFileDescriptor=assetManager.openFd(modelPath);
        // create a inputsteam to read file
        FileInputStream inputStream=new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();

        long startOffset=assetFileDescriptor.getStartOffset();
        long declaredLength=assetFileDescriptor.getDeclaredLength();
        return  fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);

    }


}
