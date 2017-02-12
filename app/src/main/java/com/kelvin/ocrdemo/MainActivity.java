package com.kelvin.ocrdemo;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_MEAN_C;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.adaptiveThreshold;

public class MainActivity extends AppCompatActivity {
    Button photo_btn, thresholding_btn, ocr_btn;
    ImageView thresholded_iv;
    TextView ocr_result_txv;
    static final int REQUEST_TAKE_PHOTO = 1;
    String mCurrentPhotoPath;
    Bitmap ocrSrc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        photo_btn = (Button) findViewById(R.id.btn_photo);
        thresholding_btn = (Button) findViewById(R.id.btn_thresholding);
        ocr_btn = (Button) findViewById(R.id.btn_ocr);
        ocr_result_txv = (TextView) findViewById(R.id.txv_ocr_result);
        thresholded_iv = (ImageView) findViewById(R.id.img_thresholded);
    }

    public void take_photo (View view){
        dispatchTakePictureIntent();
    }

    public void do_threshold (View view){
        new doThresholding().execute(mCurrentPhotoPath);
    }

    public void do_ocr (View view){
        new doOCR().execute(ocrSrc);
    }

    //Taking photo handlers
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }
    //execute this
    private void dispatchTakePictureIntent() {
        Context context = MainActivity.this;
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e("File Creation Error", ex.toString());
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    context.grantUriPermission(packageName, photoURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {

        }
    }

    //OpenCV Thresholding handlers
    private class doThresholding extends AsyncTask<String, String, Bitmap>{
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute(){
            super.onPreExecute();
            progressDialog = ProgressDialog.show(MainActivity.this, "Do thresholding", "Started");
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            publishProgress("Load image");
            // Get the dimensions of the bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
            int photoW = bmOptions.outWidth;
            int photoH = bmOptions.outHeight;
            Log.i("photoW", Integer.toString(photoW));
            Log.i("photoH", Integer.toString(photoH));
            // Decode the image file into a Bitmap sized to fill the View
            BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
            bmpOptions.inJustDecodeBounds = false;
            bmpOptions.inSampleSize = 2;
            bmpOptions.inPurgeable = true;
            Bitmap oriBmp = BitmapFactory.decodeFile(params[0], bmpOptions);

            publishProgress("OpenCV initialization");
            OpenCVLoader.initDebug();

            publishProgress("Convert original bitmap to Mat");
            Mat srcMat = new Mat();
            Utils.bitmapToMat(oriBmp, srcMat);
            oriBmp.recycle();

            publishProgress("Do thresholding");
            Mat grayMat = new Mat();
            Mat thresholdMat = new Mat();
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGB2GRAY);//rgbMat to gray grayMat
            srcMat.release();
            adaptiveThreshold(grayMat, thresholdMat, 255, ADAPTIVE_THRESH_MEAN_C, THRESH_BINARY, 25, 13);
            grayMat.release();

            publishProgress("Convert result mat to bitmap");
            Bitmap.Config conf = Bitmap.Config.ARGB_8888;
            Bitmap resultBitmap = Bitmap.createBitmap(photoW/2, photoH/2, conf);
            Utils.matToBitmap(thresholdMat, resultBitmap);
            thresholdMat.release();

            publishProgress("Save the result bitmap to file");
            //Convert bitmap to byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos);
            byte[] bitmapdata = bos.toByteArray();
            //write the bytes in file
            FileOutputStream fos;
            try {
                fos = new FileOutputStream(createImageFile());
                fos.write(bitmapdata);
                fos.flush();
                fos.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

            return resultBitmap;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            progressDialog.setMessage(values[0]);
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            ocrSrc = result;
            thresholded_iv.setImageBitmap(ocrSrc);
            progressDialog.dismiss();
        }
    }

    //OCR handlers
    private class doOCR extends AsyncTask<Bitmap, String, String>{
        String TESSBASE_PATH = "/storage/sdcard0/Download/tesseract/";
        String CHINESE_LANGUAGE = "chi_tra";
        ProgressDialog progressDialog;
        TessBaseAPI baseApi;

        @Override
        protected void onPreExecute() {
            //執行前 設定可以在這邊設定
            super.onPreExecute();
            progressDialog = ProgressDialog.show(MainActivity.this,
                    "OCR is working", "Started");
        }

        @Override
        protected String doInBackground(Bitmap... sourceImg) {
            publishProgress("Create OCR engine object");
            baseApi = new TessBaseAPI();
            publishProgress("Init OCR engine");
            baseApi.init(TESSBASE_PATH, CHINESE_LANGUAGE);
            publishProgress("SetPageSegMode");
            baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);
            publishProgress("Doing recognition");
            baseApi.setImage(sourceImg[0]);
            return baseApi.getUTF8Text();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            //執行中 可以在這邊告知使用者進度
            super.onProgressUpdate(values);
            progressDialog.setMessage(values[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            //執行後 完成背景任務
            super.onPostExecute(result);
            Log.i("result", result);
            ocr_result_txv.setText(result);
            baseApi.clear();
            baseApi.end();
            ocrSrc.recycle();
            progressDialog.dismiss();
        }
    }
}
