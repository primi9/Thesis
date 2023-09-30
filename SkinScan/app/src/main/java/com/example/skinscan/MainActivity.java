package com.example.skinscan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private Button scanButton;
    private Button infoButton;
    private Button galleryButton;

    private static final int METADATA_REQUEST_CODE = 999;
    private static final int CAMERA_PERMISSION_CODE = 111;
    private static final int CAMERA_REQUEST_CODE = 9;
    private static final int GALLERY_REQUEST_CODE = 2310;
    private static final int STORAGE_PERMISSION_CODE = 1999;
    private static final int IN_WIDTH = 224;
    private static final int IN_HEIGHT = 224;
    private static final double ood_threshold = 0.75;

    private waitResultsDialog waitDialog;

    private final String[] region_spinner_options = {"ABDOMEN","ARM","BACK","CHEST","EAR","FACE","FOOT","FOREARM","HAND","LIP","NECK","NOSE","SCALP","THIGH"};

    private Tensor current_imgTensor;// = null;
    private Module NeuralNetWork = null;
    private Module ood_classifier = null;
    private float age;
    private String region_choice;
    private float[] bool_metadata;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try{
            NeuralNetWork = LiteModuleLoader.load(assetFilePath(this, "final_app_network.ptl"));
            ood_classifier = LiteModuleLoader.load(assetFilePath(this, "ood_classifier.ptl"));
            Log.d("Main Activity", "Modules Loaded Successfully");
        }catch (Exception e){
            Log.e("Main Activity", "Exception occurred in module loading");
        }

        scanButton = (Button) findViewById(R.id.scanoptionbutton);
        infoButton = (Button) findViewById(R.id.infobutton);
        galleryButton = (Button) findViewById(R.id.open_gallery);

        waitDialog = new waitResultsDialog(this);
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void initiateScan(View view) {
        Log.d("Main Activity", "Scan button clicked");

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        else
            getPicture();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void openGallery(View view){

        Log.d("Main Activity", "Load image button clicked");

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        else
            createGalleryIntent();
    }

    private void createGalleryIntent(){
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, GALLERY_REQUEST_CODE);
    }

    private void getPicture(){
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }

    private int check_ood(){

        Tensor output_ood = ood_classifier.forward(IValue.from(current_imgTensor)).toTensor();

        float[] scores_ood = output_ood.getDataAsFloatArray();
        softmax(scores_ood);

        if (scores_ood[1] > ood_threshold)
            return 1;
        return 0;
    }

    private void startInference(Bitmap img_input) throws IOException {

        Bitmap resized_image = Bitmap.createScaledBitmap(img_input, IN_WIDTH, IN_HEIGHT, true);

        current_imgTensor = TensorImageUtils.bitmapToFloat32Tensor(resized_image,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);//(1,3,224,224)

        int ood_result = check_ood();
        Log.d("Main Activity", "OOD Classifier:  " + ood_result);

        if (ood_result == 1)
            Toast.makeText(this, "Input image may not be valid...", Toast.LENGTH_LONG).show();

        Intent metadata_intent = new Intent(MainActivity.this, GetMetaData.class);
        startActivityForResult(metadata_intent,METADATA_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
                getPicture();
            }else
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
        }

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission to access storage granted", Toast.LENGTH_LONG).show();
                createGalleryIntent();
            }else
                Toast.makeText(this, "Permission to access storage denied", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {

            try{
                startInference((Bitmap) data.getExtras().get("data"));
            } catch (IOException e){
                Log.e("Main Activity", "Exception occurred in inference method");
            }
        }

        if (requestCode == GALLERY_REQUEST_CODE && resultCode == Activity.RESULT_OK){

            Uri selectedImage = data.getData();
            try {
                Bitmap gallery_bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
                startInference(gallery_bitmap);

            } catch (IOException e) {
                Log.e("Main Activity", "Exception occurred in gallery image retrieval");
            }
        }

        if (requestCode == METADATA_REQUEST_CODE) {
            if(resultCode == Activity.RESULT_OK){

                waitDialog.showDialog("Processing");

                //create tensor from boolean array
                region_choice = data.getStringExtra("region_choice");
                age = data.getFloatExtra("age",0);
                bool_metadata = data.getFloatArrayExtra("metadata");

                Log.d("Main Activity", region_choice);
                Log.d("Main Activity", String.valueOf(age));
                for (int i = 0; i < 6; i++)
                    Log.d("Main Activity", String.valueOf(bool_metadata[i]));

                Tensor current_metaTensor = getMetadataTensor();
                Tensor outputTensor = NeuralNetWork.forward(IValue.from(current_metaTensor),IValue.from(current_imgTensor)).toTensor();

                float[] scores = outputTensor.getDataAsFloatArray();

                softmax(scores);

                Intent results_intent = new Intent(MainActivity.this, ResultsActivity.class);
                results_intent.putExtra("outputs", scores);

                waitDialog.endDialog();
                startActivity(results_intent);
            }
            if (resultCode == Activity.RESULT_CANCELED)
                Log.d("Main Activity", "Metadata Request Canceled");
        }
    }

    //['age', 'gender', 'region', 'itch', 'grew', 'hurt', 'changed', 'bleed', 'elevation']
    private Tensor getMetadataTensor(){

        float[] metadata_input = new float[27];
        Arrays.fill(metadata_input,0);//nomizo pos etsi kai allios tha einai 0 oi times

        metadata_input[0] = age;

        //region value:
        for(int i = 0; i < region_spinner_options.length; i++){
            if(region_spinner_options[i].equals(region_choice))
                metadata_input[i + 1] = 1;
        }

        //boolean values
        int current_index = 15;
        for(int i = 0; i <= 5; i++){
            if(bool_metadata[i] == 0)
                metadata_input[current_index] = 1;
            else
                metadata_input[current_index + 1] = 1;
            current_index += 2;
        }

        Log.d("Main Activity", "Metadata Results");
        for (int i = 0; i < metadata_input.length; i++)
            Log.d("Main Activity", String.valueOf(metadata_input[i]));

        return Tensor.fromBlob(metadata_input, new long[] {1,27});
    }

    //in place implementation
    private void softmax(float[] in_array){

        int arr_len = in_array.length;
        float exp_sum = 0;

        for (int i = 0; i < arr_len; i++)
            exp_sum += Math.exp(in_array[i]);

        for (int i = 0; i < arr_len; i++)
            in_array[i] = (float)Math.exp(in_array[i]) / exp_sum;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d("Main Activity", "Inside on save method");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState){
        super.onRestoreInstanceState(savedInstanceState);
        Log.d("Main Activity", "Inside on restore method");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("Main Activity", "Inside on destroy method");
    }

    public void loadInfoScreen(View view) {
        Log.d("Main Activity", "Info button clicked");

        Intent info_intent = new Intent(MainActivity.this, infoActivity.class);
        startActivity(info_intent);
    }
}