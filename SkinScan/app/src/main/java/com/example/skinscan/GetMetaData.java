package com.example.skinscan;

import androidx.appcompat.app.AppCompatActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

public class GetMetaData extends AppCompatActivity {

    private Switch bleeding_switch, itching_switch, grew_switch,hurt_switch,changed_switch,elevation_switch;
    private Button ok_button;
    private Spinner region_spinner;
    private final String[] region_spinner_strings = {"ABDOMEN","ARM","BACK","CHEST","EAR","FACE","FOOT","FOREARM","HAND","LIP","NECK","NOSE","SCALP","THIGH"};
    private String region_spinner_choice = "ARM";
    private EditText age_input;
    private float age = 0;

    private float[] metadata = new float[6];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_meta_data);

        bleeding_switch = findViewById(R.id.bleeding_switch);
        itching_switch = findViewById(R.id.itching_switch);
        grew_switch = findViewById(R.id.grew_switch);
        hurt_switch = findViewById(R.id.hurt_switch);
        changed_switch = findViewById(R.id.changed_switch);
        elevation_switch = findViewById(R.id.elevation_switch);

        age_input = (EditText) findViewById(R.id.age_input);
        ok_button = findViewById(R.id.ok_button);

        //create the region spinner
        region_spinner = (Spinner) findViewById(R.id.region_spinner);
        ArrayAdapter ad_region = new ArrayAdapter(this, R.layout.spinner_style, region_spinner_strings);
        ad_region.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        region_spinner.setAdapter(ad_region);
        region_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                region_spinner_choice = region_spinner_strings[position];
                String spinnerValue= parent.getItemAtPosition(position).toString();
                System.out.println(spinnerValue);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent){
            }
        });

        //initialize all metadata to False
        for(int i = 0; i < 6; i++)
            metadata[i] = 0;
    }

    public void ok_button_pressed(View view){

        if (itching_switch.isChecked())
            metadata[0] = 1;
        if (grew_switch.isChecked())
            metadata[1] = 1;
        if (hurt_switch.isChecked())
            metadata[2] = 1;
        if (changed_switch.isChecked())
            metadata[3] = 1;
        if (bleeding_switch.isChecked())
            metadata[4] = 1;
        if (elevation_switch.isChecked())
            metadata[5] = 1;

        String age_in_string = age_input.getText().toString();

        if(!age_in_string.trim().isEmpty()) {
            age = Integer.parseInt(age_in_string);

            if (age < 0 || age > 100)
                age = 0;
            else
                age = (float)((age - 60.46) / 15.89);
        }

        Intent returnIntent = new Intent();
        returnIntent.putExtra("metadata", metadata);
        returnIntent.putExtra("region_choice", region_spinner_choice);
        returnIntent.putExtra("age", age);

        setResult(Activity.RESULT_OK,returnIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        Intent returnIntent = new Intent();
        setResult(Activity.RESULT_CANCELED, returnIntent);
        finish();
    }
}